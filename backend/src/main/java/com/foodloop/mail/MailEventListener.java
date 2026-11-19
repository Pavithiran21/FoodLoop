package com.foodloop.mail;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.foodloop.mail.MailEvents.AccountReinstated;
import com.foodloop.mail.MailEvents.AccountSuspended;
import com.foodloop.mail.MailEvents.ClaimCancelled;
import com.foodloop.mail.MailEvents.ClaimCompleted;
import com.foodloop.mail.MailEvents.ClaimCreated;
import com.foodloop.mail.MailEvents.ClaimExpired;
import com.foodloop.mail.MailEvents.ClaimPickedUp;
import com.foodloop.mail.MailEvents.ClaimReady;
import com.foodloop.mail.MailEvents.DonorNotVerified;
import com.foodloop.mail.MailEvents.DonorVerified;
import com.foodloop.mail.MailEvents.ListingExpired;

/**
 * Sends the emails for events that services and jobs published. Two things matter here:
 * AFTER_COMMIT, so a move that was rolled back (it lost a race) never sends an email, and {@code @Async}, so the SMTP
 * server's speed (Ethereal takes 5 to 16 seconds) never slows an API response or holds a database lock.
 * {@code fallbackExecution = true} makes the same listener also run for an event published outside a transaction,
 * which is how the jobs publish (each claim is its own committed UPDATE). The mail service re-reads everything from
 * the database, so it sends what was really saved.
 */
@Component
public class MailEventListener {

    private final MailService mailService;

    public MailEventListener(MailService mailService) {
        this.mailService = mailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClaimCreated(ClaimCreated event) {
        mailService.sendNewClaim(event.claimId());
        mailService.sendClaimConfirmation(event.claimId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClaimReady(ClaimReady event) {
        mailService.sendReady(event.claimId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClaimPickedUp(ClaimPickedUp event) {
        mailService.sendPickedUp(event.claimId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClaimCompleted(ClaimCompleted event) {
        mailService.sendCompleted(event.claimId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClaimCancelled(ClaimCancelled event) {
        mailService.sendCancelled(event.claimId(), event.notifyUserId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClaimExpired(ClaimExpired event) {
        mailService.sendClaimExpired(event.claimId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onListingExpired(ListingExpired event) {
        mailService.sendListingExpired(event.listingId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDonorVerified(DonorVerified event) {
        mailService.sendDonorVerified(event.userId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDonorNotVerified(DonorNotVerified event) {
        mailService.sendDonorNotVerified(event.userId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountSuspended(AccountSuspended event) {
        mailService.sendSuspended(event.userId(), event.reason());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountReinstated(AccountReinstated event) {
        mailService.sendReinstated(event.userId());
    }
}
