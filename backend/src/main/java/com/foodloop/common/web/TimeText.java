package com.foodloop.common.web;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The wording of dates and times in messages and emails, always in the listing's (pick-up place's) time zone.
 * Locale.ENGLISH on purpose: en-GB writes September as "Sept" and uses "pm" differently.
 */
public final class TimeText {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter ZONE_NAME = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH);

    private TimeText() {
    }

    /** "9:00 am" */
    public static String clock(LocalTime time) {
        return CLOCK.format(time).toLowerCase(Locale.ENGLISH);
    }

    /** "9:00 am" for an instant, shown in the zone. */
    public static String clock(Instant instant, ZoneId zone) {
        return clock(instant.atZone(zone).toLocalTime());
    }

    /** "Tue 20 Oct" */
    public static String shortDate(LocalDate date) {
        return SHORT_DATE.format(date);
    }

    /** "Tue 20 Oct 2026" */
    public static String longDate(LocalDate date) {
        return LONG_DATE.format(date);
    }

    /** "IST", "BST", "GMT": the zone's short name at that moment. */
    public static String zoneName(ZoneId zone, Instant at) {
        return ZONE_NAME.format(ZonedDateTime.ofInstant(at, zone));
    }

    /** "9:00 am – 1:00 pm IST" */
    public static String range(Instant start, Instant end, ZoneId zone) {
        return clock(start, zone) + " – " + clock(end, zone) + " " + zoneName(zone, start);
    }

    /** "8 hours", "1 hour", "7 hours 30 minutes", "45 minutes" */
    public static String length(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        String h = hours == 1 ? "1 hour" : hours + " hours";
        String m = minutes + (minutes == 1 ? " minute" : " minutes");
        if (hours == 0) {
            return m;
        }
        return minutes == 0 ? h : h + " " + m;
    }
}
