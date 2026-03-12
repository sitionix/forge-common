package com.sitionix.forge.inbox.postgres.storage;

import com.sitionix.forge.inbox.core.model.InboxStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PostgresInboxStorageTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        verifyNoInteractions(this.jdbcTemplate);
    }

    @Test
    void givenBatchSizeLessThanOne_whenClaimPendingEvents_thenReturnEmptyWithoutStorageCalls() {
        //given
        final PostgresInboxStorage storage = new PostgresInboxStorage(this.jdbcTemplate);

        //when
        final var actual = storage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                null,
                0,
                Instant.parse("2026-01-01T10:00:00Z"),
                false,
                null);

        //then
        assertThat(actual).isEmpty();
    }

    @Test
    void givenLockEnabledAndNullLockLease_whenClaimPendingEvents_thenThrowException() {
        //given
        final PostgresInboxStorage storage = new PostgresInboxStorage(this.jdbcTemplate);

        //when
        //then
        assertThatThrownBy(() -> storage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                null,
                1,
                Instant.parse("2026-01-01T10:00:00Z"),
                true,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("lockLease is required when lockEnabled");
    }

    @Test
    void givenNullEventStatuses_whenClaimPendingEvents_thenThrowException() {
        //given
        final PostgresInboxStorage storage = new PostgresInboxStorage(this.jdbcTemplate);

        //when
        //then
        assertThatThrownBy(() -> storage.claimPendingEvents(
                null,
                null,
                1,
                Instant.parse("2026-01-01T10:00:00Z"),
                false,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("eventStatuses is required");
    }

    @Test
    void givenLockEnabledAndZeroLockLease_whenClaimPendingEvents_thenThrowException() {
        //given
        final PostgresInboxStorage storage = new PostgresInboxStorage(this.jdbcTemplate);

        //when
        //then
        assertThatThrownBy(() -> storage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                null,
                1,
                Instant.parse("2026-01-01T10:00:00Z"),
                true,
                Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lockLease must be greater than 0 when lockEnabled");
    }

    @Test
    void givenLockEnabledAndNegativeLockLease_whenClaimPendingEvents_thenThrowException() {
        //given
        final PostgresInboxStorage storage = new PostgresInboxStorage(this.jdbcTemplate);

        //when
        //then
        assertThatThrownBy(() -> storage.claimPendingEvents(
                EnumSet.of(InboxStatus.PENDING),
                null,
                1,
                Instant.parse("2026-01-01T10:00:00Z"),
                true,
                Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lockLease must be greater than 0 when lockEnabled");
    }
}
