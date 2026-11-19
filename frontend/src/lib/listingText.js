import { browserZone, formatTime, formatWindow, relativeDay, zoneShortName } from './time';

// The sentences a listing's times appear in. Times are always in the listing's own zone (its pick-up place); the
// zone is named ("IST") when it isn't the zone of the person looking.

function zoneSuffix(timeZone, instant) {
  return timeZone === browserZone() ? '' : ` ${zoneShortName(timeZone, instant)}`;
}

/** "Today 6:00 – 8:00 pm" (plus " IST" when that isn't the viewer's zone). */
export function windowText(listing, now = Date.now()) {
  return (
    formatWindow(listing.pickupFrom, listing.pickupUntil, listing.timeZone, now) +
    zoneSuffix(listing.timeZone, listing.pickupFrom)
  );
}

const capitalise = (text) => text.charAt(0).toUpperCase() + text.slice(1);

/**
 * "today, 11:00 pm" / "Tue 20 Oct, 10:00 am" (plus the zone when it differs), for the middle of a sentence;
 * { capital: true } for the start of a value ("Today, 11:00 pm").
 */
export function bestBeforeText(listing, now = Date.now(), { capital = false } = {}) {
  const { bestBefore, timeZone } = listing;
  const text = `${relativeDay(bestBefore, timeZone, now)}, ${formatTime(bestBefore, timeZone)}${zoneSuffix(timeZone, bestBefore)}`;
  return capital ? capitalise(text) : text;
}

/** "Tue 20 Oct, 10:00 am IST": always with the zone (the donor's own listing, the claim panel). */
export function withZone(instant, timeZone, now = Date.now(), { capital = true } = {}) {
  const text = `${relativeDay(instant, timeZone, now)}, ${formatTime(instant, timeZone)} ${zoneShortName(timeZone, instant)}`;
  return capital ? capitalise(text) : text;
}

/** "Indiranagar, Bengaluru" from "Indiranagar, Bengaluru, Karnataka, India": the first `parts` parts of a place. */
export function shortPlace(label, parts = 2) {
  return label.split(',').slice(0, parts).map((part) => part.trim()).join(', ');
}

/** "0.8 km", "12 km". */
export function distanceText(km) {
  return km < 10 ? `${km.toFixed(1)} km` : `${Math.round(km)} km`;
}
