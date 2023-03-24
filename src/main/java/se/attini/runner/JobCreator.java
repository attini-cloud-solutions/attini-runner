package se.attini.runner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;


@ApplicationScoped
public class JobCreator {

    private static final Logger logger = Logger.getLogger(JobCreator.class);

    public Path createScriptFile(JobData jobData,
                                 Path inputFile,
                                 Path outputFile,
                                 Path sourceDir) {
        try {
            Path path = Files.createTempFile(null, ".sh");
            File file = path.toFile();
            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file));
            fileWriter.write("cd '" + sourceDir + "'");
            fileWriter.newLine();
            fileWriter.write("export ATTINI_ENVIRONMENT_NAME='" + jobData.environment()+"'");
            fileWriter.newLine();
            fileWriter.write("export ATTINI_DISTRIBUTION_NAME='" + jobData.distributionName()+"'");
            fileWriter.newLine();
            fileWriter.write("export ATTINI_DISTRIBUTION_ID='" + jobData.distributionId()+"'");
            fileWriter.newLine();
            fileWriter.write("export ATTINI_ARTIFACT_STORE='" + jobData.sourceBucket()+"'");
            fileWriter.newLine();
            fileWriter.write("export ATTINI_DISTRIBUTION_ARTIFACTS_PREFIX='" + jobData.sourcePrefix()+"'");
            fileWriter.newLine();
            fileWriter.write("export ATTINI_INPUT='" + inputFile+"'");
            fileWriter.newLine();
            fileWriter.write("export ATTINI_OUTPUT='" + outputFile+"'");
            fileWriter.newLine();
            fileWriter.write("export ATTINI_SOURCE_DIR='" + sourceDir + "'");
            fileWriter.newLine();
            fileWriter.write("export ATTINI_STEP_NAME='" + jobData.stepName() + "'");
            fileWriter.newLine();
            fileWriter.write("export ATTINI_OBJECT_IDENTIFIER='" + jobData.objectIdentifier()+"'");
            fileWriter.newLine();
            fileWriter.write("set -eo pipefail");
            fileWriter.newLine();
            jobData.environmentVariables()
                   .forEach((key, value) -> {
                       try {
                           fileWriter.write("export " + key + "='" + value+"'");
                           fileWriter.newLine();
                       } catch (IOException e) {
                           throw new UncheckedIOException(e);
                       }
                   });
            jobData.commands().forEach(addCommandToFile(fileWriter));
            fileWriter.close();
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path createStartupFile(List<String> commands,
                                  String environment,
                                  String distributionName) {
        try {
            Path path = Files.createTempFile(null, ".sh");
            File file = path.toFile();
            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file));
            fileWriter.write("export ATTINI_ENVIRONMENT_NAME=" + environment);
            fileWriter.newLine();
            fileWriter.write("export ATTINI_DISTRIBUTION_NAME=" + distributionName);
            fileWriter.newLine();
            fileWriter.write("set -eo pipefail");
            fileWriter.newLine();
            commands.forEach(addCommandToFile(fileWriter));
            fileWriter.close();
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    private Consumer<String> addCommandToFile(BufferedWriter fileWriter) {
        return command -> {
            logger.debug("adding command: " + command);
            try {
                fileWriter.write(command);
                fileWriter.newLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
