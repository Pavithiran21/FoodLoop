// Times as the API sends them: UTC instants ("2026-11-13T12:30:00Z"). Each listing carries the IANA time zone of
// its pick-up place, and every time on a listing or claim is shown in that zone, never the browser's. Only Find
// food's "Today" and "Tomorrow" use the browser's zone (the receiver's own day). Plain Intl, no date library.

// Chrome builds its zone names from ICU, which still uses some old IANA names (Asia/Calcutta for Asia/Kolkata).
const CURRENT_ZONE_NAMES = {
  'Asia/Calcutta': 'Asia/Kolkata',
  'Asia/Katmandu': 'Asia/Kathmandu',
  'Asia/Rangoon': 'Asia/Yangon',
  'Asia/Saigon': 'Asia/Ho_Chi_Minh',
  'Europe/Kiev': 'Europe/Kyiv',
  'America/Buenos_Aires': 'America/Argentina/Buenos_Aires',
  'America/Indianapolis': 'America/Indiana/Indianapolis',
};

export function currentZoneName(zone) {
  return CURRENT_ZONE_NAMES[zone] ?? zone;
}

/** The browser's own zone, for Find food's "Today" and "Tomorrow" and for a new donor's pick-up place. */
export function browserZone() {
  try {
    return currentZoneName(Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC');
  } catch {
    return 'UTC';
  }
}

/** The wall-clock parts of an instant in a zone: { year, month, day, hour, minute } as numbers. */
function wallParts(date, timeZone) {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date);
  const get = (type) => Number(parts.find((part) => part.type === type).value);
  return { year: get('year'), month: get('month'), day: get('day'), hour: get('hour'), minute: get('minute') };
}

/** How far the zone is ahead of UTC at that instant, in milliseconds (IST: +5 h 30 m). */
function zoneOffsetMs(timeZone, utcMs) {
  const wall = wallParts(new Date(utcMs), timeZone);
  const wallAsUtc = Date.UTC(wall.year, wall.month - 1, wall.day, wall.hour, wall.minute);
  return wallAsUtc - Math.floor(utcMs / 60_000) * 60_000;
}

const pad = (value) => String(value).padStart(2, '0');

/**
 * A <input type="datetime-local"> value ("2026-11-13T18:00"), read as wall time in the pick-up place's zone,
 * as a UTC instant string. The offset is worked out at a first guess and checked again at the result, so a
 * time next to a daylight-saving change still lands right. Returns null for an empty value.
 */
export function zonedInputToUtc(value, timeZone) {
  if (!value) {
    return null;
  }
  const [datePart, timePart] = value.split('T');
  const [year, month, day] = datePart.split('-').map(Number);
  const [hour, minute] = timePart.split(':').map(Number);
  const wallAsUtc = Date.UTC(year, month - 1, day, hour, minute);
  const firstGuess = wallAsUtc - zoneOffsetMs(timeZone, wallAsUtc);
  const result = wallAsUtc - zoneOffsetMs(timeZone, firstGuess);
  return new Date(result).toISOString();
}

/** A UTC instant as a datetime-local value in the zone ("2026-11-13T18:00"), for the edit form. */
export function utcToZonedInput(instant, timeZone) {
  if (!instant) {
    return '';
  }
  const wall = wallParts(new Date(instant), timeZone);
  return `${wall.year}-${pad(wall.month)}-${pad(wall.day)}T${pad(wall.hour)}:${pad(wall.minute)}`;
}

/** "2026-11-13": the day an instant falls on in the zone. */
export function dayKey(instant, timeZone) {
  const wall = wallParts(new Date(instant), timeZone);
  return `${wall.year}-${pad(wall.month)}-${pad(wall.day)}`;
}

/** The short name of a zone at that instant: "IST", or "GMT+1" where there is no common short name. */
export function zoneShortName(timeZone, instant = new Date()) {
  try {
    const part = new Intl.DateTimeFormat('en-IN', { timeZone, timeZoneName: 'short' })
      .formatToParts(new Date(instant))
      .find((entry) => entry.type === 'timeZoneName');
    return part ? part.value : timeZone;
  } catch {
    return timeZone;
  }
}

