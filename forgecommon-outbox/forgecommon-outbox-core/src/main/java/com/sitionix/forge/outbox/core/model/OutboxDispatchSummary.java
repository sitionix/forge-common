package com.sitionix.forge.outbox.core.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OutboxDispatchSummary {

    private final int claimed;
    private final int sent;
    private final int failed;

    public static OutboxDispatchSummary empty() {
        return OutboxDispatchSummary.builder()
                .claimed(0)
                .sent(0)
                .failed(0)
                .build();
    }
}
