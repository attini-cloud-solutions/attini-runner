package se.attini.runner;


import java.util.concurrent.Executors;
import javax.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("dry-run")) {
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





        @Override
        public int run(String... args) {

            try {
                startupService.handleStartupTask();
            } catch (ScriptExecutionException e){
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
    }
}
