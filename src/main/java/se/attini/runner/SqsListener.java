package se.attini.runner;


import static java.util.Objects.requireNonNull;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.logging.Logger;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

@ApplicationScoped
public class SqsListener {


    private static final Logger logger = Logger.getLogger(SqsListener.class);

    private final EnvironmentVariables environmentVariables;
    private final Shutdown shutdown;
    private final JobCreator scriptFileCreator;
    private final ScriptRunner scriptRunner;
    private final SqsClient sqsClient;
    private final DynamoDbClient dynamoDbClient;
    private final ContainerMetadataFacade containerMetadataFacade;
    private final DistributionSourceFiles distributionSourceFiles;
    private final AtomicInteger jobCounter;
    private final ExecutorService executorService;
    private final SfnFacade sfnFacade;

    @Inject
    public SqsListener(EnvironmentVariables environmentVariables,
                       Shutdown shutdown,
                       JobCreator scriptFileCreator,
                       ScriptRunner scriptRunner,
                       SqsClient sqsClient,
                       DynamoDbClient dynamoDbClient,
                       ContainerMetadataFacade containerMetadataFacade,
                       DistributionSourceFiles distributionSourceFiles,
                       SfnFacade sfnFacade) {
        this.environmentVariables = requireNonNull(environmentVariables, "environmentVariables");
        this.shutdown = requireNonNull(shutdown, "shutdown");
        this.scriptFileCreator = requireNonNull(scriptFileCreator, "scriptFileCreator");
        this.scriptRunner = requireNonNull(scriptRunner, "scriptRunner");
        this.sqsClient = requireNonNull(sqsClient, "sqsClient");
        this.dynamoDbClient = requireNonNull(dynamoDbClient, "dynamoDbClient");
        this.containerMetadataFacade = requireNonNull(containerMetadataFacade, "containerMetadataFacade");
        this.distributionSourceFiles = distributionSourceFiles;
        this.jobCounter = new AtomicInteger();
        this.executorService = Executors.newFixedThreadPool(environmentVariables.getJobConcurrency());
        this.sfnFacade = requireNonNull(sfnFacade, "sfnFacade");
    }

    public void listen() {
        logger.info("Currently " + jobCounter.get() + " jobs are running");
        int jobConcurrency = environmentVariables.getJobConcurrency();
        if (jobCounter.get() > jobConcurrency) {
            logger.info(
                    "To many concurrent jobs, will wait before executing more. Concurrency can be configured by setting the ATTINI_MAX_CONCURRENT_JOBS system variable. Current concurrency = " + jobConcurrency);
            return;
        }

        logger.debug("polling for messages");

        String queueUrl = environmentVariables.getQueueUrl();
        List<Message> messages = getMessages();

        ContainerMetadata metadata = containerMetadataFacade.getMetadata();

        if (!messages.isEmpty() && metadata.isTerminating()) {
            logger.info("Container is stopping, will not process messages");
            return;
        }


        if (!messages.isEmpty()) {
            try {
                if (!getTaskId().equals(metadata.taskId())) {
                    logger.warn("TaskId differ between attini resource and container metadata, will terminate container");
                    shutdown.shutdown();
                    return;
                }
            } catch (DynamoDbException e) {
                messages.stream()
                        .map(MessageConverter::toJobData)
                        .forEach(jobData -> sfnFacade.sendTaskFailed(jobData.responseToken(),
                                                                     "IllegalAccessException",
                                                                     "The runners IAM role does not have permission to read from the " + environmentVariables.getResourceStatesTable() + " dynamodb table. Will terminate the container"));
                shutdown.shutdown();
                return;
            }
        }
        messages.stream()
                .map(MessageConverter::toJobData)
                .filter(jobData -> {
                    if (!isCurrentExecution(jobData)) {
                        logger.info(
                                "Received job but execution Id does not match with current run, will delete from queue");
                        deleteMessage(queueUrl, jobData);
                        return false;
                    }
                    return true;
                })
                .filter(jobData -> {
                    boolean hasSameConfig = jobData.jobConfigHashCode() == environmentVariables.getConfigurationHashCode();
                    if (!hasSameConfig) {
                        logger.info("Detected change of container config, will not process message");
                    }
                    return hasSameConfig;
                })
                .map(runJobAsync(queueUrl, metadata))
                .reduce(CompletableFuture::allOf)
                .ifPresent(shutdown::scheduleShutdown);

        logger.debug("done polling for messages");

    }

    private List<Message> getMessages() {
        try {
            return sqsClient.receiveMessage(getMessageRequest())
                            .messages();
        } catch (QueueDoesNotExistException e) {
            logger.error("No queue exists with url = " + environmentVariables.getQueueUrl() + ", will terminate runner");
            shutdown.shutdown();
            return Collections.emptyList();
        } catch (SqsException e) {
            if (e.getMessage().contains("Access to the resource") && e.getMessage().contains("is denied")) {
                logger.error("Access denied for sqs queue with url = " + environmentVariables.getQueueUrl() + ", will terminate runner");
                shutdown.shutdown();
            }
            return Collections.emptyList();
        }
    }

