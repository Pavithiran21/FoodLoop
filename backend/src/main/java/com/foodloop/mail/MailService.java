package com.foodloop.mail;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import com.foodloop.auth.PasswordResetService;
import com.foodloop.claim.Claim;
import com.foodloop.claim.ClaimRepository;
import com.foodloop.common.web.TimeText;
import com.foodloop.config.AppProperties;
import com.foodloop.donor.DonorProfile;
import com.foodloop.donor.DonorProfileRepository;
import com.foodloop.listing.Listing;
import com.foodloop.listing.ListingRepository;
import com.foodloop.listing.ListingStatus;
import com.foodloop.user.User;
import com.foodloop.user.UserRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends FoodLoop's emails through plain SMTP (Ethereal in development and tests, Brevo for real mail; only the SMTP_*
 * values in .env differ). All of them use one Thymeleaf layout, which escapes every value; a plain-text part is
 * included for mail clients that don't show HTML. Every time is shown in the listing's time zone with the zone named.
 *
 * Demo addresses (@demo.foodloop.test) never exist, so their emails go to the demo Ethereal inbox (DEMO_SMTP_*) and
 * never through Brevo; without a demo inbox they are not sent.
 *
 * Sending never throws: it returns false and logs the email type, the user id and the error class only, never the
 * address, a name, a food item or an admin's reason.
 */
@Service
public class MailService {

    /** Every demo account's address ends with this reserved domain. */
    public static final String DEMO_EMAIL_DOMAIN = "demo.foodloop.test";

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String DEMO_ADDRESS_SUFFIX = "@" + DEMO_EMAIL_DOMAIN;
    /** The logo tile, sent as an inline part of every email and shown by the layout as {@code cid:foodloop-logo}. */
    private static final String LOGO_CONTENT_ID = "foodloop-logo";
    private static final ClassPathResource LOGO = new ClassPathResource("mail/foodloop-logo.png");
    /** A dropped SMTP connection is tried again: three tries in all, 2 s and then 4 s apart. */
    private static final int SEND_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final String SAFETY_NOTE = "Food safety is the donor's responsibility. Check the food and its best-before time before you eat it.";

    /** One row of the card in the email: "Pick-up" and its value. */
    public record Detail(String label, String value) {
    }

    private final JavaMailSender mailSender;
    private final DemoMailSender demoMailSender;
    private final ITemplateEngine templates;
    private final AppProperties props;
    private final UserRepository users;
    private final DonorProfileRepository donorProfiles;
    private final ListingRepository listings;
    private final ClaimRepository claims;

    public MailService(JavaMailSender mailSender, DemoMailSender demoMailSender, ITemplateEngine templates, AppProperties props,
            UserRepository users, DonorProfileRepository donorProfiles, ListingRepository listings, ClaimRepository claims) {
        this.mailSender = mailSender;
        this.demoMailSender = demoMailSender;
        this.templates = templates;
        this.props = props;
        this.users = users;
        this.donorProfiles = donorProfiles;
        this.listings = listings;
        this.claims = claims;
    }

    // ---- Account emails ----

    public boolean sendPasswordReset(User user, String token) {
        long minutes = PasswordResetService.LINK_LIFETIME.toMinutes();
        Message message = new Message("Reset your FoodLoop password", "Reset your password",
                List.of("We got a request to reset the password for " + user.getEmail() + ".",
                        "The link works once and expires in " + minutes + " minutes."),
                List.of(), "Choose a new password", props.frontendUrl() + "/reset-password?token=" + token,
                "If you didn't ask for this, ignore this email. Your password stays the same.");
        return send("password_reset", user.getId(), user.getEmail(), message);
    }

    public boolean sendDonorVerified(Long userId) {
        return withDonor(userId, "donor_verified", (user, donor) -> new Message("You're verified: start listing food",
                "You're verified", List.of(donor.getBusinessName() + " can now list surplus food on FoodLoop."),
                List.of(new Detail("Business", donor.getBusinessName())), "List surplus food",
                props.frontendUrl() + "/donor/listings/new", "Receivers near you will see your listings as soon as you publish them."));
    }

