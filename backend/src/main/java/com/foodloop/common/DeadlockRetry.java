package com.foodloop.common;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.foodloop.common.error.DatabaseErrors;

/**
 * Runs a piece of work in its own transaction, and runs it again when MySQL picked it as the loser of a deadlock.
 *
 * Why deadlocks can still happen although every claim move locks the claim first and then the listing: InnoDB checks a
 * foreign key whenever it writes an index entry that holds the key, and that check takes a SHARED lock on the parent row.
 * A status change rewrites idx_claims_receiver_status (receiver_id, status) and idx_listings_donor_status (donor_id,
 * status, closed_at), so every claim move, claim, withdrawal and job step also locks the receiver's or donor's users row.
 * Suspending or deleting an account locks that same users row FIRST and then the account's claims and listings. Two such
 * transactions can wait for each other; InnoDB then rolls one back with error 1213 ("try restarting transaction").
 *
 * The MySQL manual's advice is to run the rolled-back transaction again, which is what this does (up to 3 attempts). Each
 * attempt is a new transaction that reads everything again, so the second try sees what the winner did (for example the
 * claim is already cancelled, which then answers 422 instead of a 500). Emails are only sent after a commit, so a
 * rolled-back attempt sends nothing.
 *
 * Only the outermost call retries. Called inside a transaction that is already running (for example "suspend from a
 * report" calls the account suspension), it just runs the work in that transaction: a deadlock has already doomed the
 * outer transaction, so only the code that started it can run it again. Methods that use it are therefore not
 * @Transactional themselves.
 */
@Component
public class DeadlockRetry {

    private static final int ATTEMPTS = 3;
    private static final Logger log = LoggerFactory.getLogger(DeadlockRetry.class);

    private final TransactionTemplate transaction;

    public DeadlockRetry(TransactionTemplate transaction) {
        this.transaction = transaction;
    }

    public <T> T inTransaction(Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return work.get();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.execute(status -> work.get());
            } catch (PessimisticLockingFailureException ex) {
                if (!DatabaseErrors.isDeadlock(ex) || attempt == ATTEMPTS) {
                    throw ex;
                }
                log.info("Deadlock: running the transaction again (attempt {} of {})", attempt + 1, ATTEMPTS);
            }
        }
    }

    public void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
