package com.sitionix.forge.inbox.boot.worker;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class InboxStorageStartupGuardTest {

    @Test
    void givenPostgresUndefinedTableSqlState_whenIsInboxSchemaMissing_thenReturnTrue() {
        //given
        final DataAccessResourceFailureException exception = new DataAccessResourceFailureException(
                "db error",
                new SQLException("relation does not exist", "42P01"));

        //when
        final boolean actual = InboxStorageStartupGuard.isInboxSchemaMissing(exception);

        //then
        assertThat(actual).isTrue();
    }

    @Test
    void givenMySqlTableNotFoundSqlState_whenIsInboxSchemaMissing_thenReturnTrue() {
        //given
        final DataAccessResourceFailureException exception = new DataAccessResourceFailureException(
                "db error",
                new SQLException("unknown table", "42S02"));

        //when
        final boolean actual = InboxStorageStartupGuard.isInboxSchemaMissing(exception);

        //then
        assertThat(actual).isTrue();
    }

    @Test
    void givenForgeInboxRelationMissingMessage_whenIsInboxSchemaMissing_thenReturnTrue() {
        //given
        final DataAccessResourceFailureException exception = new DataAccessResourceFailureException(
                "relation \"forge_inbox_events\" does not exist");

        //when
        final boolean actual = InboxStorageStartupGuard.isInboxSchemaMissing(exception);

        //then
        assertThat(actual).isTrue();
    }

    @Test
    void givenForgeInboxAggregateTypesRelationMissingMessage_whenIsInboxSchemaMissing_thenReturnTrue() {
        //given
        final DataAccessResourceFailureException exception = new DataAccessResourceFailureException(
                "relation \"forge_inbox_aggregate_types\" does not exist");

        //when
        final boolean actual = InboxStorageStartupGuard.isInboxSchemaMissing(exception);

        //then
        assertThat(actual).isTrue();
    }

    @Test
    void givenMissingTableSqlStateWithoutInboxTableInMessage_whenIsInboxSchemaMissing_thenReturnTrue() {
        //given
        final DataAccessResourceFailureException exception = new DataAccessResourceFailureException(
                "relation \"orders\" does not exist",
                new SQLException("relation does not exist", "42P01"));

        //when
        final boolean actual = InboxStorageStartupGuard.isInboxSchemaMissing(exception);

        //then
        assertThat(actual).isTrue();
    }

    @Test
    void givenUnrelatedErrorWithoutTableSignals_whenIsInboxSchemaMissing_thenReturnFalse() {
        //given
        final DataAccessResourceFailureException exception = new DataAccessResourceFailureException(
                "connection refused");

        //when
        final boolean actual = InboxStorageStartupGuard.isInboxSchemaMissing(exception);

        //then
        assertThat(actual).isFalse();
    }
}