    public boolean sendDonorNotVerified(Long userId) {
        return withDonor(userId, "donor_not_verified", (user, donor) -> new Message("Your donor account wasn't verified",
                "Your donor account wasn't verified",
                List.of("An admin looked at " + donor.getBusinessName() + " and couldn't verify it yet.",
                        "You can fix your details and ask for another review."),
                List.of(new Detail("Business", donor.getBusinessName()),
                        new Detail("Reason", donor.getRejectionReason() == null ? "" : donor.getRejectionReason())),
                "Edit business details", props.frontendUrl() + "/profile", "You can't list food until an admin verifies your account."));
    }

    public boolean sendSuspended(Long userId, String reason) {
        User user = users.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        Message message = new Message("Your account was suspended", "Your account was suspended",
                List.of("An admin suspended your FoodLoop account. Your open listings and claims were closed."),
                List.of(new Detail("Reason", reason)),
                "Open FoodLoop", props.frontendUrl(), "If you think this is a mistake, reply to this email.");
        return send("account_suspended", user.getId(), user.getEmail(), message);
    }

    public boolean sendReinstated(Long userId) {
        User user = users.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        Message message = new Message("Your account was reinstated", "Your account was reinstated",
                List.of("An admin reinstated your FoodLoop account. You can log in again."), List.of(), "Log in",
                props.frontendUrl(), "Closed listings and claims are not restored.");
        return send("account_reinstated", user.getId(), user.getEmail(), message);
    }

    // ---- Claim emails ----

    /** To the donor: a receiver claimed their listing. */
    public boolean sendNewClaim(Long claimId) {
        return withClaim(claimId, (claim, listing, donor, receiver) -> {
            Message message = new Message(firstName(receiver.getName()) + " claimed your " + listing.getTitle(),
                    "Someone claimed your food",
                    List.of(receiver.getName() + " claimed " + listing.getTitle() + ". Pack it before the pick-up window starts."),
                    claimDetails(claim, listing, false), "Open claim", claimUrl(claim),
                    "Mark it as packed when it is ready, and confirm the handover when the receiver shows the claim number.");
            return send("claim_new", donor.getId(), donor.getEmail(), message);
        });
    }

    /** To the receiver: the claim worked, and where and when to collect. */
    public boolean sendClaimConfirmation(Long claimId) {
        return withClaim(claimId, (claim, listing, donor, receiver) -> {
            Message message = new Message("You claimed " + listing.getTitle(), "You claimed " + listing.getTitle(),
                    List.of("It's reserved for you. Show the claim number at pick-up."),
                    claimDetails(claim, listing, true), "View your claim", claimUrl(claim), SAFETY_NOTE);
            return send("claim_confirmation", receiver.getId(), receiver.getEmail(), message);
        });
    }

    public boolean sendReady(Long claimId) {
        return withClaim(claimId, (claim, listing, donor, receiver) -> {
            Message message = new Message("Your food is ready for pick-up", "Your food is ready for pick-up",
                    List.of(donorName(donor) + " packed " + listing.getTitle() + " for you."),
                    claimDetails(claim, listing, true), "View your claim", claimUrl(claim), SAFETY_NOTE);
            return send("claim_ready", receiver.getId(), receiver.getEmail(), message);
        });
    }

    /** To both: the donor confirmed the hand-over. The receiver is asked to confirm receipt. */
    public boolean sendPickedUp(Long claimId) {
        return withClaim(claimId, (claim, listing, donor, receiver) -> {
            boolean sentToReceiver = send("claim_picked_up", receiver.getId(), receiver.getEmail(),
                    new Message("Picked up: " + listing.getTitle(), "Picked up",
                            List.of("The hand-over of " + listing.getTitle() + " was confirmed. Tell us you got it, or it completes by itself in 24 hours."),
                            claimDetails(claim, listing, false), "I got it", claimUrl(claim), SAFETY_NOTE));
            boolean sentToDonor = send("claim_picked_up", donor.getId(), donor.getEmail(),
                    new Message("Picked up: " + listing.getTitle(), "Handover confirmed",
                            List.of("You confirmed the hand-over to " + receiver.getName() + ". The claim completes when they confirm, or after 24 hours."),
                            claimDetails(claim, listing, false), "Open claim", claimUrl(claim), "Thank you for passing food on."));
            return sentToReceiver && sentToDonor;
        });
    }

