package se.attini.runner.registercdkstack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.inject.Inject;

import io.quarkus.runtime.QuarkusApplication;

public class RegisterCdkStackApp implements QuarkusApplication {

    @Inject
    RegisterCdkStacksService registerCdkStacksService;

    @Override
    public int run(String... args) {
        if (args.length != 2){
            System.err.println("Expected exactly two arguments, number of arguments: " + args.length);
            return 1;
        }
        try {
            System.out.println(registerCdkStacksService.registerStacks(args[1]));
        } catch (Exception e) {
            System.err.println("Encountered errored, printing logs to error stream");
            printLogs();
            e.printStackTrace();
            return 1;
        }
        return 0;
    }

    private static void printLogs() {
        try(Stream<String> lines = Files.lines(Path.of("attini-runner-commands.log"))) {
            lines.forEach(System.err::println);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
