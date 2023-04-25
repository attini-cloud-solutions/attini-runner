package se.attini.runner.registercdkstack;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CdkStack(String name, String id, CdkEnvironment environment) {
}