    /** To both: the claim is complete. */
    public boolean sendCompleted(Long claimId) {
        return withClaim(claimId, (claim, listing, donor, receiver) -> {
            String how = claim.isAutoCompleted() ? "It completed by itself 24 hours after the hand-over." : "The receiver confirmed they got the food.";
            boolean toReceiver = send("claim_completed", receiver.getId(), receiver.getEmail(),
                    new Message("Completed: " + listing.getTitle(), "Completed",
                            List.of("Claim " + claim.number() + " is complete. " + how),
                            claimDetails(claim, listing, false), "Find more food", props.frontendUrl() + "/home", "Thank you for using FoodLoop."));
            boolean toDonor = send("claim_completed", donor.getId(), donor.getEmail(),
                    new Message("Completed: " + listing.getTitle(), "Completed",
                            List.of("Claim " + claim.number() + " is complete. " + how),
                            claimDetails(claim, listing, false), "See your listings", props.frontendUrl() + "/donor",
                            "Thank you for passing food on."));
            return toReceiver && toDonor;
        });
    }

    /** To the side that did not cancel (or, after a suspension, the side that was not suspended). */
    public boolean sendCancelled(Long claimId, Long notifyUserId) {
        return withClaim(claimId, (claim, listing, donor, receiver) -> {
            User to = notifyUserId.equals(donor.getId()) ? donor : receiver;
            User by = claim.getCancelledBy() == null ? null : users.findById(claim.getCancelledBy()).orElse(null);
            String who = by == null ? "Someone" : switch (by.getRole()) {
                case DONOR -> by.getId().equals(donor.getId()) ? "The donor" : "Someone";
                case RECEIVER -> "The receiver";
                case ADMIN -> "An admin";
            };
            boolean back = listing.getStatus() == ListingStatus.LISTED;
            List<Detail> details = new ArrayList<>(claimDetails(claim, listing, false));
            details.add(new Detail("Cancelled by", who));
            details.add(new Detail("Listing", back ? "Back on the list" : "Closed"));
            boolean toDonor = to.getId().equals(donor.getId());
            String extra = toDonor ? (back ? " The listing is back on the list for other receivers." : " The listing is closed.") : "";
            Message message = new Message("Claim " + claim.number() + " was cancelled", "Claim " + claim.number() + " was cancelled",
                    List.of(who + " cancelled the claim on " + listing.getTitle() + "." + extra), details,
                    toDonor ? "See your listings" : "Find more food", props.frontendUrl() + (toDonor ? "/donor" : "/home"),
                    "You can claim other food or list more at any time.");
            return send("claim_cancelled", to.getId(), to.getEmail(), message);
        });
    }

    /** The claim was not collected before the window ended: both sides are told. */
    public boolean sendClaimExpired(Long claimId) {
        return withClaim(claimId, (claim, listing, donor, receiver) -> {
            ZoneId zone = ZoneId.of(listing.getTimeZone());
            String ended = TimeText.clock(listing.getPickupUntil(), zone) + " " + TimeText.zoneName(zone, listing.getPickupUntil());
            boolean toReceiver = send("claim_expired", receiver.getId(), receiver.getEmail(),
                    new Message("Your claim expired", "Your claim expired",
                            List.of("Claim " + claim.number() + " for " + listing.getTitle() + " wasn't collected before " + ended
                                    + ", so it was closed automatically."),
                            claimDetails(claim, listing, false), "Find more food", props.frontendUrl() + "/home",
                            "Cancel a claim you can't collect, so the food can go to someone else."));
            boolean toDonor = send("claim_expired", donor.getId(), donor.getEmail(),
                    new Message("Your listing expired", "Your listing expired",
                            List.of(listing.getTitle() + " wasn't collected before " + ended + ", so it was closed automatically."),
                            claimDetails(claim, listing, false), "See your listings", props.frontendUrl() + "/donor",
                            "You can list the food again with a new pick-up window."));
            return toReceiver && toDonor;
        });
    }

