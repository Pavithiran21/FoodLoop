package com.foodloop.seed;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.foodloop.claim.ClaimStatus;
import com.foodloop.donor.VerificationStatus;
import com.foodloop.listing.Category;
import com.foodloop.listing.Dietary;
import com.foodloop.listing.Listing;
import com.foodloop.listing.ListingStatus;
import com.foodloop.report.Report;
import com.foodloop.report.ReportEnums.Reason;
import com.foodloop.user.Role;
import com.foodloop.user.User;

/**
 * The Bengaluru sample data that the dev seed and the demo seed both build (password DEMO_PASSWORD for everyone): six donors
 * (three verified, two waiting, one not verified with a reason), three receivers, two Admins, and listings in every state
 * (listed, claimed, ready, picked up, completed, expired, withdrawn and one whose claim was cancelled), plus three
 * reports, so each screen of each role has something to show. Times are relative to "now", so the data is always current.
 * Every address ends with @demo.foodloop.test, so any email the app sends goes to the demo Ethereal inbox (DEMO_SMTP_*),
 * never through Brevo.
 */
@Component
@Profile({ "seed-dev", "seed-demo" })
public class SampleData {

    public static final String DOMAIN = "@demo.foodloop.test";
    private static final String INDIRANAGAR = "Indiranagar, Bengaluru, Karnataka, India";
    private static final ZoneId BENGALURU = ZoneId.of("Asia/Kolkata");

    private final SeedSupport seed;
    private final Clock clock;

    public SampleData(SeedSupport seed, Clock clock) {
        this.seed = seed;
        this.clock = clock;
    }

