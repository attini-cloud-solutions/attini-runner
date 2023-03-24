package se.attini.runner;

import java.util.List;
import java.util.Map;

public class TestJobData {
    public static JobData createJobData() {
        return new JobData(List.of(""),
                           "",
                           "",
                           "",
                           "",
                           "",
                           "",
                           123232,
                           "test-step",
                           "",
                           "",
                           "",
                           "", Map.of());
    }
}
