package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import com.sitionix.forge.inbox.core.port.InboxReceiveMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InboxRecordFactoryTest {

    private InboxRecordFactory inboxRecordFactory;

    @BeforeEach
    void setUp() {
        this.inboxRecordFactory = new InboxRecordFactory(
                Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void givenMetadataWithInboxData_whenCreate_thenBuildPendingRecord() {
        //given
        final InboxReceiveMetadata metadata = new InboxReceiveMetadata(
                "EMAIL_VERIFY",
                "idemp-1",
                "trace-meta-1",
                Map.of("header-1", "value-1"),
                Map.of("meta-1", "value-1"),
                "SITE",
                10L,
                "SYSTEM",
                "1",
                Instant.parse("2026-01-01T10:01:00Z"));

        //when
        final InboxRecord actual = this.inboxRecordFactory.create(metadata, "{\"value\":1}");

        //then
        assertThat(actual.getEventType()).isEqualTo("EMAIL_VERIFY");
        assertThat(actual.getPayload()).isEqualTo("{\"value\":1}");
        assertThat(actual.getHeaders()).isEqualTo(Map.of("header-1", "value-1"));
        assertThat(actual.getMetadata()).isEqualTo(Map.of("meta-1", "value-1"));
        assertThat(actual.getTraceId()).isEqualTo("trace-meta-1");
        assertThat(actual.getIdempotencyKey()).isEqualTo("idemp-1");
        assertThat(actual.getAggregateType()).isEqualTo("SITE");
        assertThat(actual.getAggregateId()).isEqualTo(10L);
        assertThat(actual.getInitiatorType()).isEqualTo("SYSTEM");
        assertThat(actual.getInitiatorId()).isEqualTo("1");
        assertThat(actual.getStatus()).isEqualTo(InboxStatus.PENDING);
        assertThat(actual.getAttempts()).isEqualTo(0);
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:01:00Z"));
        assertThat(actual.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(actual.getUpdatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void givenMetadataWithoutOptionalData_whenCreate_thenApplyDefaults() {
        //given
        final InboxReceiveMetadata metadata = new InboxReceiveMetadata(
                "EMAIL_VERIFY",
                null,
                null,
                null,
                null,
                "   ",
                null,
                "SYSTEM",
                "1",
                null);

        //when
        final InboxRecord actual = this.inboxRecordFactory.create(metadata, "{\"value\":2}");

        //then
        assertThat(actual.getAggregateType()).isNull();
        assertThat(actual.getAggregateId()).isNull();
        assertThat(actual.getInitiatorType()).isEqualTo("SYSTEM");
        assertThat(actual.getInitiatorId()).isEqualTo("1");
        assertThat(actual.getHeaders()).isEqualTo(Map.of());
        assertThat(actual.getMetadata()).isEqualTo(Map.of());
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }
}
