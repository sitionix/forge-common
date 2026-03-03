package com.sitionix.forge.outbox.boot.worker;

import com.sitionix.forge.outbox.boot.config.ForgeOutboxProperties;
import com.sitionix.forge.outbox.core.port.OutboxStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledOutboxCleanupTest {

    private ScheduledOutboxCleanup scheduledOutboxCleanup;

    @Mock
    private OutboxStorage outboxStorage;

    @Mock
    private Clock forgeOutboxClock;

    @BeforeEach
    void setUp() {
        final ForgeOutboxProperties properties = new ForgeOutboxProperties();
        properties.setEnabled(true);
        properties.getCleanup().setEnabled(true);
        properties.getCleanup().setRetention(Duration.ofDays(14));
        this.scheduledOutboxCleanup = new ScheduledOutboxCleanup(properties, this.outboxStorage, this.forgeOutboxClock);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(this.outboxStorage, this.forgeOutboxClock);
    }

    @Test
    void givenValidRetention_whenCleanupSentEvents_thenDeleteByCalculatedCutoff() {
        //given
        final Instant now = Instant.parse("2026-02-10T10:00:00Z");
        final Instant expectedCutoff = Instant.parse("2026-01-27T10:00:00Z");
        when(this.forgeOutboxClock.instant()).thenReturn(now);
        when(this.outboxStorage.deleteSentBefore(expectedCutoff)).thenReturn(3);

        //when
        final int actual = this.scheduledOutboxCleanup.cleanupSentEvents();

        //then
        assertThat(actual).isEqualTo(3);
        verify(this.forgeOutboxClock).instant();
        verify(this.outboxStorage).deleteSentBefore(expectedCutoff);
    }

    @Test
    void givenZeroRetention_whenCleanupSentEvents_thenSkipDelete() {
        //given
        final ForgeOutboxProperties properties = new ForgeOutboxProperties();
        properties.setEnabled(true);
        properties.getCleanup().setEnabled(true);
        properties.getCleanup().setRetention(Duration.ZERO);
        this.scheduledOutboxCleanup = new ScheduledOutboxCleanup(properties,
                this.outboxStorage,
                Clock.fixed(Instant.parse("2026-02-10T10:00:00Z"), ZoneOffset.UTC));

        //when
        final int actual = this.scheduledOutboxCleanup.cleanupSentEvents();

        //then
        assertThat(actual).isEqualTo(0);
    }
}
