package se.attini.runner;


import java.util.Map;
import java.util.concurrent.Executors;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.core.Version;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import se.attini.runner.commandmode.CommandModeApp;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

@QuarkusMain
public class Main {

    public static void main(String[] args) {

        if (args.length > 0 && args[0].equals("dry-run")) {
            System.exit(0);
        }

        if (args.length > 0 && args[0].equals("command-mode")) {
            System.setProperty("quarkus.log.file.enable", "true");
            System.setProperty("quarkus.log.console.enable", "false");
            System.setProperty("quarkus.log.file.path", CommandModeApp.LOG_FILE);
            Quarkus.run(CommandModeApp.class, args);
            System.exit(0);
        }
        Quarkus.run(AttiniRunnerApp.class, args);
    }

    public static class AttiniRunnerApp implements QuarkusApplication {

        private static final Logger logger = Logger.getLogger(AttiniRunnerApp.class);


        @Inject
        Shutdown shutdown;

        @Inject
        SqsListener sqsListener;

        @Inject
        StartupService startupService;

        @Inject
        SfnFacade sfnFacade;

        @Inject
        EnvironmentVariables environmentVariables;

        @ConfigProperty(name = "quarkus.application.version")
        String version;


        @Override
        public int run(String... args) {

            if (args.length == 2) {
                Version requiredVersion = toVersion(args[1]);
                Version currentVersion = toVersion(version);

                if (requiredVersion.compareTo(currentVersion) > 0) {
                    String errorMessage = "The current runner version is older then the required version. Required version: " + args[1] + ", current version: " + version;
                    logger.error(errorMessage);
                    sfnFacade.sendTaskFailed(args[0],
                                             "Startup tasks failed",
                                             errorMessage);
                   return 1;
                }

            }

            try {
                addEc2ShutdownHook();
                startupService.handleStartupTask();
            } catch (ScriptExecutionException e) {
                shutdown.shutdown();
                logger.error("Failed performing startup tasks");
                sfnFacade.sendTaskFailed(args[0], "Startup tasks failed", e.getMessage());
                return 1;
            } catch (Exception e) {
                shutdown.shutdown();
                logger.error("Failed performing startup tasks", e);
                sfnFacade.sendTaskFailed(args[0], "Startup tasks failed", e.getMessage());
               return 1;
            }

            Executors.newSingleThreadExecutor(r -> new Thread(r, "sqs-listener-thread"))
                     .submit(() -> {
                         while (!shutdown.shouldExit()) {
                             try {
                                 sqsListener.listen();
                             } catch (Exception e) {
                                 logger.error(e.getMessage(), e);
                             }
                         }
                     });
            shutdown.scheduleShutdown();
            Quarkus.waitForExit();
            return 0;
        }

        private static Version toVersion(String versionString) {
            if (versionString.contains("-")) {
                versionString = versionString.substring(0,versionString.indexOf("-"));
            }
            String[] splitVersion = versionString.split("\\.");
            return new Version(Integer.parseInt(splitVersion[0]),
                               Integer.parseInt(splitVersion[1]),
                               Integer.parseInt(splitVersion[2]),
                               null,
                               null,
                               null);
        }

        private void addEc2ShutdownHook() {
            environmentVariables.getEc2InstanceId()
                                .ifPresent(instanceId -> {
                                    logger.info("Registered shutdown hook for terminating ec2 instance");
                                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                        if (ec2ShutdownHookEnabled()) {
                                            AwsEc2ApiFacade.terminateEc2Instance(instanceId);
                                        } else {
                                            System.out.println(
                                                    "Shutdown hook is disabled. Will leave EC2 instance running.");
                                        }
                                    }));
                                });
        }

        private boolean ec2ShutdownHookEnabled() {
            try (DynamoDbClient dbClient = DynamoDbClient.create()) {
                return !dbClient.getItem(GetItemRequest.builder()
                                                       .consistentRead(true)
                                                       .tableName(environmentVariables.getResourceStatesTable())
                                                       .key(Map.of("resourceType",
                                                                   AttributeValue.builder()
                                                                                 .s("Runner")
                                                                                 .build(),
                                                                   "name",
                                                                   AttributeValue.builder()
                                                                                 .s(environmentVariables.getRunnerResourceName())
                                                                                 .build()))
                                                       .build()).item().get("shutdownHookDisabled").bool();
            } catch (Exception e) {
                System.err.println("Error when reading shutdown hook toggle, will treat as enabled. Error: " + e.getMessage());
                e.printStackTrace();
                return true;
            }
        }
    }
}