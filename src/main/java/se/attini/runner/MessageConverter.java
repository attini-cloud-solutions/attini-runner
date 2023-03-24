package se.attini.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import software.amazon.awssdk.services.sqs.model.Message;

public class MessageConverter {

    private static String createInput(String messageBody) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            ObjectNode objectNode = (ObjectNode) objectMapper.readTree(messageBody);
            return objectNode.remove(List.of("Properties",
                                             "RunnerConfig",
                                             "attiniActionType",
                                             "deploymentPlanExecutionMetadata"))
                             .toString();
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

    }

    public static JobData toJobData(Message message) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            JsonNode jsonNode = objectMapper.readTree(message.body());

            JsonNode commands = jsonNode.path("Properties").path("Commands");
            List<String> commandsList = new ArrayList<>();

            if (!commands.isMissingNode()) {
                commands.forEach(command -> commandsList.add(command.asText()));
            }


            HashMap<String, String> envVariables = new HashMap<>();
            jsonNode.path("Properties")
                    .path("Environment").fields()
                    .forEachRemaining(stringJsonNodeEntry -> envVariables.put(stringJsonNodeEntry.getKey(),
                                                                              stringJsonNodeEntry.getValue()
                                                                                                 .asText()));

            return new JobData(commandsList,
                               jsonNode.path("deploymentPlanExecutionMetadata").path("sfnToken").asText(),
                               message.receiptHandle(),
                               createInput(message.body()),
                               jsonNode.path("deploymentPlanExecutionMetadata").path("executionArn").asText(),
                               jsonNode.path("deploymentOriginData").path("environment").asText(),
                               jsonNode.path("deploymentOriginData").path("distributionName").asText(),
                               jsonNode.path("taskConfigHashCode").asInt(),
                               jsonNode.path("deploymentPlanExecutionMetadata").path("stepName").asText(),
                               jsonNode.path("deploymentOriginData")
                                       .path("deploymentSource")
                                       .path("deploymentSourceBucket")
                                       .asText(),
                               jsonNode.path("deploymentOriginData")
                                       .path("deploymentSource")
                                       .path("deploymentSourcePrefix")
                                       .asText(),
                               jsonNode.path("deploymentOriginData").path("objectIdentifier").asText(),
                               jsonNode.path("deploymentOriginData").path("distributionId").asText(),
                               envVariables);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
