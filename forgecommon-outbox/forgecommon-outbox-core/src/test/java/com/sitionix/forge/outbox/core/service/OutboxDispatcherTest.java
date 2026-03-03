package com.sitionix.forge.outbox.core.service;

import com.sitionix.forge.outbox.core.model.OutboxDispatchSummary;
import com.sitionix.forge.outbox.core.model.OutboxRecord;
import com.sitionix.forge.outbox.core.model.OutboxStatus;
import com.sitionix.forge.outbox.core.model.OutboxWorkerPolicy;
import com.sitionix.forge.outbox.core.port.OutboxPublisher;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    @Mock
    private OutboxStorage outboxStorage;

    @Mock
    private OutboxPublisher outboxPublisher;

    private OutboxDispatcher outboxDispatcher;

    @BeforeEach
    void setUp() {
        final OutboxWorkerPolicy outboxWorkerPolicy = OutboxWorkerPolicy.builder()
                .batchSize(5)
                .retryDelay(Duration.ofSeconds(60))
                .maxRetries(3)
                .lockEnabled(true)
                .lockLease(Duration.ofSeconds(30))
                .build();
        final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        this.outboxDispatcher = new OutboxDispatcher(this.outboxStorage, this.outboxPublisher, outboxWorkerPolicy, fixedClock);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(this.outboxStorage, this.outboxPublisher);
    }

    @Test
    void givenNoEvents_whenDispatchPendingEvents_thenReturnEmptySummary() {
        //given
        when(this.outboxPublisher.supportedEventTypes())
                .thenReturn(Set.of("EMAIL_VERIFY"));
        when(this.outboxStorage.claimPendingEvents(eq(EnumSet.of(OutboxStatus.PENDING, OutboxStatus.FAILED)),
                eq(Set.of("EMAIL_VERIFY")),
                eq(5),
                eq(Instant.parse("2026-01-01T10:00:00Z")),
                eq(true),
                eq(Duration.ofSeconds(30))))
                .thenReturn(List.of());

        //when
        final OutboxDispatchSummary actual = this.outboxDispatcher.dispatchPendingEvents();

        //then
        assertThat(actual.getClaimed()).isEqualTo(0);
        assertThat(actual.getSent()).isEqualTo(0);
        assertThat(actual.getFailed()).isEqualTo(0);
        verify(this.outboxPublisher).supportedEventTypes();
        verify(this.outboxStorage).claimPendingEvents(eq(EnumSet.of(OutboxStatus.PENDING, OutboxStatus.FAILED)),
                eq(Set.of("EMAIL_VERIFY")),
                eq(5),
                eq(Instant.parse("2026-01-01T10:00:00Z")),
                eq(true),
                eq(Duration.ofSeconds(30)));
    }

    @Test
    void givenPublishSuccess_whenDispatchPendingEvents_thenMarkSent() throws Exception {
        //given
        final OutboxRecord outboxRecord = OutboxRecord.builder()
                .id("10")
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .build();
        when(this.outboxPublisher.supportedEventTypes())
                .thenReturn(Set.of("EMAIL_VERIFY"));
        when(this.outboxStorage.claimPendingEvents(any(), any(), any(Integer.class), any(), any(Boolean.class), any()))
                .thenReturn(List.of(outboxRecord));

        //when
        final OutboxDispatchSummary actual = this.outboxDispatcher.dispatchPendingEvents();

        //then
        assertThat(actual.getClaimed()).isEqualTo(1);
        assertThat(actual.getSent()).isEqualTo(1);
        assertThat(actual.getFailed()).isEqualTo(0);
        verify(this.outboxPublisher).supportedEventTypes();
        verify(this.outboxStorage).claimPendingEvents(any(), any(), any(Integer.class), any(), any(Boolean.class), any());
        verify(this.outboxPublisher).publish(outboxRecord);
        verify(this.outboxStorage).markSent("10");
    }

    @Test
    void givenPublishFailure_whenDispatchPendingEvents_thenMarkFailedWithTrimmedMessage() throws Exception {
        //given
        final String longMessage = "X".repeat(1200);
        final OutboxRecord outboxRecord = OutboxRecord.builder()
                .id("11")
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .build();
        when(this.outboxPublisher.supportedEventTypes())
                .thenReturn(Set.of("EMAIL_VERIFY"));
        when(this.outboxStorage.claimPendingEvents(any(), any(), any(Integer.class), any(), any(Boolean.class), any()))
                .thenReturn(List.of(outboxRecord));
        doThrow(new RuntimeException(longMessage))
                .when(this.outboxPublisher)
                .publish(outboxRecord);
        final ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);

        //when
        final OutboxDispatchSummary actual = this.outboxDispatcher.dispatchPendingEvents();

        //then
        assertThat(actual.getClaimed()).isEqualTo(1);
        assertThat(actual.getSent()).isEqualTo(0);
        assertThat(actual.getFailed()).isEqualTo(1);
        verify(this.outboxPublisher).supportedEventTypes();
        verify(this.outboxStorage).claimPendingEvents(any(), any(), any(Integer.class), any(), any(Boolean.class), any());
        verify(this.outboxPublisher).publish(outboxRecord);
        verify(this.outboxStorage).markFailed(eq("11"),
                errorCaptor.capture(),
                eq(Duration.ofSeconds(60)),
                eq(3),
                eq(Instant.parse("2026-01-01T10:00:00Z")));
        assertThat(errorCaptor.getValue().length()).isEqualTo(1000);
    }
}
