package se.attini.runner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@QuarkusTest
class S3LoggerTest {

    @InjectMock
    S3Client s3Client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    S3Logger s3Logger;

    @BeforeEach
    void setUp() {
        s3Logger = new S3Logger(s3Client,objectMapper);
    }

    @Test
    void log() {
        JobData jobData = TestJobData.createJobData();
        s3Logger.log("this happened", jobData);
        s3Logger.syncToS3(jobData);
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

    }
}
