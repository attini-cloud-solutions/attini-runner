package se.attini.runner;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.logging.Logger;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@ApplicationScoped
public class StartupService {

    private static final Logger logger = Logger.getLogger(StartupService.class);


    private final DynamoDbClient dynamoDbClient;
    private final EnvironmentVariables environmentVariables;
    private final ContainerMetadataFacade containerMetadataFacade;
    private final JobCreator jobCreator;
    private final ScriptRunner scriptRunner;


    @Inject
    public StartupService(DynamoDbClient dynamoDbClient,
                          EnvironmentVariables environmentVariables,
                          ContainerMetadataFacade containerMetadataFacade,
                          JobCreator jobCreator,
                          ScriptRunner scriptRunner) {
        this.dynamoDbClient = requireNonNull(dynamoDbClient, "dynamoDbClient");
        this.environmentVariables = requireNonNull(environmentVariables, "environmentVariables");
        this.containerMetadataFacade = requireNonNull(containerMetadataFacade, "containerMetadataFacade");
        this.jobCreator = requireNonNull(jobCreator, "jobCreator");
        this.scriptRunner = requireNonNull(scriptRunner, "scriptRunner");
    }

    public void handleStartupTask() {

        setAsStarted();

        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
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
                                                         .item();

        if (item.containsKey("startupCommands")) {
            List<String> startupCommands = item.get("startupCommands")
                                               .l()
                                               .stream()
                                               .map(AttributeValue::s)
                                               .toList();

            logger.info("Running the following startup commands: ");
            startupCommands.forEach(logger::info);

            Path startupFile = jobCreator.createStartupFile(startupCommands,
                                                            item.get("environment").s(),
                                                            item.get("distributionName").s());
            int exitCode = scriptRunner.runScript(startupFile, environmentVariables.getStartupCommandsTimeout(3600));
            if (exitCode != 0) {
                throw new ScriptExecutionException("Startup script finished with exit code = " + exitCode + containerMetadataFacade.getMetadata()
                                                                                                                           .getLogUrl()
                                                                                                                           .map(s -> ". See complete logs at: " + s)
                                                                                                                           .orElse(""));
            }

        }
    }

    private void setAsStarted() {
        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                                                       .tableName(environmentVariables.getResourceStatesTable())
                                                       .key(Map.of("resourceType",
                                                                   AttributeValue.builder()
                                                                                 .s("Runner")
                                                                                 .build(),
                                                                   "name",
                                                                   AttributeValue.builder()
                                                                                 .s(environmentVariables.getRunnerResourceName())
                                                                                 .build()))
                                                       .updateExpression("SET #started = :v_started")
                                                       .conditionExpression("#taskId = :v_taskId")
                                                       .expressionAttributeNames(Map.of("#started",
                                                                                        "started",
                                                                                        "#taskId",
                                                                                        "taskId"))
                                                       .expressionAttributeValues(Map.of(":v_started",
                                                                                         AttributeValue.builder()
                                                                                                       .bool(true)
                                                                                                       .build(),
                                                                                         ":v_taskId",
                                                                                         AttributeValue.builder()
                                                                                                       .s(containerMetadataFacade.getMetadata()
                                                                                                                                 .taskId())
                                                                                                       .build()))
                                                       .build());
        } catch (ConditionalCheckFailedException e) {
            logger.info(
                    "Conditional check failed when completing startup tasks. This normally indicated that parallel tasks exist and this one is redundant.");
        }
    }

}
