const { randomBytes } = require('node:crypto');
const { expect } = require('@playwright/test');
const { ADMINS, DONOR_SPOT } = require('./pool');

// A small API client over Playwright's request context. Calls carry X-Test-Now, so a listing's
// pick-up window is in the future on every day the suite runs. Logging in does not: access tokens are
// always checked against the real clock (TestTimeFilter).

// Wednesday 18 Nov 2026, 18:00 in Bengaluru (12:30 UTC): most food is listed in the evening.
const NOW = '2026-11-18T12:30:00Z';

function apiUrl(pathname) {
  return `${process.env.E2E_API_URL}${pathname}`;
}

function client(request, token, now = NOW) {
  const headers = { 'X-Test-Now': now, ...(token ? { Authorization: `Bearer ${token}` } : {}) };
  const call = (method) => (pathname, data) =>
    request.fetch(apiUrl(pathname), { method, headers, ...(data === undefined ? {} : { data }) });
  const api = { get: call('GET'), post: call('POST'), put: call('PUT'), delete: call('DELETE'), token };
  /** The same client "as if it were" another moment. */
  api.at = (moment) => client(request, token, moment);
  return api;
}

/** The JSON body of a response that must have the given status. */
async function json(response, status) {
  const text = await response.text();
  expect(response.status(), text.slice(0, 500)).toBe(status);
  return text ? JSON.parse(text) : null;
}

async function loginAs(request, email, password = process.env.E2E_PASSWORD) {
  const body = await json(await request.post(apiUrl('/auth/login'), { data: { email, password } }), 200);
  return client(request, body.accessToken);
}

const admin = (request, which = 0) => loginAs(request, ADMINS[which]);

/** A listing with a unique title. Default window: 18:30–20:30 in Bengaluru, best before 22:00. */
async function newListing(donor, overrides = {}) {
  const body = {
    title: `Veg biryani ${randomBytes(3).toString('hex')}`,
    quantity: '12 boxes',
    category: 'COOKED_MEAL',
    dietary: ['VEGETARIAN'],
    pickupFrom: '2026-11-18T13:00:00Z',
    pickupUntil: '2026-11-18T15:00:00Z',
    bestBefore: '2026-11-18T16:30:00Z',
    safetyConfirmed: true,
    ...overrides,
  };
  return json(await donor.post('/donor/listings', body), 201);
}

/** Find food around the pool's donor spot. */
async function findFood(receiver, extra = '') {
  return json(await receiver.get(`/listings?lat=${DONOR_SPOT.lat}&lng=${DONOR_SPOT.lng}&radiusKm=5&size=100${extra}`), 200);
}

module.exports = { NOW, apiUrl, client, json, loginAs, admin, newListing, findFood };
