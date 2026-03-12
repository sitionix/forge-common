package com.sitionix.forge.inbox.core.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InboxDispatchSummary {

    private final int claimed;
    private final int processed;
    private final int failed;

    public static InboxDispatchSummary empty() {
        return InboxDispatchSummary.builder()
                .claimed(0)
                .processed(0)
                .failed(0)
                .build();
    }
}
