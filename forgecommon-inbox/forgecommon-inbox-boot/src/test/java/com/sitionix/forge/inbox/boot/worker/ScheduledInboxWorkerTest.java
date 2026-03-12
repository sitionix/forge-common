package com.sitionix.forge.inbox.boot.worker;

import com.sitionix.forge.inbox.boot.config.ForgeInboxProperties;
import com.sitionix.forge.inbox.core.model.InboxDispatchSummary;
import com.sitionix.forge.inbox.core.port.ForgeInboxWorker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledInboxWorkerTest {

    @Mock
    private ForgeInboxWorker forgeInboxWorker;

    @Mock
    private Clock forgeInboxClock;

    private ScheduledInboxWorker scheduledInboxWorker;

    @BeforeEach
    void setUp() {
        final ForgeInboxProperties properties = new ForgeInboxProperties();
        properties.setEnabled(true);
        properties.getWorker().setEnabled(true);
        this.scheduledInboxWorker = new ScheduledInboxWorker(properties, this.forgeInboxWorker, this.forgeInboxClock);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(this.forgeInboxWorker, this.forgeInboxClock);
    }

    @Test
    void givenClaimedEvents_whenDispatchPendingEvents_thenDispatchUsingWorker() {
        //given
        when(this.forgeInboxWorker.dispatchPendingEvents()).thenReturn(InboxDispatchSummary.builder()
                .claimed(1)
                .processed(1)
                .failed(0)
                .build());

        //when
        this.scheduledInboxWorker.dispatchPendingEvents();

        //then
        verify(this.forgeInboxWorker).dispatchPendingEvents();
    }

    @Test
    void givenInboxSchemaMissing_whenDispatchPendingEvents_thenSkipWithoutError() {
        //given
        when(this.forgeInboxWorker.dispatchPendingEvents())
                .thenThrow(new DataAccessResourceFailureException("relation \"forge_inbox_events\" does not exist"));
        when(this.forgeInboxClock.instant()).thenReturn(Instant.parse("2026-02-10T10:00:00Z"));

        //when
        this.scheduledInboxWorker.dispatchPendingEvents();

        //then
        verify(this.forgeInboxWorker).dispatchPendingEvents();
        verify(this.forgeInboxClock).instant();
    }

    @Test
    void givenInboxSchemaMissingLongerThanTimeout_whenDispatchPendingEvents_thenThrowException() {
        //given
        final ForgeInboxProperties properties = new ForgeInboxProperties();
        properties.setEnabled(true);
        properties.getWorker().setEnabled(true);
        properties.getStartup().setSchemaReadyTimeout(Duration.ofSeconds(1));
        this.scheduledInboxWorker = new ScheduledInboxWorker(properties, this.forgeInboxWorker, this.forgeInboxClock);
        when(this.forgeInboxWorker.dispatchPendingEvents())
                .thenThrow(new DataAccessResourceFailureException("relation \"forge_inbox_events\" does not exist"));
        when(this.forgeInboxClock.instant())
                .thenReturn(Instant.parse("2026-02-10T10:00:00Z"))
                .thenReturn(Instant.parse("2026-02-10T10:00:02Z"));

        //when
        this.scheduledInboxWorker.dispatchPendingEvents();

        //then
        assertThatThrownBy(() -> this.scheduledInboxWorker.dispatchPendingEvents())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema was not ready within PT1S");
        verify(this.forgeInboxWorker, times(2)).dispatchPendingEvents();
        verify(this.forgeInboxClock, times(2)).instant();
    }
}
