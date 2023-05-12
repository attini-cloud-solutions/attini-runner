package se.attini.runner.commandmode;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import javax.enterprise.context.ApplicationScoped;

import se.attini.runner.BeanFactory;
import se.attini.runner.EnvironmentVariables;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ApplicationScoped
public class ArtifactStoreFacade {

    private final S3Client s3Client;
    private final EnvironmentVariables environmentVariables;

    public ArtifactStoreFacade(@BeanFactory.CustomAwsClient S3Client s3Client, EnvironmentVariables environmentVariables)  {
        this.s3Client = requireNonNull(s3Client, "s3Client");
        this.environmentVariables = environmentVariables;
    }


    public void store(String key, String path){
       s3Client.putObject(PutObjectRequest.builder().key(key).bucket(environmentVariables.getArtifactStoreBucketName()).build(),
                                                                 RequestBody.fromFile(Path.of(path)));
    }
}
