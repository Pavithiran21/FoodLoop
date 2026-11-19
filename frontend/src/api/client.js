// The one place that talks to the backend. Every call goes to /api on the same origin (Vite forwards it to the
// backend), so the HttpOnly refresh cookie fl_refresh (path /api/auth) is sent with the refresh call.
const BASE_URL = '/api';

/**
 * An error answer in the shared format { error: { code, message, details: [{ field, message }], requestId } }.
 * status is 0 when the server could not be reached at all.
 */
export class ApiError extends Error {
  constructor(status, code, message, details = [], requestId = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
    this.requestId = requestId;
  }

  /** The message of one entry in details, for example detail('pickupUntil'). */
  detail(field) {
    return this.details.find((entry) => entry.field === field)?.message ?? null;
  }
}

// The access token lives only in memory (never localStorage, where an injected script could read it).
// After a reload, POST /auth/refresh with the cookie gets a new one.
let accessToken = null;
let refreshPromise = null;
let onSessionEnded = () => {};

export function setAccessToken(token) {
  accessToken = token;
}

/** Called when a logged-in request gets 401 and the session can't be refreshed. */
export function setSessionEndedHandler(handler) {
  onSessionEnded = handler;
}

async function send(path, { method = 'GET', body, form, auth = true, signal } = {}) {
  const headers = {};
  let payload;
  if (form !== undefined) {
    payload = form; // FormData: the browser sets the multipart boundary itself.
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  if (auth && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  try {
    return await fetch(BASE_URL + path, { method, headers, body: payload, credentials: 'same-origin', signal });
  } catch (error) {
    if (signal?.aborted) {
      throw error;
    }
    throw new ApiError(0, 'NETWORK_ERROR', "Couldn't reach FoodLoop. Check your connection and try again.");
  }
}

/** "Too many attempts. Try again in N minutes." with N from Retry-After (seconds), rounded up. */
function tooManyMessage(response) {
  const seconds = Number(response.headers.get('Retry-After'));
  const minutes = Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds / 60) : 1;
  return `Too many attempts. Try again in ${minutes} ${minutes === 1 ? 'minute' : 'minutes'}.`;
}

async function toApiError(response) {
  let body = null;
  try {
    body = await response.json();
  } catch {
    // Not JSON, for example the dev proxy's page while the backend is down.
  }
  const error = body?.error;
  let message = error?.message ?? "Something went wrong. It's not you. Try again in a moment.";
  if (response.status === 429) {
    message = tooManyMessage(response);
  }
  return new ApiError(
    response.status,
    error?.code ?? (response.status >= 500 ? 'SERVER_ERROR' : 'UNKNOWN_ERROR'),
    message,
    error?.details ?? [],
    error?.requestId ?? response.headers.get('X-Request-Id'),
  );
}

/**
 * Gets a new access token with the refresh cookie: { user, accessToken }, or null with no valid session.
 * Only one refresh runs at a time: the backend rotates the cookie on every use, so a second refresh sent in
 * parallel would carry the old cookie and be refused.
 */
export function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const response = await send('/auth/refresh', { method: 'POST', auth: false });
        if (!response.ok) {
          accessToken = null;
          return null;
        }
        const session = await response.json();
        accessToken = session.accessToken;
        return session;
      } catch {
        return null;
      } finally {
        refreshPromise = null;
      }
    })();
  }
  return refreshPromise;
}

/**
 * Sends a request and returns the raw Response once it is OK.
 * A logged-in request that gets 401 (usually the 15-minute token ran out) refreshes once and retries; if that
 * fails too, the session has ended and the app logs out. Requests with auth: false (log in, reset password...)
 * never do this, so a wrong password stays a normal error. WRONG_PASSWORD is a 400 on purpose, so it never
 * logs anyone out.
 */
async function sendWithSession(path, options) {
  const auth = options.auth ?? true;
  let response = await send(path, options);

  if (response.status === 401 && auth) {
    const session = await refreshSession();
    if (session) {
      response = await send(path, options);
    }
    if (!session || response.status === 401) {
      onSessionEnded();
      throw new ApiError(401, 'SESSION_ENDED', 'Your session has ended. Log in again.');
    }
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  return response;
}

/** JSON in, JSON out; undefined when the answer has no body (204, or a 201 for a report). */
export async function apiRequest(path, options = {}) {
  const response = await sendWithSession(path, options);
  const text = await response.text();
  return text ? JSON.parse(text) : undefined;
}

/** A file from the API (the listing photo, which needs the Bearer token, so an <img src> can't load it). */
export async function apiBlob(path, options = {}) {
  const response = await sendWithSession(path, options);
  return response.blob();
}

/** "?a=1&b=x&b=y" from an object, leaving out empty values; an array becomes a repeated parameter. */
export function toQuery(params) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (Array.isArray(value)) {
      value.forEach((item) => search.append(key, String(item)));
    } else if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}