    /** To the donor: nobody claimed the listing before its window ended. */
    public boolean sendListingExpired(Long listingId) {
        Listing listing = listings.findById(listingId).orElse(null);
        User donor = listing == null ? null : users.findById(listing.getDonorId()).orElse(null);
        if (donor == null) {
            return false;
        }
        ZoneId zone = ZoneId.of(listing.getTimeZone());
        String ended = TimeText.clock(listing.getPickupUntil(), zone) + " " + TimeText.zoneName(zone, listing.getPickupUntil());
        List<Detail> details = List.of(new Detail("Food", listing.getTitle() + " · " + listing.getQuantity()),
                new Detail("Pick-up was", pickupText(listing)));
        Message message = new Message("Your listing expired", "Your listing expired",
                List.of(listing.getTitle() + " wasn't claimed before " + ended + ", so it was closed automatically."), details,
                "See your listings", props.frontendUrl() + "/donor", "You can list the food again with a new pick-up window.");
        return send("listing_expired", donor.getId(), donor.getEmail(), message);
    }

    // ---- Building and sending ----

    /** What a template needs for one email. */
    private record Message(String subject, String headline, List<String> paragraphs, List<Detail> details,
            String buttonLabel, String buttonUrl, String footnote) {
    }

    @FunctionalInterface
    private interface ClaimMail {
        boolean build(Claim claim, Listing listing, User donor, User receiver);
    }

    @FunctionalInterface
    private interface DonorMail {
        Message build(User user, DonorProfile donor);
    }

    private boolean withClaim(Long claimId, ClaimMail mail) {
        Claim claim = claims.findById(claimId).orElse(null);
        Listing listing = claim == null ? null : listings.findById(claim.getListingId()).orElse(null);
        User donor = listing == null ? null : users.findById(listing.getDonorId()).orElse(null);
        User receiver = claim == null ? null : users.findById(claim.getReceiverId()).orElse(null);
        if (donor == null || receiver == null) {
            return false;
        }
        return mail.build(claim, listing, donor, receiver);
    }

    private boolean withDonor(Long userId, String type, DonorMail builder) {
        User user = users.findById(userId).orElse(null);
        DonorProfile donor = user == null ? null : donorProfiles.findById(userId).orElse(null);
        if (donor == null) {
            return false;
        }
        return send(type, user.getId(), user.getEmail(), builder.build(user, donor));
    }

