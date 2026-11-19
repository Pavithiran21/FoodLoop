package com.foodloop.report;

import java.util.Set;

/** The small enums of a report, kept together. */
public final class ReportEnums {

    private ReportEnums() {
    }

    /** LISTING: a receiver reports a listing (and so its donor). RECEIVER: a donor reports the receiver of a claim. */
    public enum Kind {
        LISTING,
        RECEIVER
    }

    /** The reasons offered on each report dialog (UI-UX.md screens 8 and 17); OTHER is on both. */
    public enum Reason {
        SPOILED_OR_UNSAFE(Kind.LISTING),
        NOT_AS_LISTED(Kind.LISTING),
        DONOR_NOT_THERE(Kind.LISTING),
        NO_SHOW(Kind.RECEIVER),
        RUDE_OR_UNSAFE(Kind.RECEIVER),
        OTHER(Kind.LISTING, Kind.RECEIVER);

        private final Set<Kind> kinds;

        Reason(Kind... kinds) {
            this.kinds = Set.of(kinds);
        }

        public boolean isFor(Kind kind) {
            return kinds.contains(kind);
        }
    }

    /** OPEN until an Admin dismisses it (no action) or suspends the account (ACTIONED). */
    public enum Status {
        OPEN,
        DISMISSED,
        ACTIONED
    }
}
