package se.attini.runner.commandmode.registercdkstack;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CdkStack(String name, String id, CdkEnvironment environment) {
}
