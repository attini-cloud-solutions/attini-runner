package se.attini.runner;


import java.util.Map;
import java.util.concurrent.Executors;
import javax.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import se.attini.runner.registercdkstack.RegisterCdkStackApp;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

@QuarkusMain
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("dry-run")) {
            System.exit(0);
        }

        if (args.length > 0 && args[0].equals("register-cdk-stacks")) {
            System.setProperty("quarkus.log.file.enable","true");
            System.setProperty("quarkus.log.console.enable","false");
            System.setProperty("quarkus.log.file.path", "attini-runner-commands.log");
            Quarkus.run(RegisterCdkStackApp.class, args);
            System.exit(0);
        }

        if (args.length == 0){
            Quarkus.run(AttiniRunnerApp.class, args);
        }

        System.err.println("Invalid number of arguments");
        System.exit(1);

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


        @Override
        public int run(String... args) {

            try {
                addEc2ShutdownHook();
                startupService.handleStartupTask();
            } catch (ScriptExecutionException e) {
                shutdown.shutdown();
                logger.error("Failed performing startup tasks");
                sfnFacade.sendTaskFailed(args[0], "Startup tasks failed", e.getMessage());
                System.exit(1);
            } catch (Exception e) {
                shutdown.shutdown();
                logger.error("Failed performing startup tasks", e);
                sfnFacade.sendTaskFailed(args[0], "Startup tasks failed", e.getMessage());
                System.exit(1);
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
                System.err.println("Error when reading shutdown hook, will treat as enabled. Error: " + e.getMessage());
                e.printStackTrace();
                return true;
            }
        }
    }
}
