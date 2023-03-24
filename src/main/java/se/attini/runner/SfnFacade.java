package se.attini.runner;


import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskFailureRequest;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;
import software.amazon.awssdk.services.sfn.model.TaskTimedOutException;

@ApplicationScoped
public class SfnFacade {

    private static final Logger logger = Logger.getLogger(SfnFacade.class);

    private final SfnClient sfnClient;

    private final ObjectMapper objectMapper;

    @Inject
    public SfnFacade(SfnClient sfnClient) {
        this.sfnClient = sfnClient;
        objectMapper = new ObjectMapper();

    }

    public void setTaskSuccess(JobData jobData, Path outputFile) {
        try {
            sfnClient
                    .sendTaskSuccess(SendTaskSuccessRequest.builder()
                                                           .taskToken(jobData.responseToken())
                                                           .output(createOutput(outputFile).toString())
                                                           .build());
        } catch (TaskTimedOutException e) {
            logger.error("Failed to send success response to Step Function, message = " + e.getMessage());
        }
    }

    public void sendTaskFailed(String responseToken, String error, String cause) {
        try {
            sfnClient.sendTaskFailure(SendTaskFailureRequest.builder()
                                                            .taskToken(responseToken)
                                                            .error(error)
                                                            .cause(cause)
                                                            .build());
        } catch (TaskTimedOutException e) {
            logger.error("Failed to send failure response to Step Function, message = " + e.getMessage());
        }
    }


    private JsonNode createOutput(Path outputFile) {
        try {
            if (!outputFile.toFile().exists()) {
                logger.info("The output file has been deleted, no output from step will be included in payload");
                return objectMapper.createObjectNode();
            }
            JsonNode jsonNode = objectMapper.readTree(outputFile.toFile());
            if (jsonNode == null || jsonNode.isMissingNode()) {
                return objectMapper.createObjectNode();
            }
            return jsonNode;
        } catch (IOException e) {
            logger.info("The output is not valid json, will wrap it");
            try {
                String output = Files.readString(outputFile);
                if (output.endsWith("\n")){
                    output = output.substring(0, output.length()-1);
                }
                return objectMapper.createObjectNode().put("result",output);
            } catch (IOException ex) {
                throw new UncheckedIOException("Could not ready output file", e);
            }
        }
    }
}