    /** Builds the sample data in an empty database (the caller checks the database name and wipes it first). */
    public void build() {
        Instant now = clock.instant();

        // People. The demo accounts (the "Log in as" buttons) are donor@, receiver@ and admin@.
        User admin = seed.user("Neha Kapoor", "admin" + DOMAIN, Role.ADMIN, true);
        seed.user("Rohan Das", "admin2" + DOMAIN, Role.ADMIN, false);

        User crumb = seed.donor("Asha Rao", "donor" + DOMAIN, "Crumb & Co.", VerificationStatus.VERIFIED, null, "10020041000123",
                "100 Feet Road, Indiranagar, Bengaluru 560038", "Indiranagar", 12.9719, 77.6412, true, admin);
        User annapurna = seed.donor("Lakshmi Iyer", "annapurna" + DOMAIN, "Annapurna Kitchen", VerificationStatus.VERIFIED, null,
                "11221999000456", "27 Old Airport Road, Domlur, Bengaluru 560071", "Domlur", 12.9610, 77.6387, false, admin);
        User oven = seed.donor("Dev Malhotra", "ovenstory" + DOMAIN, "Oven Story", VerificationStatus.VERIFIED, null, null,
                "80 Feet Road, Koramangala, Bengaluru 560034", "Koramangala", 12.9352, 77.6245, false, admin);
        seed.donor("Sam Fernandes", "quickbites" + DOMAIN, "Quick Bites", VerificationStatus.WAITING, null, "10019333000789",
                "5 Church Street, Shivajinagar, Bengaluru 560001", "Shivajinagar", 12.9756, 77.6049, false, null);
        seed.donor("Nisha Rao", "newdonor" + DOMAIN, "Fresh Basket Foods", VerificationStatus.WAITING, null, null,
                "14 Sarjapur Road, Bellandur, Bengaluru 560103", "Bellandur", 12.9259, 77.6762, false, null);
        seed.donor("Tara Singh", "greenbasket" + DOMAIN, "Green Basket", VerificationStatus.NOT_VERIFIED,
                "The FSSAI number doesn't match the business name.", "99999999999999",
                "22 CMH Road, Indiranagar, Bengaluru 560038", "Indiranagar", 12.9784, 77.6408, false, admin);

        User meera = seed.receiver("Meera N.", "receiver" + DOMAIN, true, INDIRANAGAR, 12.9784, 77.6408);
        User kiran = seed.receiver("Kiran P.", "kiran" + DOMAIN, false, "Koramangala, Bengaluru, Karnataka, India", 12.9352, 77.6245);
        seed.receiver("Hope Shelter", "hope" + DOMAIN, false, "Domlur, Bengaluru, Karnataka, India", 12.9610, 77.6387);

        // Crumb & Co.'s listings: one in every state the donor's board shows.
        Set<Dietary> veg = Set.of(Dietary.VEGETARIAN);
        seed.listing(crumb, "Sourdough loaves and buns", "12 items", Category.BAKERY, Set.of(Dietary.VEGETARIAN, Dietary.CONTAINS_EGG),
                now.minus(mins(30)), now.plus(mins(180)), now.plus(hours(16)));
        seed.listing(crumb, "Banana loaf cakes", "8 loaves", Category.BAKERY, veg, now.plus(hours(4)), now.plus(hours(7)), now.plus(hours(30)));

        Listing croissants = seed.listing(crumb, "Almond croissants", "10 pieces", Category.BAKERY,
                Set.of(Dietary.VEGETARIAN, Dietary.CONTAINS_NUTS), now.minus(mins(10)), now.plus(hours(4)), now.plus(hours(20)));
        seed.claim(croissants, meera, ClaimStatus.CLAIMED, now.minus(mins(5)), null);

        Listing sandwiches = seed.listing(crumb, "Multigrain sandwiches", "15 packs", Category.PACKAGED, veg,
                now.minus(hours(1)), now.plus(hours(2)), now.plus(hours(10)));
        seed.claim(sandwiches, kiran, ClaimStatus.READY, now.minus(mins(45)), null);

        Listing tarts = seed.listing(crumb, "Fruit tarts", "6 tarts", Category.BAKERY, Set.of(Dietary.VEGETARIAN, Dietary.CONTAINS_EGG),
                now.minus(hours(3)), now.plus(hours(1)), now.plus(hours(8)));
        seed.claim(tarts, meera, ClaimStatus.PICKED_UP, now.minus(mins(150)), null);

        Listing bagels = seed.listing(crumb, "Plain bagels", "20 bagels", Category.BAKERY, Set.of(Dietary.VEGAN),
                now.minus(hours(30)), now.minus(hours(27)), now.minus(hours(6)));
        seed.claim(bagels, meera, ClaimStatus.COMPLETED, now.minus(hours(29)), null);

        Listing rolls = seed.listing(crumb, "Day-old bread rolls", "30 rolls", Category.BAKERY, veg, now.minus(hours(28)),
                now.minus(hours(26)), now.minus(hours(20)));
        seed.close(rolls, ListingStatus.EXPIRED, now.minus(hours(26)));

        Listing puffs = seed.listing(crumb, "Cheese puffs", "24 puffs", Category.BAKERY, veg, now.minus(hours(5)), now.plus(hours(1)),
                now.plus(hours(12)));
        seed.close(puffs, ListingStatus.WITHDRAWN, now.minus(hours(4)));

        Listing lemon = seed.listing(crumb, "Lemon drizzle cakes", "6 cakes", Category.BAKERY, Set.of(Dietary.VEGETARIAN, Dietary.CONTAINS_EGG),
                now.minus(mins(20)), now.plus(hours(5)), now.plus(hours(24)));
        seed.claim(lemon, kiran, ClaimStatus.CANCELLED, now.minus(mins(40)), kiran);

        // Other verified donors' open listings, for Find food.
        seed.listing(annapurna, "Rice, dal and sabzi boxes", "20 meals", Category.COOKED_MEAL, Set.of(Dietary.VEGETARIAN, Dietary.JAIN),
                now.plus(mins(60)), now.plus(hours(3)), now.plus(hours(6)));
        seed.listing(annapurna, "Veg biryani", "15 portions", Category.COOKED_MEAL, veg, now.minus(mins(15)), now.plus(mins(150)),
                now.plus(hours(5)));
        seed.listing(oven, "Seasonal fruit crate", "1 crate (about 8 kg)", Category.FRUIT_VEG, Set.of(Dietary.VEGAN, Dietary.JAIN),
                now.minus(mins(5)), now.plus(hours(6)), now.plus(hours(48)));
        // Tomorrow 10:00-13:00 in Bengaluru, whatever time the seed runs, so the "Tomorrow" filter always finds it.
        // (now + 26 h lands on the day after tomorrow when the seed runs late in the evening.)
        Instant tomorrowTen = LocalDate.ofInstant(now, BENGALURU).plusDays(1).atTime(10, 0).atZone(BENGALURU).toInstant();
        seed.listing(oven, "Brownie trays", "4 trays", Category.PACKAGED, Set.of(Dietary.VEGETARIAN, Dietary.CONTAINS_NUTS, Dietary.CONTAINS_EGG),
                tomorrowTen, tomorrowTen.plus(hours(3)), tomorrowTen.plus(hours(48)));

        // Reports: a receiver reports a listing (open), a donor reports a receiver (open), and one already dismissed.
        Listing bowls = seed.listing(annapurna, "Curd rice bowls", "10 bowls", Category.COOKED_MEAL, veg, now.minus(hours(50)),
                now.minus(hours(48)), now.minus(hours(44)));
        seed.close(bowls, ListingStatus.EXPIRED, now.minus(hours(48)));
        seed.save(Report.aboutListing(meera.getId(), annapurna.getId(), bowls.getId(), Reason.NOT_AS_LISTED, "It was plain rice, not curd rice."));
        var noShow = seed.claim(rolls, kiran, ClaimStatus.EXPIRED, now.minus(hours(27)), null);
        seed.save(Report.aboutReceiver(crumb.getId(), kiran.getId(), rolls.getId(), noShow.getId(), Reason.NO_SHOW, null));
        Report dismissed = seed.save(Report.aboutListing(kiran.getId(), crumb.getId(), puffs.getId(), Reason.OTHER, "Listing photo looked old."));
        dismissed.dismiss(admin.getId(), now.minus(hours(1)));
        seed.save(dismissed);
        seed.backdateListings();
    }

    private static Duration mins(long n) {
        return Duration.ofMinutes(n);
    }

    private static Duration hours(long n) {
        return Duration.ofHours(n);
    }
}