    private boolean send(String type, Long userId, String to, Message message) {
        Context context = new Context();
        context.setVariable("headline", message.headline());
        context.setVariable("paragraphs", message.paragraphs());
        context.setVariable("details", message.details());
        context.setVariable("buttonLabel", message.buttonLabel());
        context.setVariable("buttonUrl", message.buttonUrl());
        context.setVariable("footnote", message.footnote());
        String html = templates.process("mail/message", context);
        String text = plainText(message);

        JavaMailSender sender = mailSender;
        if (isDemoAddress(to)) {
            if (!demoMailSender.isConfigured()) {
                log.warn("Email not sent: type={}, userId={}, error=DemoInboxNotConfigured (set DEMO_SMTP_*)", type, userId);
                return false;
            }
            sender = demoMailSender.sender();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                MimeMessage mime = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
                helper.setFrom(props.mailFrom());
                helper.setTo(to);
                helper.setSubject(message.subject());
                helper.setText(text, html);
                // After setText, as MimeMessageHelper requires; named, so no mail client lists it as "inline".
                helper.addInline(LOGO_CONTENT_ID, "foodloop-logo.png", LOGO, "image/png");
                sender.send(mime);
                return true;
            } catch (MailException | MessagingException ex) {
                // A dropped connection is worth another try; a refused login or a bad address is not.
                if (attempt < SEND_ATTEMPTS && isConnectionProblem(ex) && pause(RETRY_DELAY.multipliedBy(attempt))) {
                    continue;
                }
                // Class names only: an SMTP error message can quote the recipient's address. The cause tells a timeout
                // from a refused login or a TLS failure.
                log.warn("Email not sent: type={}, userId={}, error={}, cause={}, attempts={}", type, userId,
                        ex.getClass().getSimpleName(), rootCauseName(ex), attempt);
                return false;
            }
        }
    }

    /** True when the SMTP connection itself failed (reset, timeout, refused), not the server's answer. */
    private static boolean isConnectionProblem(Exception ex) {
        if (ex instanceof MailAuthenticationException) {
            return false;
        }
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.io.IOException) {
                return true;
            }
        }
        return false;
    }

    /** Waits before the next try. Runs on the mail thread (after the commit), never in a request. */
    private static boolean pause(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String plainText(Message message) {
        StringBuilder text = new StringBuilder(message.headline()).append("\n\n");
        message.paragraphs().forEach(paragraph -> text.append(paragraph).append("\n\n"));
        message.details().forEach(detail -> text.append(detail.label()).append(": ").append(detail.value()).append("\n"));
        if (!message.details().isEmpty()) {
            text.append("\n");
        }
        text.append(message.buttonLabel()).append(": ").append(message.buttonUrl()).append("\n\n").append(message.footnote()).append("\n");
        return text.toString();
    }

    // ---- Wording shared by the claim emails ----

    /**
     * The card in a claim email. The pick-up address goes only to the receiver who claimed it (withAddress) and is never
     * in the donor's copy, where it would be their own address anyway.
     */
    private static List<Detail> claimDetails(Claim claim, Listing listing, boolean withAddress) {
        ZoneId zone = ZoneId.of(listing.getTimeZone());
        List<Detail> details = new ArrayList<>();
        details.add(new Detail("Claim number", claim.number()));
        details.add(new Detail("Food", listing.getTitle() + " · " + listing.getQuantity()));
        details.add(new Detail("Pick-up", pickupText(listing)));
        details.add(new Detail("Best before", TimeText.shortDate(listing.getBestBefore().atZone(zone).toLocalDate()) + ", "
                + TimeText.clock(listing.getBestBefore(), zone) + " " + TimeText.zoneName(zone, listing.getBestBefore())));
        if (withAddress) {
            details.add(new Detail("Address", listing.getPickupAddress()));
        }
        return details;
    }

    /** "Tue 20 Oct, 6:00 pm – 8:00 pm IST" */
    private static String pickupText(Listing listing) {
        ZoneId zone = ZoneId.of(listing.getTimeZone());
        return TimeText.shortDate(listing.getPickupFrom().atZone(zone).toLocalDate()) + ", "
                + TimeText.range(listing.getPickupFrom(), listing.getPickupUntil(), zone);
    }

    private String claimUrl(Claim claim) {
        return props.frontendUrl() + "/claims/" + claim.getId();
    }

    private String donorName(User donor) {
        return donorProfiles.findById(donor.getId()).map(DonorProfile::getBusinessName).orElse(donor.getName());
    }

    /**
     * "Meera N." becomes "Meera". The deleted-account names stay whole: emails are built after the commit, so a
     * receiver who claims and then deletes the account at once is already "Former user", not "Former".
     */
    private static String firstName(String name) {
        if (User.FORMER_USER.equals(name) || User.FORMER_DONOR.equals(name)) {
            return name;
        }
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }

    private static boolean isDemoAddress(String to) {
        return to.toLowerCase(Locale.ROOT).endsWith(DEMO_ADDRESS_SUFFIX);
    }

    /** The deepest cause's class. Spring's MailSendException keeps it per message, not as getCause(). */
    private static String rootCauseName(Exception ex) {
        Throwable cause = ex;
        if (ex instanceof MailSendException sendFailure && sendFailure.getMessageExceptions().length > 0) {
            cause = sendFailure.getMessageExceptions()[0];
        }
        return NestedExceptionUtils.getMostSpecificCause(cause).getClass().getSimpleName();
    }
}
