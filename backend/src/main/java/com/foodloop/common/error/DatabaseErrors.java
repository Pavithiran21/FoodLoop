package com.foodloop.common.error;

import java.sql.SQLException;

/** Helpers for reading MySQL errors that reach us wrapped in Spring exceptions. */
public final class DatabaseErrors {

    /** MySQL ER_DUP_ENTRY: a UNIQUE index refused the row. */
    private static final int DUPLICATE_ENTRY = 1062;
    /** MySQL ER_LOCK_DEADLOCK: InnoDB found a deadlock and rolled this transaction back. */
    private static final int DEADLOCK = 1213;

    private DatabaseErrors() {
    }

    public static boolean isDuplicateKey(Throwable error) {
        return hasErrorCode(error, DUPLICATE_ENTRY);
    }

    public static boolean isDeadlock(Throwable error) {
        return hasErrorCode(error, DEADLOCK);
    }

    private static boolean hasErrorCode(Throwable error, int code) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == code) {
                return true;
            }
        }
        return false;
    }
}
