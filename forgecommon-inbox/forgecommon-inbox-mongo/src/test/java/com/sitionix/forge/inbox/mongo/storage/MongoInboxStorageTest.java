package com.sitionix.forge.inbox.mongo.storage;

import com.sitionix.forge.inbox.core.model.InboxStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MongoInboxStorageTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @AfterEach
    void tearDown() {
        verifyNoInteractions(this.mongoTemplate);
    }

    @Test
    void givenBatchSizeLessThanOne_whenClaimPendingEvents_thenReturnEmptyWithoutStorageCalls() {
        //given
        final MongoInboxStorage storage = new MongoInboxStorage(this.mongoTemplate);

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
        final MongoInboxStorage storage = new MongoInboxStorage(this.mongoTemplate);

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
        final MongoInboxStorage storage = new MongoInboxStorage(this.mongoTemplate);

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
        final MongoInboxStorage storage = new MongoInboxStorage(this.mongoTemplate);

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
        final MongoInboxStorage storage = new MongoInboxStorage(this.mongoTemplate);

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
