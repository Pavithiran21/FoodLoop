// What the screens print for each value the API sends: UI-UX.md "Words for API values", word for word.

export const CATEGORIES = [
  { value: 'BAKERY', label: 'Bakery' },
  { value: 'COOKED_MEAL', label: 'Cooked meals' },
  { value: 'FRUIT_VEG', label: 'Fruit and veg' },
  { value: 'PACKAGED', label: 'Packaged' },
];

export const DIETARY = [
  { value: 'VEGETARIAN', label: 'Vegetarian', short: 'Veg' },
  { value: 'VEGAN', label: 'Vegan', short: 'Vegan' },
  { value: 'JAIN', label: 'Jain', short: 'Jain' },
  { value: 'HALAL', label: 'Halal', short: 'Halal' },
  { value: 'CONTAINS_EGG', label: 'Contains egg', short: 'Contains egg' },
  { value: 'CONTAINS_NUTS', label: 'Contains nuts', short: 'Contains nuts' },
  { value: 'CONTAINS_GLUTEN', label: 'Contains gluten', short: 'Contains gluten' },
];

/** The four "suitable for" tags offered as Find food filters (the API matches every chosen tag). */
export const DIETARY_FILTERS = DIETARY.slice(0, 4);

export const SORTS = [
  { value: 'CLOSEST', label: 'Closest first' },
  { value: 'ENDS_SOONEST', label: 'Pick-up ends soonest' },
  { value: 'NEWEST', label: 'Newest' },
];

export const WHEN = [
  { value: '', label: 'Any time' },
  { value: 'NEXT_2_HOURS', label: 'Next 2 hours' },
  { value: 'TODAY', label: 'Today' },
  { value: 'TOMORROW', label: 'Tomorrow' },
];

const STATUS_LABELS = {
  LISTED: 'Listed',
  CLAIMED: 'Claimed',
  READY: 'Ready',
  PICKED_UP: 'Picked up',
  COMPLETED: 'Completed',
  EXPIRED: 'Expired',
  CANCELLED: 'Cancelled',
  WITHDRAWN: 'Withdrawn',
  DONE_TODAY: 'Done today',
};

const STATUS_CLASSES = {
  LISTED: 's-listed',
  CLAIMED: 's-claimed',
  READY: 's-ready',
  PICKED_UP: 's-picked',
  COMPLETED: 's-done',
  DONE_TODAY: 's-done',
  EXPIRED: 's-expired',
  WITHDRAWN: 's-expired',
  CANCELLED: 's-cancel',
};

export const statusLabel = (status) => STATUS_LABELS[status] ?? status;
export const statusClass = (status) => STATUS_CLASSES[status] ?? 's-expired';

export const LISTING_REPORT_REASONS = [
  { value: 'SPOILED_OR_UNSAFE', label: 'Food was spoiled or unsafe' },
  { value: 'NOT_AS_LISTED', label: 'Not what was listed' },
  { value: 'DONOR_NOT_THERE', label: "Donor wasn't there" },
  { value: 'OTHER', label: 'Something else' },
];

export const RECEIVER_REPORT_REASONS = [
  { value: 'NO_SHOW', label: "Didn't show up" },
  { value: 'RUDE_OR_UNSAFE', label: 'Rude or unsafe at pick-up' },
  { value: 'OTHER', label: 'Something else' },
];

const REASON_LABELS = Object.fromEntries(
  [...LISTING_REPORT_REASONS, ...RECEIVER_REPORT_REASONS].map((reason) => [reason.value, reason.label]),
);

export const reportReasonLabel = (reason) => REASON_LABELS[reason] ?? reason;

export const REPORT_STATUSES = [
  { value: 'OPEN', label: 'Open', className: 's-claimed' },
  { value: 'DISMISSED', label: 'Dismissed', className: 's-expired' },
  { value: 'ACTIONED', label: 'Actioned · suspended', className: 's-cancel' },
];

export const reportStatus = (status) => REPORT_STATUSES.find((entry) => entry.value === status);

export const DONOR_STATUSES = [
  { value: 'WAITING', label: 'Waiting for verification', tab: 'Waiting', countKey: 'waiting' },
  { value: 'VERIFIED', label: 'Verified donor', tab: 'Verified', countKey: 'verified' },
  { value: 'NOT_VERIFIED', label: 'Not verified', tab: 'Not verified', countKey: 'notVerified' },
];

export const ACCOUNT_STATUSES = [
  { value: 'ACTIVE', label: 'Active', className: 's-listed' },
  { value: 'WAITING', label: 'Waiting for verification', className: 's-claimed' },
  { value: 'NOT_VERIFIED', label: 'Not verified', className: 's-cancel' },
  { value: 'SUSPENDED', label: 'Suspended', className: 's-cancel' },
];

export const accountStatus = (status) => ACCOUNT_STATUSES.find((entry) => entry.value === status);

export const ROLES = [
  { value: 'DONOR', label: 'Donor' },
  { value: 'RECEIVER', label: 'Receiver' },
  { value: 'ADMIN', label: 'Admin' },
];

export const roleLabel = (role) => ROLES.find((entry) => entry.value === role)?.label ?? role;

/** "Bakery · Veg" on a ticket: the category and the first dietary tag. */
export function ticketTag(category, dietary = []) {
  const categoryLabel = CATEGORIES.find((entry) => entry.value === category)?.label ?? category;
  const first = DIETARY.find((entry) => dietary.includes(entry.value));
  return first ? `${categoryLabel} · ${first.short}` : categoryLabel;
}

export const categoryLabel = (category) => CATEGORIES.find((entry) => entry.value === category)?.label ?? category;

/** "Vegetarian, contains gluten" in the order of the form. */
export function dietaryText(dietary = []) {
  const labels = DIETARY.filter((entry) => dietary.includes(entry.value)).map((entry, index) =>
    index === 0 ? entry.label : entry.label.charAt(0).toLowerCase() + entry.label.slice(1),
  );
  return labels.length ? labels.join(', ') : 'None given';
}

/** "MN" from "Meera N.", "CC" from "Crumb & Co. Bakery". */
export function initials(name = '') {
  const words = name.replace(/[^\p{L}\p{N}\s]/gu, ' ').split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return '?';
  }
  const letters = words.length === 1 ? words[0].slice(0, 2) : words[0][0] + words[1][0];
  return letters.toUpperCase();
}

/** "R-118": the short handle admins use for a report (the API has only the id). */
export const reportHandle = (id) => `R-${id}`;

/** "Udupi Corner (donor)": who a report is about. */
export function partyText(party) {
  return `${party.name} (${party.role === 'DONOR' ? 'donor' : 'receiver'})`;
}
