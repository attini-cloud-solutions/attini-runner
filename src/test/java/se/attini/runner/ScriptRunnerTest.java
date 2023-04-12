package se.attini.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static se.attini.runner.TestJobData.createJobData;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;


@QuarkusTest
class ScriptRunnerTest {

    @InjectMock
    S3Logger s3Loggers;

    @InjectMock
    EnvironmentVariables environmentVariables;

    ScriptRunner scriptRunner;

    @BeforeEach
    void setUp() {
        when(environmentVariables.getJobConcurrency()).thenReturn(1);
        when(environmentVariables.getShell()).thenReturn( "/bin/bash");
        this.scriptRunner = new ScriptRunner(s3Loggers, environmentVariables);
    }

    @Test
    public void test_startScript() {
        Path script = Paths.get("src", "test", "resources", "test-script.sh");


        int i = scriptRunner.runScript(script, 1000);

        assertEquals(0, i);
        verify(s3Loggers, never()).log(anyString(), any());
    }

    @Test
    public void test_jobRequest() {
        Path script = Paths.get("src", "test", "resources", "test-script.sh");

        JobData jobData = createJobData();
        int i = scriptRunner.runScript(script, 1000, jobData);

        assertEquals(0, i);
        verify(s3Loggers).log("Testing", jobData);
    }
}
