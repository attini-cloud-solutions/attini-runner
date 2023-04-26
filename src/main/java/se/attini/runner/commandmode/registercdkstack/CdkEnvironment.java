package se.attini.runner.commandmode.registercdkstack;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CdkEnvironment(String region, String account) {

}
