package se.attini.runner;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.sqs.model.Message;

class MessageConverterTest {

    @Test
    void convertMessage() {
        Message message = testMessage();
        JobData jobData = MessageConverter.toJobData(message);
        assertEquals("test-arn", jobData.executionArn());
        assertEquals("tes-sfn-token", jobData.responseToken());
        assertEquals(List.of("echo hello"), jobData.commands());
        assertEquals("dev", jobData.environment());
        assertEquals( "test-demo",jobData.distributionName());
        assertEquals("DeployApp", jobData.stepName());

    }

    private static Message testMessage() {
        return Message.builder()
                      .receiptHandle("test")
                      .body("""
                                    {
                                       "Properties":{
                                          "Commands":[
                                           "echo hello"
                                          ],
                                          "Runner":"AttiniDefaultRunner",
                                          "Environment":null
                                       },
                                       "deploymentPlanExecutionMetadata":{
                                          "retryCounter":0,
                                          "sfnToken":"tes-sfn-token",
                                          "stepName":"DeployApp",
                                          "executionArn":"test-arn",
                                          "executionStartTime":"2023-03-21T12:31:24.152Z"
                                       },
                                       "deploymentOriginData":{
                                          "distributionName":"test-demo",
                                          "deploymentTime":1679401866564,
                                          "deploymentSource":{
                                             "deploymentSourcePrefix":"dev/cdk-demo/5ed6cd45-636d-4dd9-b7ed-a90580fd0b7e/distribution-origin",
                                             "deploymentSourceBucket":"attini-artifact-store-eu-west-1-855066048591"
                                          },
                                          "environment":"dev",
                                          "distributionId":"5ed6cd45-636d-4dd9-b7ed-a90580fd0b7e",
                                          "deploymentName":"dev-cdk-demo",
                                          "objectIdentifier":"dev/cdk-demo/cdk-demo.zip#3v4JtzjjXZMJiS2_qAFYCdB0ZKATEJh3",
                                          "stackName":"dev-cdk-demo-deployment-plan",
                                          "distributionTags":{
                                            \s
                                          },
                                          "version":null,
                                          "samPackaged":false
                                       },
                                       "output":{
                                          "DeployDatabase":{
                                             "DatabaseArn":"arn:aws:dynamodb:eu-west-1:855066048591:table/demo-database-Database-12BTRYSYLU6PR"
                                          }
                                       },
                                       "dependencies":{
                                         \s
                                       },
                                       "customData":{
                                         \s
                                       },
                                       "stackParameters":{
                                          "BootstrapVersion":"15"
                                       },
                                       "taskConfigHashCode":-1102536878
                                    }
                                                                           
                                    """).build();
    }
}
