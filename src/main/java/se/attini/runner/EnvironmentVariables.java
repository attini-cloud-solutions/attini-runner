package se.attini.runner;


import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

@ApplicationScoped
public class EnvironmentVariables {

    private static final int JOB_CONCURRENCY_DEFAULT = 5;

    private static final int IDLE_TTL_DEFAULT = 3600;

    private static final Logger logger = Logger.getLogger(EnvironmentVariables.class);


    public String getQueueUrl(){
        return System.getenv("ATTINI_QUEUE_URL");
    }

    public String getDeploymentDataTable(){
        return System.getenv("ATTINI_DEPLOY_DATA_TABLE");
    }

    public String getResourceStatesTable(){
        return System.getenv("ATTINI_RESOURCE_STATE_TABLE");
    }

    public String getRunnerResourceName(){
        return System.getenv("ATTINI_RUNNER_RESOURCE_NAME");
    }

    public String getMetaDataEndpoint(){
        return System.getenv("ECS_CONTAINER_METADATA_URI_V4");
    }

    public String getRegion(){
        return System.getenv("ATTINI_AWS_REGION");
    }

    public String getArtifactStoreBucketName(){
        return System.getenv("ATTINI_ARTIFACT_STORE");
    }

    public Optional<String> getEc2InstanceId(){
        return Optional.ofNullable(System.getenv("ATTINI_EC2_INSTANCE_ID"));
    }

    public String getShell(){
        return System.getenv("SHELL") != null ? System.getenv("SHELL") : "/bin/bash";
    }

    public int getScriptTimeout(int defaultValue){
        try {
            if (System.getenv("ATTINI_JOB_TIMEOUT") == null){
                return defaultValue;
            }
            return Integer.parseInt(System.getenv("ATTINI_JOB_TIMEOUT"));
        } catch (NumberFormatException e) {
            logger.warn("ATTINI_JOB_TIMEOUT is not an integer, returning default value = " + defaultValue);
            return defaultValue;
        }
    }

    public int getStartupCommandsTimeout(int defaultValue){
        try {
            if (System.getenv("ATTINI_STARTUP_COMMANDS_TIMEOUT") == null){
                return defaultValue;
            }
            return Integer.parseInt(System.getenv("ATTINI_STARTUP_COMMANDS_TIMEOUT"));
        } catch (NumberFormatException e) {
            logger.warn("ATTINI_STARTUP_COMMANDS_TIMEOUT is not an integer, returning default value = " + defaultValue);
            return defaultValue;
        }
    }


    public int getIdleTime(){
        try {
            if (System.getenv("ATTINI_RUNNER_IDLE_TTL") == null){
                return IDLE_TTL_DEFAULT;
            }
            return Integer.parseInt(System.getenv("ATTINI_RUNNER_IDLE_TTL"));
        } catch (NumberFormatException e) {
            logger.warn("ATTINI_RUNNER_IDLE_TTL is not an integer, returning default value = " + IDLE_TTL_DEFAULT);
            return IDLE_TTL_DEFAULT;
        }
    }

    public int getConfigurationHashCode(){
        try {
            return Integer.parseInt(System.getenv("ATTINI_CONFIGURATION_HASH"));
        } catch (NumberFormatException e) {
            logger.warn("no configuration hash code set on container");
            return 0;
        }
    }

    public int getJobConcurrency(){
        try {
            if (System.getenv("ATTINI_MAX_CONCURRENT_JOBS") == null){
                return JOB_CONCURRENCY_DEFAULT;
            }
            return Integer.parseInt(System.getenv("ATTINI_MAX_CONCURRENT_JOBS"));
        } catch (NumberFormatException e) {
            logger.warn("ATTINI_RUNNER_CONCURRENCY is not an integer, returning default value = " + JOB_CONCURRENCY_DEFAULT);
            return JOB_CONCURRENCY_DEFAULT;
        }
    }

    public String getAccountId() {
        return System.getenv("ATTINI_AWS_ACCOUNT");
    }
}
