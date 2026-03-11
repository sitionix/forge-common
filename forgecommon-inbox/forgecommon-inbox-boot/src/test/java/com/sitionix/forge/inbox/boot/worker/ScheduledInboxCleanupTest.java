package com.sitionix.forge.inbox.boot.worker;

import com.sitionix.forge.inbox.boot.config.ForgeInboxProperties;
import com.sitionix.forge.inbox.core.port.InboxStorage;
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
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledInboxCleanupTest {

    private ScheduledInboxCleanup scheduledInboxCleanup;

    @Mock
    private InboxStorage inboxStorage;

    @Mock
    private Clock forgeInboxClock;

    @BeforeEach
    void setUp() {
        final ForgeInboxProperties properties = new ForgeInboxProperties();
        properties.setEnabled(true);
        properties.getCleanup().setEnabled(true);
        properties.getCleanup().setRetention(Duration.ofDays(14));
        this.scheduledInboxCleanup = new ScheduledInboxCleanup(properties, this.inboxStorage, this.forgeInboxClock);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(this.inboxStorage, this.forgeInboxClock);
    }

    @Test
    void givenValidRetention_whenCleanupSentEvents_thenDeleteByCalculatedCutoff() {
        //given
        final Instant now = Instant.parse("2026-02-10T10:00:00Z");
        final Instant expectedCutoff = Instant.parse("2026-01-27T10:00:00Z");
        when(this.forgeInboxClock.instant()).thenReturn(now);
        when(this.inboxStorage.deleteProcessedBefore(expectedCutoff)).thenReturn(3);

        //when
        final int actual = this.scheduledInboxCleanup.cleanupProcessedEvents();

        //then
        assertThat(actual).isEqualTo(3);
        verify(this.forgeInboxClock).instant();
        verify(this.inboxStorage).deleteProcessedBefore(expectedCutoff);
    }

    @Test
    void givenZeroRetention_whenCleanupSentEvents_thenSkipDelete() {
        //given
        final ForgeInboxProperties properties = new ForgeInboxProperties();
        properties.setEnabled(true);
        properties.getCleanup().setEnabled(true);
        properties.getCleanup().setRetention(Duration.ZERO);
        this.scheduledInboxCleanup = new ScheduledInboxCleanup(properties,
                this.inboxStorage,
                Clock.fixed(Instant.parse("2026-02-10T10:00:00Z"), ZoneOffset.UTC));

        //when
        final int actual = this.scheduledInboxCleanup.cleanupProcessedEvents();

        //then
        assertThat(actual).isEqualTo(0);
    }

    @Test
    void givenInboxSchemaMissing_whenCleanupSentEvents_thenSkipDeleteWithoutError() {
        //given
        final Instant now = Instant.parse("2026-02-10T10:00:00Z");
        when(this.forgeInboxClock.instant()).thenReturn(now);
        when(this.inboxStorage.deleteProcessedBefore(Instant.parse("2026-01-27T10:00:00Z")))
                .thenThrow(new DataAccessResourceFailureException("relation \"forge_inbox_events\" does not exist"));

        //when
        final int actual = this.scheduledInboxCleanup.cleanupProcessedEvents();

        //then
        assertThat(actual).isEqualTo(0);
        verify(this.forgeInboxClock).instant();
        verify(this.inboxStorage).deleteProcessedBefore(Instant.parse("2026-01-27T10:00:00Z"));
    }

    @Test
    void givenInboxSchemaMissingLongerThanTimeout_whenCleanupSentEvents_thenThrowException() {
        //given
        final ForgeInboxProperties properties = new ForgeInboxProperties();
        properties.setEnabled(true);
        properties.getWorker().setEnabled(true);
        properties.getCleanup().setEnabled(true);
        properties.getCleanup().setRetention(Duration.ofDays(14));
        properties.getStartup().setSchemaReadyTimeout(Duration.ofSeconds(1));
        this.scheduledInboxCleanup = new ScheduledInboxCleanup(properties, this.inboxStorage, this.forgeInboxClock);
        when(this.forgeInboxClock.instant())
                .thenReturn(Instant.parse("2026-02-10T10:00:00Z"))
                .thenReturn(Instant.parse("2026-02-10T10:00:02Z"));
        when(this.inboxStorage.deleteProcessedBefore(any()))
                .thenThrow(new DataAccessResourceFailureException("relation \"forge_inbox_events\" does not exist"));

        //when
        this.scheduledInboxCleanup.cleanupProcessedEvents();

        //then
        assertThatThrownBy(() -> this.scheduledInboxCleanup.cleanupProcessedEvents())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema was not ready within PT1S");
        verify(this.forgeInboxClock, times(2)).instant();
        verify(this.inboxStorage).deleteProcessedBefore(Instant.parse("2026-01-27T10:00:00Z"));
        verify(this.inboxStorage).deleteProcessedBefore(Instant.parse("2026-01-27T10:00:02Z"));
    }
}
