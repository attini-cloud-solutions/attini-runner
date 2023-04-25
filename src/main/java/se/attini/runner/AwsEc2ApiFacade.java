package se.attini.runner;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;

/**
 * Interact with the EC2 API. This class interacts with the API directly, without the use of the EC2 SDK.
 * It does, however, use some general classes from the SDK for signing etc.
 * The main reason for this is the massive size of the EC2 SDK dependency, almost doubling the size of the Runner.
 */
public class AwsEc2ApiFacade {


    /**
     * Terminate an EC2 instance. This method is triggered from a JVM shutdown hook.
     * Therefore, it uses no Quarkus resources.
     *
     * @param instanceId The id of the instance to terminate
     */
    public static void terminateEc2Instance(String instanceId) {

        Map<String, List<String>> parameters = Map.of("Action",
                                                      List.of("TerminateInstances"),
                                                      "Version",
                                                      List.of("2016-11-15"),
                                                      "InstanceId",
                                                      List.of(instanceId));

        HttpExecuteResponse httpExecuteResponse = callApi(parameters);

        if (!httpExecuteResponse.httpResponse().isSuccessful()) {
            System.err.printf("Terminate EC2 instance failed. Status: %s: %s%n", httpExecuteResponse.httpResponse()
                                                                                                    .statusCode(),
                              httpExecuteResponse.httpResponse().statusText());

            httpExecuteResponse.responseBody()
                               .map(AbortableInputStream::delegate)
                               .map(AwsEc2ApiFacade::createString)
                               .map(s -> s.replace("\n", ""))
                               .map(s -> "Response body: ")
                               .ifPresent(System.err::println);
        }
    }

    private static HttpExecuteResponse callApi(Map<String, List<String>> parameters) {
        try (ContainerCredentialsProvider credentialsProvider = ContainerCredentialsProvider.builder().build();
             SdkHttpClient httpClient = UrlConnectionHttpClient.create()) {
            final String region = System.getenv("ATTINI_AWS_REGION");
            final String service = "ec2";
            SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                                                           .protocol("https")
                                                           .encodedPath("/")
                                                           .host("%s.%s.amazonaws.com".formatted(service, region))
                                                           .method(SdkHttpMethod.GET)
                                                           .rawQueryParameters(parameters)
                                                           .putHeader("content-type",
                                                                      "application/x-www-form-urlencoded; charset=utf-8")
                                                           .build();

            SdkHttpFullRequest signedRequest = Aws4Signer.create()
                                                         .sign(request,
                                                               Aws4SignerParams.builder()
                                                                               .signingName(service)
                                                                               .signingRegion(Region.of(region))
                                                                               .awsCredentials(credentialsProvider.resolveCredentials())
                                                                               .build());

            HttpExecuteRequest httpExecuteRequest = HttpExecuteRequest.builder()
                                                                      .request(signedRequest)
                                                                      .contentStreamProvider(signedRequest.contentStreamProvider()
                                                                                                          .orElse(null))
                                                                      .build();

            return httpClient.prepareRequest(httpExecuteRequest)
                             .call();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String createString(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
