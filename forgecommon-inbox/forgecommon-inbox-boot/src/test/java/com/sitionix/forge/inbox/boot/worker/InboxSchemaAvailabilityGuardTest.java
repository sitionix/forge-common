package com.sitionix.forge.inbox.boot.worker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class InboxSchemaAvailabilityGuardTest {

    @Mock
    private Logger logger;

    private InboxSchemaAvailabilityGuard guard;

    @BeforeEach
    void setUp() {
        this.guard = new InboxSchemaAvailabilityGuard(
                this.logger,
                "worker",
                Duration.ofSeconds(1),
                Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(this.logger);
    }

    @Test
    void givenSchemaMissingWithinTimeout_whenOnFailure_thenSkipAndLogInfo() {
        //given
        final DataAccessResourceFailureException exception = new DataAccessResourceFailureException(
                "relation \"forge_inbox_events\" does not exist");

        //when
        //then
        assertThatCode(() -> this.guard.onFailure(exception, Instant.parse("2026-01-01T10:00:00Z")))
                .doesNotThrowAnyException();
        verify(this.logger).info("ForgeInbox {} skipped: storage schema is not ready yet", "worker");
    }

    @Test
    void givenSchemaMissingLongerThanTimeout_whenOnFailure_thenThrowIllegalStateException() {
        //given
        final DataAccessResourceFailureException exception = new DataAccessResourceFailureException(
                "relation \"forge_inbox_events\" does not exist");
        this.guard.onFailure(exception, Instant.parse("2026-01-01T10:00:00Z"));
        verify(this.logger).info("ForgeInbox {} skipped: storage schema is not ready yet", "worker");

        //when
        //then
        assertThatThrownBy(() -> this.guard.onFailure(exception, Instant.parse("2026-01-01T10:00:02Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema was not ready within PT1S");
    }

    @Test
    void givenSchemaMissingAfterRecovery_whenOnFailure_thenRestartTimeoutWindow() {
        //given
        final DataAccessResourceFailureException exception = new DataAccessResourceFailureException(
                "relation \"forge_inbox_events\" does not exist");
        this.guard.onFailure(exception, Instant.parse("2026-01-01T10:00:00Z"));
        this.guard.onSuccess();

        //when
        //then
        assertThatCode(() -> this.guard.onFailure(exception, Instant.parse("2026-01-01T10:00:02Z")))
                .doesNotThrowAnyException();
        verify(this.logger, times(2)).info("ForgeInbox {} skipped: storage schema is not ready yet", "worker");
    }

    @Test
    void givenNonSchemaException_whenOnFailure_thenRethrowOriginalException() {
        //given
        final DataAccessResourceFailureException exception = new DataAccessResourceFailureException("connection refused");

        //when
        //then
        assertThatThrownBy(() -> this.guard.onFailure(exception, Instant.parse("2026-01-01T10:00:00Z")))
                .isSameAs(exception);
    }
}
