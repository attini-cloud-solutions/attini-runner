package se.attini.runner;

import java.util.Optional;

public record ContainerMetadata(boolean isTerminating, String taskId, String logUrl) {

    public Optional<String> getLogUrl(){
        return Optional.ofNullable(logUrl);
    }
}
