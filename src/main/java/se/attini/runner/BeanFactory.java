package se.attini.runner;


import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Qualifier;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sfn.SfnClient;

@ApplicationScoped
public class BeanFactory {

    @ApplicationScoped
    public SfnClient sfnClient() {
        return SfnClient.create();
    }

    @ApplicationScoped
    @CustomAwsClient
    public S3Client s3Client() {
        return S3Client.builder()
                       .overrideConfiguration(ClientOverrideConfiguration.builder()
                                                                         .apiCallTimeout(Duration.ofSeconds(240))
                                                                         .apiCallAttemptTimeout(Duration.ofSeconds(30))
                                                                         .retryPolicy(RetryPolicy.builder()
                                                                                                 .backoffStrategy(
                                                                                                         BackoffStrategy.defaultStrategy(
                                                                                                                 RetryMode.STANDARD))
                                                                                                 .numRetries(20)
                                                                                                 .build())
                                                                         .build())
                       .build();
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target({TYPE, METHOD, FIELD, PARAMETER})
    public @interface CustomAwsClient {
    }
}
