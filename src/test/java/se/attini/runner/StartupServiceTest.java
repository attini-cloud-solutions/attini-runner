package se.attini.runner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

@QuarkusTest
class StartupServiceTest {

    @InjectMock
    private DynamoDbClient dynamoDbClient;
    @InjectMock
    private EnvironmentVariables environmentVariables;
    @InjectMock
    private ContainerMetadataFacade containerMetadataFacade;
    @InjectMock
    private JobCreator jobCreator;
    @InjectMock
    private ScriptRunner scriptRunner;

    StartupService startupService;

    @BeforeEach
    void setUp() {
        startupService = new StartupService(dynamoDbClient,
                                            environmentVariables,
                                            containerMetadataFacade,
                                            jobCreator,
                                            scriptRunner);

    }

    @Test
    void RunStartUpCommands() {

        when(containerMetadataFacade.getMetadata()).thenReturn(new ContainerMetadata(false, "12323", "test.test"));

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder()
                                           .item(Map.of("startupCommands",
                                                        AttributeValue.builder()
                                                                      .l(AttributeValue.builder()
                                                                                       .s("some command")
                                                                                       .build())
                                                                      .build(),
                                                        "environment",
                                                        AttributeValue.builder().s("dev").build(),
                                                        "distributionName",
                                                        AttributeValue.builder().s("test-dist").build()))
                                           .build());

        Path scriptFile = Paths.get("src",
                           "test",
                           "resources",
                           "test-script.sh");
        when(jobCreator.createStartupFile(anyList(), anyString(), anyString())).thenReturn(scriptFile);
        when(environmentVariables.getStartupCommandsTimeout(anyInt())).thenReturn(3600);

        startupService.handleStartupTask();

        verify(scriptRunner).runScript(scriptFile, 3600);

    }

    @Test
    void RunNoStartUpCommands() {

        when(containerMetadataFacade.getMetadata()).thenReturn(new ContainerMetadata(false, "12323", "test.test"));

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder()
                                           .item(Map.of("environment",
                                                        AttributeValue.builder().s("dev").build(),
                                                        "distributionName",
                                                        AttributeValue.builder().s("test-dist").build()))
                                           .build());

        startupService.handleStartupTask();

        verify(scriptRunner, never()).runScript(any(), anyInt());

    }
}
