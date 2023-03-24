package se.attini.runner;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import se.attini.runner.BeanFactory.CustomAwsClient;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;


@ApplicationScoped
public class S3Logger {

    private static final Logger logger = Logger.getLogger(S3Logger.class);

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    private final Map<String, File> logCash;

    private final Map<String, Instant> lastWrite;


    @Inject
    public S3Logger(@CustomAwsClient S3Client s3Client, ObjectMapper objectMapper) {
        this.s3Client = requireNonNull(s3Client, "s3Client");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
        this.logCash = new ConcurrentHashMap<>();
        this.lastWrite = new ConcurrentHashMap<>();
    }

    public void log(String line, JobData jobData) {
        Instant timestamp = Instant.now();
        String key = createKey(jobData);
        File file = logCash.computeIfAbsent(key, s -> {
            String fileName = jobData.stepName() +
                              "-" +
                              jobData.executionArn()
                                     .substring(jobData.executionArn()
                                                       .lastIndexOf(":") + 1);
            return createTempFile(fileName);
        });


        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("timestamp", timestamp.toEpochMilli());
        objectNode.put("data", line);

        appendToFile(objectNode, file);

        Instant instant = lastWrite.computeIfAbsent(key, s -> Instant.now());

        if (Instant.now().isAfter(instant.plusSeconds(10))) {

            logger.info("Syncing step " + jobData.stepName() + " to S3");

            putObject(jobData, key);
            lastWrite.put(key, Instant.now());
        }


    }

    private static File createTempFile(String fileName) {
        try {

            File file = File.createTempFile(fileName,
                                           ".log");

            file.deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void appendToFile(JsonNode line, File file) {
        try {
            FileUtils.write(file, line + "\n", StandardCharsets.UTF_8, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void syncToS3(JobData jobData) {
        if (jobData != null) {
            String key = createKey(jobData);
            if (logCash.get(key) != null && logCash.get(key).exists()) {
                logger.info("Performing final sync for step " + jobData.stepName() + " to S3");
                putObject(jobData, key);
                //noinspection ResultOfMethodCallIgnored
                logCash.get(key).delete();
                logCash.remove(key);
            }
        }
    }
    private void putObject(JobData jobData, String key) {
        try {
            s3Client.putObject(PutObjectRequest.builder().bucket(jobData.sourceBucket()).key(key).build(),
                               RequestBody.fromFile(logCash.get(key)));
        } catch (S3Exception e) {
            throw new RuntimeException(
                    "Could not sync logs to S3. Please make sure that the Runner has s3:PutObject permission",
                    e);
        }
    }

    private String createKey(JobData jobData) {
        return "attini/deployment/logs/runner/"
               + jobData.environment() +
               "/" + jobData.distributionName() +
               "/" + jobData.distributionId() +
               "/" + jobData.stepName() +
               "/" + jobData.executionArn().substring(jobData.executionArn().lastIndexOf(":") + 1);

    }

}
