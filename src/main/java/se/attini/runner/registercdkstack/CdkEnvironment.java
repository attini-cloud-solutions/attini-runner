package se.attini.runner.registercdkstack;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CdkEnvironment(String region, String account) {

}
