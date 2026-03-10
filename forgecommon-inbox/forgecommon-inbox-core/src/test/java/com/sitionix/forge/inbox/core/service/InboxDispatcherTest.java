package com.sitionix.forge.inbox.core.service;

import com.sitionix.forge.inbox.core.model.InboxDispatchSummary;
import com.sitionix.forge.inbox.core.model.InboxRecord;
import com.sitionix.forge.inbox.core.model.InboxStatus;
import com.sitionix.forge.inbox.core.model.InboxWorkerPolicy;
import com.sitionix.forge.inbox.core.port.InboxHandler;
import com.sitionix.forge.inbox.core.port.InboxStorage;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxDispatcherTest {

    @Mock
    private InboxStorage inboxStorage;

    @Mock
    private InboxHandler inboxPublisher;

    private InboxDispatcher inboxDispatcher;

    @BeforeEach
    void setUp() {
        final InboxWorkerPolicy inboxWorkerPolicy = InboxWorkerPolicy.builder()
                .batchSize(5)
                .retryDelay(Duration.ofSeconds(60))
                .maxRetries(3)
                .lockEnabled(true)
                .lockLease(Duration.ofSeconds(30))
                .build();
        final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        this.inboxDispatcher = new InboxDispatcher(this.inboxStorage, this.inboxPublisher, inboxWorkerPolicy, fixedClock);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(this.inboxStorage, this.inboxPublisher);
    }

    @Test
    void givenNoEvents_whenDispatchPendingEvents_thenReturnEmptySummary() {
        //given
        when(this.inboxPublisher.supportedEventTypes())
                .thenReturn(Set.of("EMAIL_VERIFY"));
        when(this.inboxStorage.claimPendingEvents(eq(EnumSet.of(InboxStatus.PENDING, InboxStatus.FAILED)),
                eq(Set.of("EMAIL_VERIFY")),
                eq(5),
                eq(Instant.parse("2026-01-01T10:00:00Z")),
                eq(true),
                eq(Duration.ofSeconds(30))))
                .thenReturn(List.of());

        //when
        final InboxDispatchSummary actual = this.inboxDispatcher.dispatchPendingEvents();

        //then
        assertThat(actual.getClaimed()).isEqualTo(0);
        assertThat(actual.getProcessed()).isEqualTo(0);
        assertThat(actual.getFailed()).isEqualTo(0);
        verify(this.inboxPublisher).supportedEventTypes();
        verify(this.inboxStorage).claimPendingEvents(eq(EnumSet.of(InboxStatus.PENDING, InboxStatus.FAILED)),
                eq(Set.of("EMAIL_VERIFY")),
                eq(5),
                eq(Instant.parse("2026-01-01T10:00:00Z")),
                eq(true),
                eq(Duration.ofSeconds(30)));
    }

    @Test
    void givenNoSupportedEventTypes_whenDispatchPendingEvents_thenReturnEmptySummaryWithoutStorageCall() {
        //given
        when(this.inboxPublisher.supportedEventTypes()).thenReturn(Set.of());

        //when
        final InboxDispatchSummary actual = this.inboxDispatcher.dispatchPendingEvents();

        //then
        assertThat(actual.getClaimed()).isEqualTo(0);
        assertThat(actual.getProcessed()).isEqualTo(0);
        assertThat(actual.getFailed()).isEqualTo(0);
        verify(this.inboxPublisher).supportedEventTypes();
    }

    @Test
    void givenPublishSuccess_whenDispatchPendingEvents_thenMarkSent() throws Exception {
        //given
        final InboxRecord inboxRecord = InboxRecord.builder()
                .id("10")
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .updatedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
        when(this.inboxPublisher.supportedEventTypes())
                .thenReturn(Set.of("EMAIL_VERIFY"));
        when(this.inboxStorage.claimPendingEvents(any(), any(), any(Integer.class), any(), any(Boolean.class), any()))
                .thenReturn(List.of(inboxRecord));

        //when
        final InboxDispatchSummary actual = this.inboxDispatcher.dispatchPendingEvents();

        //then
        assertThat(actual.getClaimed()).isEqualTo(1);
        assertThat(actual.getProcessed()).isEqualTo(1);
        assertThat(actual.getFailed()).isEqualTo(0);
        verify(this.inboxPublisher).supportedEventTypes();
        verify(this.inboxStorage).claimPendingEvents(any(), any(), any(Integer.class), any(), any(Boolean.class), any());
        verify(this.inboxPublisher).handle(inboxRecord);
        verify(this.inboxStorage).markProcessed("10",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void givenPublishFailure_whenDispatchPendingEvents_thenMarkFailedWithTrimmedMessage() throws Exception {
        //given
        final String longMessage = "X".repeat(1200);
        final InboxRecord inboxRecord = InboxRecord.builder()
                .id("11")
                .eventType("EMAIL_VERIFY")
                .payload("{}")
                .updatedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
        when(this.inboxPublisher.supportedEventTypes())
                .thenReturn(Set.of("EMAIL_VERIFY"));
        when(this.inboxStorage.claimPendingEvents(any(), any(), any(Integer.class), any(), any(Boolean.class), any()))
                .thenReturn(List.of(inboxRecord));
        doThrow(new RuntimeException(longMessage))
                .when(this.inboxPublisher)
                .handle(inboxRecord);
        final ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);

        //when
        final InboxDispatchSummary actual = this.inboxDispatcher.dispatchPendingEvents();

        //then
        assertThat(actual.getClaimed()).isEqualTo(1);
        assertThat(actual.getProcessed()).isEqualTo(0);
        assertThat(actual.getFailed()).isEqualTo(1);
        verify(this.inboxPublisher).supportedEventTypes();
        verify(this.inboxStorage).claimPendingEvents(any(), any(), any(Integer.class), any(), any(Boolean.class), any());
        verify(this.inboxPublisher).handle(inboxRecord);
        verify(this.inboxStorage).markFailed(eq("11"),
                errorCaptor.capture(),
                eq(Duration.ofSeconds(60)),
                eq(3),
                eq(Instant.parse("2026-01-01T10:00:00Z")),
                eq(Instant.parse("2026-01-01T10:00:00Z")));
        assertThat(errorCaptor.getValue().length()).isEqualTo(1000);
    }

    @Test
    void givenInvalidPolicy_whenCreateDispatcher_thenThrowException() {
        //given
        final InboxWorkerPolicy invalidPolicy = InboxWorkerPolicy.builder()
                .batchSize(0)
                .retryDelay(Duration.ofSeconds(60))
                .maxRetries(3)
                .lockEnabled(true)
                .lockLease(Duration.ofSeconds(30))
                .build();
        final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

        //when
        //then
        assertThatThrownBy(() -> new InboxDispatcher(this.inboxStorage, this.inboxPublisher, invalidPolicy, fixedClock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("policy.batchSize must be greater than 0");
    }
}
