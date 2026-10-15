package com.ezaigc.hr.common.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("hr.executors")
@Data
@NoArgsConstructor
public class PlatformExecutorProperties {

    @Valid
    @NotNull
    private Pool ai = new Pool(13, 13, 32);
    @Valid
    @NotNull
    private AiWorkloadSlots aiWorkloadSlots = new AiWorkloadSlots(2, 1, 10);
    @Valid
    @NotNull
    private Pool matching = new Pool(1, 2, 16);
    @Valid
    @NotNull
    private Pool search = new Pool(2, 2, 8);
    @Valid
    @NotNull
    private Pool maintenance = new Pool(1, 1, 8);
    @Valid
    @NotNull
    private Pool reporting = new Pool(1, 1, 2);
    @Valid
    @NotNull
    private Pool directory = new Pool(1, 1, 1);
    @Valid
    @NotNull
    private Pool mail = new Pool(1, 1, 1);
    @Valid
    @NotNull
    private Pool outbox = new Pool(2, 2, 4);
    @Valid
    @NotNull
    private Pool brokerIo = new Pool(1, 1, 4);
    @Valid
    @NotNull
    private Pool brokerCallbacks = new Pool(2, 2, 32);
    @Valid
    @NotNull
    private Pool brokerConsumers = new Pool(1, 1, 4);
    @Min(1)
    private int schedulerSize = 4;
    @NotNull
    private Duration shutdownAwait = Duration.ofSeconds(30);

    @AssertTrue(message = "AI workload slots must exactly match the fixed AI core pool size")
    public boolean hasCompleteAiWorkloadAllocation() {
        if (ai == null || aiWorkloadSlots == null) return false;
        long allocated = (long) aiWorkloadSlots.getResumeParse()
                + aiWorkloadSlots.getIntakeParse()
                + aiWorkloadSlots.getJdParse();
        return ai.getCoreSize() == ai.getMaxSize() && allocated == ai.getCoreSize();
    }

    @AssertTrue(message = "AI executor queue must hold one complete fixed-pool workload burst")
    public boolean hasSufficientAiBurstQueue() {
        return ai != null && ai.getQueueCapacity() >= ai.getCoreSize();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiWorkloadSlots {

        @Min(1)
        private int resumeParse;
        @Min(1)
        private int intakeParse;
        @Min(1)
        private int jdParse;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pool {

        @Min(1)
        private int coreSize;
        @Min(1)
        private int maxSize;
        @Min(0)
        private int queueCapacity;

    }
}
