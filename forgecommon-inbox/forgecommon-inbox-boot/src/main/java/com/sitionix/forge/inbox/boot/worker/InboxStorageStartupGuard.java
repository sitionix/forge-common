package com.sitionix.forge.inbox.boot.worker;

import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

final class InboxStorageStartupGuard {

    private static final String UNDEFINED_TABLE_SQL_STATE = "42P01";
    private static final String MYSQL_TABLE_NOT_FOUND = "42S02";
    private static final String SQLITE_TABLE_NOT_FOUND = "no such table";

    private InboxStorageStartupGuard() {
    }

    static boolean isInboxSchemaMissing(final DataAccessException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException && isTableMissingSqlState(sqlException.getSQLState())) {
                return true;
            }
            final String message = current.getMessage();
            if (message != null) {
                final String normalized = message.toLowerCase();
                final boolean mentionsInboxTable = normalized.contains("forge_inbox_events");
                final boolean missingRelation = normalized.contains("does not exist")
                        || normalized.contains("unknown table")
                        || normalized.contains(SQLITE_TABLE_NOT_FOUND);
                if (mentionsInboxTable && missingRelation) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isTableMissingSqlState(final String sqlState) {
        if (sqlState == null) {
            return false;
        }
        return UNDEFINED_TABLE_SQL_STATE.equals(sqlState) || MYSQL_TABLE_NOT_FOUND.equals(sqlState);
    }
}
