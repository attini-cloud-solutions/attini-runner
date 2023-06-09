package se.attini.runner.commandmode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.QuarkusApplication;
import se.attini.runner.commandmode.registercdkstack.RegisterCdkStacksService;

public class CommandModeApp implements QuarkusApplication {



    public static final String LOG_FILE ="attini-runner-commands.log";
    @Inject
    RegisterCdkStacksService registerCdkStacksService;

    @Inject
    ArtifactStoreFacade artifactStoreFacade;

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @Override
    public int run(String... args) {
        int result = switch (args[1]) {
            case "version" -> {
                System.out.println(version);
                yield 0;
            }
            case "register-cdk-stacks" -> {
                try {
                    registerCdkStacksService.registerStacksDeprecated(args[2]);
                    yield 0;
                } catch (Exception e) {
                    System.err.println("Encountered errored, printing logs to error stream");
                    printLogs();
                    e.printStackTrace();
                    yield 1;
                }
            }
            case "save-cdk-stacks" -> {
                try {
                    registerCdkStacksService.registerStacks(args[2]);
                    yield 0;
                } catch (Exception e) {
                    System.err.println("Encountered errored, printing logs to error stream");
                    printLogs();
                    e.printStackTrace();
                    yield 1;
                }
            }
            case "format-cdk-output" -> {
                try {
                    System.out.println(registerCdkStacksService.formatOutput(args[2]));
                    yield 0;
                } catch (Exception e) {
                    System.err.println("Encountered errored, printing logs to error stream");
                    printLogs();
                    e.printStackTrace();
                    yield 1;
                }
            }
            case "store-artifact" ->{
                try {
                    System.out.println("Storing "+args[3] +" in artifact store.");
                    artifactStoreFacade.store(args[2], args[3]);
                    yield 0;
                } catch (Exception e){
                    System.err.println("Encountered errored, printing logs to error stream");
                    printLogs();
                    e.printStackTrace();
                    yield 1;
                }

            }
           default -> {
               System.err.println("Unknown arguments: " + Arrays.toString(args));
               yield 1;
           }
        };
        deleteLogFile();
        return result;
    }

    private static void deleteLogFile() {
        try {
            Files.deleteIfExists(Path.of(LOG_FILE));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void printLogs() {
        try(Stream<String> lines = Files.lines(Path.of(LOG_FILE))) {
            lines.forEach(System.err::println);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