    private String getTaskId() {
        return dynamoDbClient.getItem(GetItemRequest.builder()
                                                    .tableName(environmentVariables.getResourceStatesTable())
                                                    .key(Map.of("resourceType",
                                                                AttributeValue.builder()
                                                                              .s("Runner")
                                                                              .build(),
                                                                "name",
                                                                AttributeValue.builder()
                                                                              .s(environmentVariables.getRunnerResourceName())
                                                                              .build()))
                                                    .build())
                             .item().get("taskId").s();
    }

    private Function<JobData, CompletableFuture<Void>> runJobAsync(String queueUrl,
                                                                   ContainerMetadata containerMetadata) {
        return jobData -> CompletableFuture.runAsync(() -> {
            logger.info("Starting job for message = " + jobData.input());
            PathRegistry pathRegistry = PathRegistry.create();
            deleteMessage(queueUrl, jobData);
            try {
                jobCounter.incrementAndGet();
                Path outputFile = pathRegistry.register(createTempJsonFile("output"));
                Path inputFile = pathRegistry.register(createInputFile(jobData));
                Path sourceDirectory = pathRegistry.register(distributionSourceFiles.sync(jobData));
                Path scriptFile = pathRegistry.register(scriptFileCreator.createScriptFile(jobData,
                                                                                           inputFile,
                                                                                           outputFile,
                                                                                           sourceDirectory));
                int exitCode = scriptRunner.runScript(scriptFile, environmentVariables.getScriptTimeout(3600), jobData);
                if (exitCode == 0) {
                    logger.info("Script finished successfully");
                    sfnFacade.setTaskSuccess(jobData, outputFile);
                } else {
                    logger.error("Script exited with code " + exitCode);
                    String logPathMessage = containerMetadata.getLogUrl()
                                                             .map(logUrl -> ", see complete logs at: " + logUrl)
                                                             .orElse("");
                    sfnFacade.sendTaskFailed(jobData.responseToken(),
                                             "ScriptExecutionError",
                                             "Script exited with code " + exitCode + logPathMessage);
                }

            } catch (ScriptExecutionException e) {
                logger.error("script failed to execute", e);
                sfnFacade.sendTaskFailed(jobData.responseToken(),
                                         "ScriptExecutionError",
                                         e.getMessage() + containerMetadata.getLogUrl()
                                                                           .map(logUrl -> ", see complete logs at: " + logUrl)
                                                                           .orElse(""));
            } catch (Exception e) {
                logger.error("Failed to run task", e);
                sfnFacade.sendTaskFailed(jobData.responseToken(), "RuntimeError", e.getMessage());
            } finally {
                jobCounter.decrementAndGet();
                pathRegistry.deletePaths();
            }
        }, executorService);
    }

    private Path createInputFile(JobData jobData) {
        Path inputFile = createTempJsonFile("input");
        try {
            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(inputFile.toFile()));
            fileWriter.write(jobData.input());
            fileWriter.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return inputFile;
    }

    private Path createTempJsonFile(String name) {
        try {
            return Files.createTempFile(name, ".json");
        } catch (IOException e) {
            throw new UncheckedIOException("could not create " + name + " file", e);
        }
    }

    private void deleteMessage(String queueUrl, JobData jobData) {
        sqsClient.deleteMessage(
                DeleteMessageRequest.builder()
                                    .queueUrl(queueUrl)
                                    .receiptHandle(jobData.receiptHandle())
                                    .build());
    }

    private boolean isCurrentExecution(JobData jobData) {
        return dynamoDbClient.getItem(GetItemRequest.builder()
                                                    .tableName(environmentVariables.getDeploymentDataTable())
                                                    .key(Map.of("deploymentName",
                                                                AttributeValue.builder()
                                                                              .s(jobData.environment() + "-" + jobData.distributionName())
                                                                              .build(),
                                                                "deploymentTime",
                                                                AttributeValue.builder()
                                                                              .n("0")
                                                                              .build()))
                                                    .build())
                             .item()
                             .get("executionArns")
                             .m()
                             .values()
                             .stream()
                             .map(AttributeValue::s)
                             .anyMatch(s -> s.equals(jobData.executionArn()));
    }

    private ReceiveMessageRequest getMessageRequest() {
        return ReceiveMessageRequest.builder()
                                    .waitTimeSeconds(Math.min(20, environmentVariables.getIdleTime()))
                                    .queueUrl(environmentVariables.getQueueUrl())
                                    .build();
    }
}
