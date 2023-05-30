package se.attini.runner;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.fs.util.ZipUtils;
import se.attini.runner.BeanFactory.CustomAwsClient;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;


@ApplicationScoped
public class DistributionSourceFiles {

    private static final Logger logger = Logger.getLogger(DistributionSourceFiles.class);


    private final S3Client s3Client;

    private String objectIdentifier;
    private Path sourcePath;

    @Inject
    public DistributionSourceFiles(@CustomAwsClient S3Client s3Client) {
        this.s3Client = requireNonNull(s3Client, "s3Client");
    }


    public synchronized Path sync(JobData jobData) {
        if (!jobData.objectIdentifier().equals(objectIdentifier)) {
            logger.info("downloading new source file");
            deleteOldSourceZip(sourcePath);
            this.sourcePath = getSourceZip(jobData);
            logger.info("downloaded new source file");
        }
        this.objectIdentifier = jobData.objectIdentifier();
        return extractSourceFiles(jobData, sourcePath);
    }

    private Path extractSourceFiles(JobData jobData, Path sourcePath) {
        try {
            Path sourceFilesDir = Files.createTempDirectory(jobData.distributionName() + "_" + jobData.stepName());
            ZipUtils.unzip(sourcePath, sourceFilesDir);
            return sourceFilesDir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteOldSourceZip(Path oldSourcePath) {
        if (oldSourcePath != null && oldSourcePath.toFile().delete()) {
            logger.info("deleted old source");
        }
    }

    private Path getSourceZip(JobData jobData) {
        File file = new File("attini_dist_source.zip");

        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String key = jobData.sourcePrefix() + "/" + jobData.distributionName() + ".zip";

        s3Client.getObject(GetObjectRequest.builder().bucket(jobData.sourceBucket()).key(key).build(),
                           ResponseTransformer.toFile(file));
        return file.toPath();
    }

}
