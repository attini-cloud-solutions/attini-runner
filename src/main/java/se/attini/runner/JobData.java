package se.attini.runner;

import java.util.List;
import java.util.Map;

public record JobData(List<String> commands,
                      String responseToken,
                      String receiptHandle,
                      String input,
                      String executionArn,
                      String environment,
                      String distributionName,
                      int jobConfigHashCode,
                      String stepName,
                      String sourceBucket,
                      String sourcePrefix,
                      String objectIdentifier,
                      String distributionId,
                      Map<String, String> environmentVariables) {

}
