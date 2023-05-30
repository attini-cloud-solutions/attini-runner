package se.attini.runner.commandmode.registercdkstack;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.attini.runner.EnvironmentVariables;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;


@ApplicationScoped
public class RegisterCdkStacksService {

    private static final Logger logger = Logger.getLogger(RegisterCdkStacksService.class);

    private final EnvironmentVariables environmentVariables;
    private final ObjectMapper objectMapper;
    private final DynamoDbClient dynamoDbClient;


    private static final String STEP_NAME = "stepName";
    private static final String OBJECT_IDENTIFIER = "attiniObjectIdentifier";
    private static final String DISTRIBUTION_ID = "distributionId";
    private static final String DISTRIBUTION_NAME = "distributionName";
    private static final String ENVIRONMENT = "environment";


    @Inject
    public RegisterCdkStacksService(EnvironmentVariables environmentVariables,
                                    ObjectMapper objectMapper,
                                    DynamoDbClient dynamoDbClient) {
        this.environmentVariables = requireNonNull(environmentVariables, "environmentVariables");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
        this.dynamoDbClient = requireNonNull(dynamoDbClient, "dynamoDbClient");
    }

    public void registerStacks(String request) {

        logger.info("Processing cdk stack event: " + request);
        RegisterCdkStacksEvent event = createEvent(request);


        event.stacks()
             .stream()
             .map(cdkStack -> {
                 CdkEnvironment environment = cdkStack.environment();
                 String region = isUnknown(environment.region()) ? environmentVariables.getRegion() : environment.region();
                 String account = isUnknown(environment.account()) ? environmentVariables.getAccountId() : environment.account();
                 return new CdkStack(cdkStack.name(), cdkStack.id(), new CdkEnvironment(region, account));
             })
             .forEach(cdkStack -> saveCdkStack(event, event.stepName(), cdkStack));

    }

    public JsonNode formatOutput(String request) {

        logger.info("Formatting output for cdk stack event: " + request);
        RegisterCdkStacksEvent event = createEvent(request);


        return objectMapper.valueToTree(event.stacks()
                                             .stream()
                                             .collect(Collectors.toMap(CdkStack::id, cdkStack -> {

                                                 long nrOfStacksWithName = event.stacks()
                                                                                .stream()
                                                                                .filter(cdkStack1 -> cdkStack1.name().equals(cdkStack.name()))
                                                                                .count();
                                                 if (nrOfStacksWithName > 1) {
                                                     return "Could not resolve stack output, multiple stacks with same name detected in app";
                                                 }
                                                 return event.outputs().getOrDefault(cdkStack.name(), Collections.emptyMap());
                                             })));
    }
    public void saveCdkStack(RegisterCdkStacksEvent event, String stepName, CdkStack cdkStack) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                                             .tableName(environmentVariables.getResourceStatesTable())
                                             .item(Map.of("resourceType",
                                                          stringAttribute("CloudformationStack"),
                                                          "name",
                                                          stringAttribute("%s-%s-%s".formatted(cdkStack.name(),
                                                                                               cdkStack.environment()
                                                                                                       .region(),
                                                                                               cdkStack.environment()
                                                                                                       .account())),
                                                          STEP_NAME,
                                                          stringAttribute(stepName),
                                                          DISTRIBUTION_NAME,
                                                          stringAttribute(event.distributionName()),
                                                          DISTRIBUTION_ID,
                                                          stringAttribute(event.distributionId()),
                                                          OBJECT_IDENTIFIER,
                                                          stringAttribute(event.objectIdentifier()),
                                                          ENVIRONMENT,
                                                          stringAttribute(event.environment()),
                                                          "stackType", stringAttribute("Cdk")
                                             )).build());
    }

    @Deprecated
    //User by older versions of the Attini framework, to be removed.
    public JsonNode registerStacksDeprecated(String request) {

        logger.info("Processing cdk stack event: " + request);
        RegisterCdkStacksEvent event = createEvent(request);


        event.stacks()
             .stream()
             .map(cdkStack -> {
                 CdkEnvironment environment = cdkStack.environment();
                 String region = isUnknown(environment.region()) ? environmentVariables.getRegion() : environment.region();
                 String account = isUnknown(environment.account()) ? environmentVariables.getAccountId() : environment.account();
                 return new CdkStack(cdkStack.name(), cdkStack.id(), new CdkEnvironment(region, account));
             })
             .forEach(cdkStack -> saveCdkStack(event, event.stepName(), cdkStack));

        return objectMapper.valueToTree(event.stacks()
                                             .stream()
                                             .collect(Collectors.toMap(CdkStack::id, cdkStack -> {

                                                 long nrOfStacksWithName = event.stacks()
                                                                                .stream()
                                                                                .filter(cdkStack1 -> cdkStack1.name().equals(cdkStack.name()))
                                                                                .count();
                                                 if (nrOfStacksWithName > 1) {
                                                     return "Could not resolve stack output, multiple stacks with same name detected in app";
                                                 }
                                                 return event.outputs().getOrDefault(cdkStack.name(), Collections.emptyMap());
                                             })));
    }

    private static AttributeValue stringAttribute(String value) {
        return AttributeValue.builder().s(value).build();
    }



    private RegisterCdkStacksEvent createEvent(String request) {
        try {

            return objectMapper.readValue(request, RegisterCdkStacksEvent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isUnknown(String value) {
        return value.contains("unknown-");
    }


}