/** "6:00 pm" in the zone. */
export function formatTime(instant, timeZone) {
  return new Intl.DateTimeFormat('en-GB', { timeZone, hour: 'numeric', minute: '2-digit', hour12: true })
    .format(new Date(instant))
    .replace(/\s/g, ' ')
    .toLowerCase();
}

/** "Tue 20 Oct" in the zone (with { year: true }: "Tue 20 Oct 2026"). */
export function formatDay(instant, timeZone, { year = false } = {}) {
  const options = { timeZone, weekday: 'short', day: 'numeric', month: 'short' };
  if (year) {
    options.year = 'numeric';
  }
  return new Intl.DateTimeFormat('en-GB', options).format(new Date(instant)).replace(',', '');
}

function addDaysToKey(key, count) {
  const date = new Date(`${key}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + count);
  return date.toISOString().slice(0, 10);
}

/** "today", "tomorrow", "yesterday" or "Tue 20 Oct", seen from now in the zone. */
export function relativeDay(instant, timeZone, now = Date.now()) {
  const day = dayKey(instant, timeZone);
  const today = dayKey(now, timeZone);
  if (day === today) {
    return 'today';
  }
  if (day === addDaysToKey(today, 1)) {
    return 'tomorrow';
  }
  if (day === addDaysToKey(today, -1)) {
    return 'yesterday';
  }
  return formatDay(instant, timeZone);
}

const capitalise = (text) => text.charAt(0).toUpperCase() + text.slice(1);

/** "Today, 6:00 pm", "Tomorrow, 9:00 am" or "Tue 20 Oct, 10:00 am". */
export function formatWhen(instant, timeZone, now = Date.now()) {
  return `${capitalise(relativeDay(instant, timeZone, now))}, ${formatTime(instant, timeZone)}`;
}

/** "6:00 – 8:00 pm", or "6:00 pm – 9:00 am" across the half day, without the day. */
function timeRange(from, until, timeZone) {
  const start = formatTime(from, timeZone);
  const end = formatTime(until, timeZone);
  const [startTime, startPeriod] = start.split(' ');
  const [, endPeriod] = end.split(' ');
  return startPeriod === endPeriod ? `${startTime} – ${end}` : `${start} – ${end}`;
}

/**
 * A pick-up window: "Today 6:00 – 8:00 pm", "Tomorrow 9:00 – 11:00 am", "Tue 20 Oct 6:00 – 8:00 pm"; a window
 * that runs into another day names both: "Today 10:00 pm – Tomorrow 1:00 am".
 */
export function formatWindow(from, until, timeZone, now = Date.now()) {
  const startDay = capitalise(relativeDay(from, timeZone, now));
  if (dayKey(from, timeZone) === dayKey(until, timeZone)) {
    return `${startDay} ${timeRange(from, until, timeZone)}`;
  }
  const endDay = capitalise(relativeDay(until, timeZone, now));
  return `${startDay} ${formatTime(from, timeZone)} – ${endDay} ${formatTime(until, timeZone)}`;
}

/** "ends in 1 h 40 m" while pick-up is open and ends within 3 hours, else null. */
export function endsSoon(from, until, now = Date.now()) {
  const start = new Date(from).getTime();
  const end = new Date(until).getTime();
  const left = end - now;
  if (start > now || left <= 0 || left > 3 * 60 * 60 * 1000) {
    return null;
  }
  const minutes = Math.max(1, Math.round(left / 60_000));
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return hours > 0 ? `ends in ${hours} h ${rest} m` : `ends in ${rest} m`;
}

/** "5 hours ago", "1 hour ago", "12 minutes ago", "just now", or the day for anything older than a week. */
export function timeAgo(instant, timeZone, now = Date.now()) {
  const minutes = Math.round((now - new Date(instant).getTime()) / 60_000);
  if (minutes < 1) {
    return 'just now';
  }
  if (minutes < 60) {
    return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'} ago`;
  }
  const hours = Math.round(minutes / 60);
  if (hours < 24) {
    return `${hours} ${hours === 1 ? 'hour' : 'hours'} ago`;
  }
  const days = Math.round(hours / 24);
  if (days <= 7) {
    return `${days} ${days === 1 ? 'day' : 'days'} ago`;
  }
  return formatDay(instant, timeZone, { year: true });
}
