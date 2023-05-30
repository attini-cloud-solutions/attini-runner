package se.attini.runner;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@ApplicationScoped
public class ContainerMetadataFacade {

    private static final Logger logger = Logger.getLogger(ContainerMetadataFacade.class);


    private final EnvironmentVariables environmentVariables;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final static String LOG_URL = "https://%s.console.aws.amazon.com/cloudwatch/home?region=%s#logsV2:log-groups/log-group/%s/log-events/%s";


    @Inject
    public ContainerMetadataFacade(EnvironmentVariables environmentVariables) {
        this.environmentVariables = requireNonNull(environmentVariables, "environmentVariables");
    }

    public ContainerMetadata getMetadata() {
        try {
            logger.debug("Checking container metadata for status");
            String metaDataEndpoint = environmentVariables.getMetaDataEndpoint();
            String body = HttpClient.newHttpClient()
                                    .send(HttpRequest.newBuilder()
                                                     .uri(URI.create(metaDataEndpoint))
                                                     .GET()
                                                     .build(), HttpResponse.BodyHandlers.ofString())
                                    .body();

            logger.debug(body);
            JsonNode jsonNode = objectMapper.readTree(body);
            String logUrl = createLogUrl(jsonNode);
            return new ContainerMetadata(jsonNode.path("DesiredStatus").asText().equals("STOPPED"),
                                         jsonNode.path("Labels").path("com.amazonaws.ecs.task-arn").asText(),
                                         logUrl);
        } catch (IOException | InterruptedException e) {
            throw new ContainerMetadataException("Error when getting container metadata", e);
        }
    }

    private String createLogUrl(JsonNode jsonNode) {
        if (!jsonNode.isMissingNode() &&
            !jsonNode.path("LogOptions")
                     .path("awslogs-region")
                     .isMissingNode() &&
            !jsonNode.path("LogOptions")
                     .path("awslogs-group")
                     .isMissingNode() &&
            !jsonNode.path("LogOptions")
                     .path("awslogs-stream")
                     .isMissingNode()) {
            return createLogUrl(jsonNode.path("LogOptions").path("awslogs-region").asText(),
                         jsonNode.path("LogOptions").path("awslogs-group").asText(),
                         jsonNode.path("LogOptions").path("awslogs-stream").asText());
        }
        return null;
    }

    private String createLogUrl(String region, String logGroup, String logStream) {
        return String.format(LOG_URL, region, region, encode(logGroup), encode(logStream));
    }


    private static String encode(String value) {
        return URLEncoder.encode(URLEncoder.encode(value, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

}
