-- FoodLoop schema, version 1.
-- Instants (pickup_from, claimed_at, created_at, ...) are DATETIME(6) in UTC: the server runs at +00:00 and
-- Hibernate writes UTC. A listing keeps the time zone of its pick-up place (time_zone), used only for
-- showing times in screens and emails.
-- There is no money in FoodLoop. Nothing is ever hard-deleted: a person who deletes their account is
-- anonymised ("Former user" / "Former donor"), so the other side's claims and reports stay readable.
-- Distances use plain DOUBLE latitude and longitude and the haversine formula (see BACKEND.md).

-- role: DONOR, RECEIVER or ADMIN (only the seed script creates an Admin).
-- status: ACTIVE can log in; SUSPENDED was suspended by an Admin (reason shown at log-in);
-- DELETED deleted their own account (anonymised).
CREATE TABLE users (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(80)  NOT NULL,
    email            VARCHAR(254) NOT NULL,
    password_hash    VARCHAR(72)  NULL,
    role             VARCHAR(10)  NOT NULL,
    status           VARCHAR(10)  NOT NULL,
    suspended_reason VARCHAR(200) NULL,
    suspended_at     DATETIME(6)  NULL,
    -- A receiver's saved home location (the default search centre); all three are set together or not at all.
    home_label       VARCHAR(250) NULL,
    home_lat         DOUBLE       NULL,
    home_lng         DOUBLE       NULL,
    is_demo          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('DONOR', 'RECEIVER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT chk_users_password CHECK (status = 'DELETED' OR password_hash IS NOT NULL),
    CONSTRAINT chk_users_home CHECK ((home_label IS NULL AND home_lat IS NULL AND home_lng IS NULL)
        OR (home_label IS NOT NULL AND home_lat BETWEEN -90 AND 90 AND home_lng BETWEEN -180 AND 180)),
    INDEX idx_users_role_status (role, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One row per Donor: the business, where food is picked up, and the Admin's verification decision.
-- verification_status: WAITING (new, or asked for review again), VERIFIED, NOT_VERIFIED (with a reason).
CREATE TABLE donor_profiles (
    user_id             BIGINT       NOT NULL,
    business_name       VARCHAR(100) NOT NULL,
    fssai_no            CHAR(14)     NULL,
    pickup_address      VARCHAR(250) NOT NULL,
    -- The part of the address receivers may see before they claim ("Indiranagar").
    pickup_area         VARCHAR(80)  NOT NULL,
    pickup_lat          DOUBLE       NOT NULL,
    pickup_lng          DOUBLE       NOT NULL,
    time_zone           VARCHAR(64)  NOT NULL,
    verification_status VARCHAR(12)  NOT NULL,
    rejection_reason    VARCHAR(200) NULL,
    verified_at         DATETIME(6)  NULL,
    verified_by         BIGINT       NULL,
    review_requested_at DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT chk_donor_status CHECK (verification_status IN ('WAITING', 'VERIFIED', 'NOT_VERIFIED')),
    CONSTRAINT chk_donor_coords CHECK (pickup_lat BETWEEN -90 AND 90 AND pickup_lng BETWEEN -180 AND 180),
    CONSTRAINT fk_donor_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_donor_verified_by FOREIGN KEY (verified_by) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_donor_verification (verification_status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- A listing's status mirrors its claim while one is active:
-- LISTED -> CLAIMED -> READY -> PICKED_UP -> COMPLETED, or EXPIRED / WITHDRAWN. A cancelled claim sends the
-- listing back to LISTED (window still open) or closes it as EXPIRED. The address columns are a snapshot of
-- the donor's pick-up place when the listing was made, so a later change of address affects new listings only.
CREATE TABLE listings (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    donor_id       BIGINT       NOT NULL,
    title          VARCHAR(100) NOT NULL,
    quantity       VARCHAR(60)  NOT NULL,
    category       VARCHAR(12)  NOT NULL,
    pickup_from    DATETIME(6)  NOT NULL,
    pickup_until   DATETIME(6)  NOT NULL,
    best_before    DATETIME(6)  NOT NULL,
    pickup_address VARCHAR(250) NOT NULL,
    pickup_area    VARCHAR(80)  NOT NULL,
    pickup_lat     DOUBLE       NOT NULL,
    pickup_lng     DOUBLE       NOT NULL,
    time_zone      VARCHAR(64)  NOT NULL,
    status         VARCHAR(10)  NOT NULL,
    has_photo      BOOLEAN      NOT NULL DEFAULT FALSE,
    -- When it reached COMPLETED, EXPIRED or WITHDRAWN: the donor board's "Done today" and History use it.
    closed_at      DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_listings_category CHECK (category IN ('BAKERY', 'COOKED_MEAL', 'FRUIT_VEG', 'PACKAGED')),
    CONSTRAINT chk_listings_status CHECK (status IN ('LISTED', 'CLAIMED', 'READY', 'PICKED_UP', 'COMPLETED', 'EXPIRED', 'WITHDRAWN')),
    CONSTRAINT chk_listings_window CHECK (pickup_until > pickup_from),
    CONSTRAINT chk_listings_best_before CHECK (best_before >= pickup_from),
    CONSTRAINT chk_listings_coords CHECK (pickup_lat BETWEEN -90 AND 90 AND pickup_lng BETWEEN -180 AND 180),
    CONSTRAINT fk_listings_donor FOREIGN KEY (donor_id) REFERENCES users (id) ON DELETE RESTRICT,
    -- The scheduler's and the browse screen's main filter, then the bounding-box search.
    INDEX idx_listings_status_until (status, pickup_until),
    INDEX idx_listings_geo (status, pickup_lat, pickup_lng),
    INDEX idx_listings_donor_status (donor_id, status, closed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- VEGETARIAN, VEGAN, JAIN, HALAL, CONTAINS_EGG, CONTAINS_NUTS, CONTAINS_GLUTEN
CREATE TABLE listing_dietary (
    listing_id BIGINT      NOT NULL,
    tag        VARCHAR(16) NOT NULL,
    PRIMARY KEY (listing_id, tag),
    CONSTRAINT chk_listing_dietary CHECK (tag IN ('VEGETARIAN', 'VEGAN', 'JAIN', 'HALAL', 'CONTAINS_EGG', 'CONTAINS_NUTS', 'CONTAINS_GLUTEN')),
    CONSTRAINT fk_listing_dietary_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The photo bytes live in their own table, so a query on listings never reads them.
CREATE TABLE listing_photos (
    listing_id   BIGINT      NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    size_bytes   INT         NOT NULL,
    data         LONGBLOB    NOT NULL,
    PRIMARY KEY (listing_id),
    CONSTRAINT chk_listing_photos_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT chk_listing_photos_size CHECK (size_bytes BETWEEN 1 AND 5242880),
    CONSTRAINT fk_listing_photos_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One row per claim attempt that won. status: CLAIMED -> READY -> PICKED_UP -> COMPLETED, or CANCELLED / EXPIRED
-- from CLAIMED or READY. The claim number shown to people is "FL-" + (id + 2000), computed, not stored.
CREATE TABLE claims (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    listing_id     BIGINT      NOT NULL,
    receiver_id    BIGINT      NOT NULL,
    status         VARCHAR(10) NOT NULL,
    claimed_at     DATETIME(6) NOT NULL,
    ready_at       DATETIME(6) NULL,
    picked_up_at   DATETIME(6) NULL,
    completed_at   DATETIME(6) NULL,
    cancelled_at   DATETIME(6) NULL,
    cancelled_by   BIGINT      NULL,
    expired_at     DATETIME(6) NULL,
    -- TRUE when the 24-hour auto-complete did it, FALSE when the receiver confirmed receipt.
    auto_completed BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    -- Equal to listing_id while the claim is active and NULL otherwise. NULLs never collide in a UNIQUE index,
    -- so this index lets the database itself refuse a second active claim on one listing, even if a bug
    -- in the application let two requests through (MySQL has no partial unique index).
    active_listing_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('CLAIMED', 'READY', 'PICKED_UP') THEN listing_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uq_claims_one_active_per_listing UNIQUE (active_listing_id),
    CONSTRAINT chk_claims_status CHECK (status IN ('CLAIMED', 'READY', 'PICKED_UP', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT fk_claims_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_claims_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_claims_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_claims_receiver_status (receiver_id, status),
    INDEX idx_claims_listing (listing_id),
    INDEX idx_claims_status_picked_up (status, picked_up_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- kind LISTING: a receiver reports a listing (so its donor), once per listing.
-- kind RECEIVER: a donor reports the receiver of a claim, once per claim.
-- target_ref is the listing id (LISTING) or the claim id (RECEIVER), so one UNIQUE index covers both rules.
-- subject_id is the account being reported. status: OPEN, DISMISSED (no action), ACTIONED (account suspended).
CREATE TABLE reports (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    kind        VARCHAR(8)   NOT NULL,
    reporter_id BIGINT       NOT NULL,
    subject_id  BIGINT       NOT NULL,
    listing_id  BIGINT       NOT NULL,
    claim_id    BIGINT       NULL,
    target_ref  BIGINT       NOT NULL,
    reason      VARCHAR(20)  NOT NULL,
    details     VARCHAR(500) NULL,
    status      VARCHAR(10)  NOT NULL,
    resolved_by BIGINT       NULL,
    resolved_at DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_reports_once UNIQUE (reporter_id, kind, target_ref),
    CONSTRAINT chk_reports_kind CHECK (kind IN ('LISTING', 'RECEIVER')),
    CONSTRAINT chk_reports_status CHECK (status IN ('OPEN', 'DISMISSED', 'ACTIONED')),
    CONSTRAINT chk_reports_reason CHECK (reason IN ('SPOILED_OR_UNSAFE', 'NOT_AS_LISTED', 'DONOR_NOT_THERE', 'NO_SHOW',
        'RUDE_OR_UNSAFE', 'OTHER')),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reports_subject FOREIGN KEY (subject_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reports_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reports_claim FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reports_resolved_by FOREIGN KEY (resolved_by) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_reports_status_created (status, created_at),
    INDEX idx_reports_subject_status (subject_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Only the SHA-256 of each token is stored, so a database leak can't be replayed as a login.
CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_password_reset_tokens_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One summary row per job run: counts only, never an email address, a name or a reason.
CREATE TABLE job_runs (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    run_id       VARCHAR(36) NOT NULL,
    job          VARCHAR(14) NOT NULL,
    trigger_type VARCHAR(10) NOT NULL,
    status       VARCHAR(10) NOT NULL,
    as_of        DATETIME(6) NOT NULL,
    started_at   DATETIME(6) NOT NULL,
    finished_at  DATETIME(6) NULL,
    items_seen   INT         NOT NULL DEFAULT 0,
    items_done   INT         NOT NULL DEFAULT 0,
    skipped      INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_job_runs_run_id UNIQUE (run_id),
    CONSTRAINT chk_job_runs_job CHECK (job IN ('EXPIRY', 'AUTO_COMPLETE')),
    CONSTRAINT chk_job_runs_trigger CHECK (trigger_type IN ('SCHEDULE', 'API')),
    CONSTRAINT chk_job_runs_status CHECK (status IN ('RUNNING', 'FINISHED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
