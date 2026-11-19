// Turning API errors into what the screens show. On any 4xx the screen shows the backend's own message (the
// backend writes them to match UI-UX.md); field details go next to their fields.

/**
 * Puts each `details` entry of a 400 next to its field with React Hook Form's setError. `fieldMap` renames an API
 * field to a form field (for example { pickupLat: 'pickupAddress' }). Returns true when at least one field got its
 * message, so the caller knows whether a general message is still needed.
 */
export function applyFieldErrors(error, setError, knownFields, fieldMap = {}) {
  let placed = false;
  (error?.details ?? []).forEach(({ field, message }) => {
    const name = fieldMap[field] ?? field;
    if (knownFields.includes(name)) {
      setError(name, { type: 'server', message }, { shouldFocus: !placed });
      placed = true;
    }
  });
  return placed;
}

/** The message to show for an error that isn't about one field. */
export function errorMessage(error) {
  if (!error) {
    return null;
  }
  if (error.status === 0 || error.status >= 500) {
    return error.message || "Something went wrong. It's not you. Try again in a moment.";
  }
  return error.message;
}

export const isNotFound = (error) => error?.status === 404;
