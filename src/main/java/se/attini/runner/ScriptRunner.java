package se.attini.runner;

import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.logging.Logger;


@ApplicationScoped
public class ScriptRunner {

    private static final Logger logger = Logger.getLogger(ScriptRunner.class);
    private final S3Logger s3Logger;

    private final ExecutorService executorService;
    private final EnvironmentVariables environmentVariables;

    @Inject
    public ScriptRunner(S3Logger s3Logger,
                        EnvironmentVariables environmentVariables) {
        this.s3Logger = requireNonNull(s3Logger, "s3Logger");
        this.environmentVariables = requireNonNull(environmentVariables, "environmentVariables");
        this.executorService = Executors.newFixedThreadPool(environmentVariables.getJobConcurrency());
    }

    public int runScript(Path path, int scriptTimeout) {
        return runScript(path, scriptTimeout, null);
    }


    public int runScript(Path path, int scriptTimeout, JobData jobData) {

        try {

            logger.debug("execution timeout is set to = " + scriptTimeout);


            String fileCommand = "chmod +x " + path.toString() + "; " + path;

            Process process = new ProcessBuilder()
                    .redirectErrorStream(true)
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .command(List.of(environmentVariables.getShell(),
                                     "-c",
                                     fileCommand))
                    .start();

            CompletableFuture<Void> loggingFeature = CompletableFuture
                    .runAsync(() -> logProcessOutput(jobData, process), executorService);

            boolean waitFor = process.waitFor(scriptTimeout, TimeUnit.SECONDS);
            if (!waitFor) {
                logger.error("Process is still running after " + scriptTimeout + " seconds, will destroy");
                process.destroy();
                s3Logger.syncToS3(jobData);
                throw new ScriptExecutionException("Script timed out after " + scriptTimeout + " seconds");
            }
            int exitCode = process.waitFor();
            loggingFeature.get();
            s3Logger.syncToS3(jobData);
            return exitCode;
        } catch (ScriptExecutionException e) {
            throw e;
        } catch (Exception e) {
            s3Logger.syncToS3(jobData);
            logger.error("Error while running script", e);
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    private void logProcessOutput(JobData jobData, Process process) {
        try( BufferedReader bufferedReader = process.inputReader()) {
            bufferedReader.lines()
                          .forEach(s -> {
                              try {
                                  System.out.println(s);
                                  if (jobData != null) {
                                      s3Logger.log(s, jobData);
                                  }
                              } catch (Exception e) {
                                  String stepName = jobData != null ? jobData.stepName() : "";
                                  logger.error("Error while logging script output for step " + stepName + ", message: " + e.getMessage());
                                  throw new RuntimeException(e.getMessage(), e);
                              }
                          });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
