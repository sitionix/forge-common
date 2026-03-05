package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxAggregateType;
import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;
import com.sitionix.forge.outbox.core.port.ForgeOutboxPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRecordFactoryTest {

    private OutboxRecordFactory outboxRecordFactory;

    @BeforeEach
    void setUp() {
        this.outboxRecordFactory = new OutboxRecordFactory(
                Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void givenPayloadWithOutboxData_whenCreate_thenBuildPendingRecord() {
        //given
        final TestPayload payload = new TestPayload(
                "trace-1",
                "SITE",
                10L,
                Instant.parse("2026-01-01T10:01:00Z"),
                Map.of("header-1", "value-1"),
                Map.of("meta-1", "value-1"),
                "SYSTEM",
                "1");

        //when
        final OutboxRecord actual = this.outboxRecordFactory.create(payload, "EMAIL_VERIFY");

        //then
        assertThat(actual.getEventType()).isEqualTo("EMAIL_VERIFY");
        assertThat(actual.getPayload()).isNull();
        assertThat(actual.getHeaders()).isEqualTo(Map.of("header-1", "value-1"));
        assertThat(actual.getMetadata()).isEqualTo(Map.of("meta-1", "value-1"));
        assertThat(actual.getTraceId()).isEqualTo("trace-1");
        assertThat(actual.getAggregateType()).isEqualTo("SITE");
        assertThat(actual.getAggregateId()).isEqualTo(10L);
        assertThat(actual.getInitiatorType()).isEqualTo("SYSTEM");
        assertThat(actual.getInitiatorId()).isEqualTo("1");
        assertThat(actual.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(actual.getAttempts()).isEqualTo(0);
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:01:00Z"));
        assertThat(actual.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(actual.getUpdatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void givenPayloadWithoutOptionalData_whenCreate_thenApplyDefaults() {
        //given
        final TestPayload payload = new TestPayload(
                null,
                "   ",
                null,
                null,
                null,
                null,
                "SYSTEM",
                "1");

        //when
        final OutboxRecord actual = this.outboxRecordFactory.create(payload, "EMAIL_VERIFY");

        //then
        assertThat(actual.getAggregateType()).isNull();
        assertThat(actual.getAggregateId()).isNull();
        assertThat(actual.getInitiatorType()).isEqualTo("SYSTEM");
        assertThat(actual.getInitiatorId()).isEqualTo("1");
        assertThat(actual.getHeaders()).isEqualTo(Map.of());
        assertThat(actual.getMetadata()).isEqualTo(Map.of());
        assertThat(actual.getNextAttemptAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void givenLegacyEnumAggregateType_whenCreate_thenBuildRecordWithAggregateTypeValue() {
        //given
        final LegacyPayload payload = new LegacyPayload(100L);

        //when
        final OutboxRecord actual = this.outboxRecordFactory.create(payload, "EMAIL_VERIFY");

        //then
        assertThat(actual.getAggregateType()).isEqualTo("USER");
        assertThat(actual.getAggregateId()).isEqualTo(100L);
    }

    @Test
    void givenOutboxRecord_whenWithPayload_thenReturnUpdatedCopy() {
        //given
        final OutboxRecord source = OutboxRecord.builder()
                .eventType("EMAIL_VERIFY")
                .payload(null)
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();

        //when
        final OutboxRecord actual = this.outboxRecordFactory.withPayload(source, "{\"value\":1}");

        //then
        assertThat(actual.getPayload()).isEqualTo("{\"value\":1}");
        assertThat(source.getPayload()).isNull();
        assertThat(actual.getEventType()).isEqualTo("EMAIL_VERIFY");
    }

    private record TestPayload(String traceId,
                               String aggregateTypeName,
                               Long userId,
                               Instant nextAttemptAt,
                               Map<String, String> headers,
                               Map<String, String> metadata,
                               String initiatorType,
                               String initiatorId) implements ForgeOutboxPayload {

        @Override
        public String eventType() {
            return "EMAIL_VERIFY";
        }

        @Override
        public String traceId() {
            return this.traceId;
        }

        @Override
        public String aggregateTypeValue() {
            return this.aggregateTypeName;
        }

        @Override
        public Long aggregateId() {
            return this.userId;
        }

        @Override
        public String initiatorType() {
            return this.initiatorType;
        }

        @Override
        public String initiatorId() {
            return this.initiatorId;
        }

        @Override
        public Instant nextAttemptAt() {
            return this.nextAttemptAt;
        }

        @Override
        public Map<String, String> headers() {
            return this.headers;
        }

        @Override
        public Map<String, String> metadata() {
            return this.metadata;
        }
    }

    private record LegacyPayload(Long userId) implements ForgeOutboxPayload {

        @Override
        public String eventType() {
            return "EMAIL_VERIFY";
        }

        @Override
        public OutboxAggregateType aggregateType() {
            return OutboxAggregateType.USER;
        }

        @Override
        public Long aggregateId() {
            return this.userId;
        }
    }
}
