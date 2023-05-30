package se.attini.runner;


import static java.util.Objects.requireNonNull;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.runtime.Quarkus;

@ApplicationScoped
public class Shutdown {

    private static final Logger logger = Logger.getLogger(Shutdown.class);
    private final EnvironmentVariables environmentVariables;

    private TimerTask timerTask;
    private final Timer timer;
    private boolean shouldExit = false;


    private CompletableFuture<Void> completableFuture = CompletableFuture.completedFuture(null);

    @Inject
    public Shutdown(EnvironmentVariables environmentVariables) {
        this.environmentVariables = requireNonNull(environmentVariables, "environmentVariables");
        timer = new Timer("shutdown-monitor-thread");
    }

    public synchronized void scheduleShutdown() {
        scheduleShutdown(null);
    }

    public synchronized void shutdown(){
        completableFuture.thenAccept(unused -> {
            if (timerTask != null){
                timerTask.cancel();
            }
            shouldExit = true;
            Quarkus.asyncExit();
        });

    }

    public boolean shouldExit(){
        return shouldExit;
    }

    public synchronized void scheduleShutdown(CompletableFuture<Void> processFeatures) {
        setFeature(processFeatures);

        int timeToLive = environmentVariables.getIdleTime();

        if (timerTask != null) {
            logger.info("resetting shutdown timer");
            timerTask.cancel();
        }

        timerTask = new TimerTask() {
            int countdown = timeToLive;

            @Override
            public void run() {
                countdown--;
                logger.debug("Time until shutdown = " + countdown);
                if (!completableFuture.isDone()) {
                    logger.debug("Jobs are still running, resetting counter");
                    countdown = timeToLive;
                }
                if (countdown <= 0 && completableFuture.isDone()) {
                    logger.info("All jobs are done and TTL counter hit zero, will exit");
                    timerTask.cancel();
                    timer.cancel();
                    shouldExit = true;
                    Quarkus.asyncExit();
                }
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 1000);

    }

    private void setFeature(CompletableFuture<Void> processFeatures) {
        if (!this.completableFuture.isDone() && processFeatures != null) {
            this.completableFuture = CompletableFuture.allOf(this.completableFuture, processFeatures);
        } else if (processFeatures != null) {
            this.completableFuture = processFeatures;
        }

    }
}
