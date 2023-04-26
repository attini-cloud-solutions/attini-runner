package se.attini.runner.commandmode.registercdkstack;


import java.util.List;
import java.util.Map;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record RegisterCdkStacksEvent(String requestType,
                                     String distributionId,
                                     String distributionName,
                                     String environment,
                                     String objectIdentifier,
                                     String stepName,
                                     List<CdkStack> stacks,
                                     Map<String, Map<String, Object>> outputs) {
}
