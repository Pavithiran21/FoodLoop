const { test, expect } = require('@playwright/test');
const { apiUrl, findFood, json, loginAs, newListing } = require('../../helpers/api');
const { claimDonor, claimReceiver, claimReceivers } = require('../../helpers/pool');

// The signature feature: a claim lifecycle that is safe under concurrency. Claiming is one conditional
// UPDATE (only "1 row changed" wins) backed by a UNIQUE index on the active claim, and every later move
// goes through the ClaimMove state machine: the wrong person gets 403, the wrong state 422.

test.describe('claiming', () => {
  test('ten receivers claim one listing at the same moment: exactly one 201, nine 409 CLAIM_LOST', async ({ request }) => {
    const donor = await loginAs(request, claimDonor());
    const listing = await newListing(donor);
    const receivers = await Promise.all(claimReceivers(10).map((email) => loginAs(request, email)));

    const responses = await Promise.all(receivers.map((api) => api.post(`/listings/${listing.id}/claim`)));
    const statuses = responses.map((response) => response.status());
    expect(statuses.filter((status) => status === 201)).toHaveLength(1);
    expect(statuses.filter((status) => status === 409)).toHaveLength(9);
    const losers = await Promise.all(responses.filter((r) => r.status() === 409).map((r) => r.json()));
    expect(new Set(losers.map((body) => body.error.code))).toEqual(new Set(['CLAIM_LOST']));
  });

  test('the full lifecycle: claimed -> ready -> picked up -> completed, each by the right person', async ({ request }) => {
    const donor = await loginAs(request, claimDonor());
    const receiver = await loginAs(request, claimReceiver());
    const listing = await newListing(donor);

    let claim = await json(await receiver.post(`/listings/${listing.id}/claim`), 201);
    expect(claim.status).toBe('CLAIMED');
    claim = await json(await donor.post(`/claims/${claim.id}/ready`), 200);
    expect(claim.status).toBe('READY');
    claim = await json(await donor.post(`/claims/${claim.id}/picked-up`), 200);
    expect(claim.status).toBe('PICKED_UP');
    claim = await json(await receiver.post(`/claims/${claim.id}/complete`), 200);
    expect(claim.status).toBe('COMPLETED');
  });

  test('the wrong person gets 403 and the wrong state 422 (ILLEGAL_TRANSITION)', async ({ request }) => {
    const donor = await loginAs(request, claimDonor());
    const receiver = await loginAs(request, claimReceiver());
    const listing = await newListing(donor);
    const claim = await json(await receiver.post(`/listings/${listing.id}/claim`), 201);

    expect((await receiver.post(`/claims/${claim.id}/ready`)).status()).toBe(403);
    expect((await json(await donor.post(`/claims/${claim.id}/picked-up`), 422)).error.code).toBe('ILLEGAL_TRANSITION');
    expect((await donor.post(`/claims/${claim.id}/complete`)).status()).toBe(403);
  });

  test('cancelling a claim lists the food again for someone else', async ({ request }) => {
    const donor = await loginAs(request, claimDonor());
    const [first, second] = await Promise.all([loginAs(request, claimReceiver()), loginAs(request, claimReceiver())]);
    const listing = await newListing(donor);

    const claim = await json(await first.post(`/listings/${listing.id}/claim`), 201);
    expect((await findFood(second)).items.some((item) => item.id === listing.id)).toBe(false);
    await json(await first.post(`/claims/${claim.id}/cancel`), 200);
    expect((await findFood(second)).items.some((item) => item.id === listing.id)).toBe(true);
    await json(await second.post(`/listings/${listing.id}/claim`), 201);
  });

  test('a donor cannot claim food and a receiver cannot list it (403); a stranger\'s claim answers 404', async ({ request }) => {
    const donor = await loginAs(request, claimDonor());
    const [receiver, stranger] = await Promise.all([loginAs(request, claimReceiver()), loginAs(request, claimReceiver())]);
    const listing = await newListing(donor);

    expect((await donor.post(`/listings/${listing.id}/claim`)).status()).toBe(403);
    // A complete, valid listing: only the role is wrong.
    const valid = { title: 'Rotis', quantity: '20', category: 'COOKED_MEAL', dietary: [], pickupFrom: '2026-11-18T13:00:00Z', pickupUntil: '2026-11-18T15:00:00Z', bestBefore: '2026-11-18T16:00:00Z', safetyConfirmed: true };
    expect((await receiver.post('/donor/listings', valid)).status()).toBe(403);
    const claim = await json(await receiver.post(`/listings/${listing.id}/claim`), 201);
    expect((await stranger.get(`/claims/${claim.id}`)).status()).toBe(404);
  });
});

test.describe('jobs', () => {
  test('expiry closes an unclaimed listing and an open claim once the pick-up window ends, exactly once', async ({ request }) => {
    // Its own early window (17:00–17:30 in Bengaluru), so the run at 12:05 UTC closes only this test's food.
    const donor = (await loginAs(request, claimDonor())).at('2026-11-18T11:00:00Z');
    const receiver = (await loginAs(request, claimReceiver())).at('2026-11-18T11:00:00Z');
    const window = { pickupFrom: '2026-11-18T11:30:00Z', pickupUntil: '2026-11-18T12:00:00Z', bestBefore: '2026-11-18T12:30:00Z' };
    const unclaimed = await newListing(donor, window);
    const claimed = await newListing(donor, window);
    const claim = await json(await receiver.post(`/listings/${claimed.id}/claim`), 201);

    const run = (at) => request.post(apiUrl('/internal/jobs/expiry'), { data: { at }, headers: { 'X-Job-Secret': process.env.JOB_TRIGGER_SECRET } });
    expect((await request.post(apiUrl('/internal/jobs/expiry'), { data: { at: '2026-11-18T12:05:00Z' } })).status()).toBe(401);
    const first = await json(await run('2026-11-18T12:05:00Z'), 200);
    expect(first.itemsDone).toBeGreaterThanOrEqual(2);
    const again = await Promise.all([run('2026-11-18T12:06:00Z'), run('2026-11-18T12:06:00Z')]);
    for (const response of again) expect(response.status()).toBe(200);

    expect((await json(await donor.get(`/donor/listings/${unclaimed.id}`), 200)).status).toBe('EXPIRED');
    expect((await json(await receiver.get(`/claims/${claim.id}`), 200)).status).toBe('EXPIRED');
  });
});

test.describe('find food and the map', () => {
  test('cards carry rounded coordinates (about 1 km) for the map, never the exact pick-up spot', async ({ request }) => {
    const donor = await loginAs(request, claimDonor());
    const receiver = await loginAs(request, claimReceiver());
    const listing = await newListing(donor);

    const card = (await findFood(receiver)).items.find((item) => item.id === listing.id);
    // The pool's donors pick up at 12.9719, 77.6412.
    expect(card).toMatchObject({ approxLat: 12.97, approxLng: 77.64, area: 'Indiranagar' });
    expect(JSON.stringify(card)).not.toContain('12.9719');
    expect(JSON.stringify(card)).not.toContain('100 Feet Road');
    expect(card.distanceKm).toBeLessThan(1);

    const detail = await json(await receiver.get(`/listings/${listing.id}?lat=12.9719&lng=77.6412`), 200);
    expect(detail.listing).toMatchObject({ approxLat: 12.97, approxLng: 77.64 });
  });

  test('filters narrow by category and dietary tag', async ({ request }) => {
    const donor = await loginAs(request, claimDonor());
    const receiver = await loginAs(request, claimReceiver());
    const bread = await newListing(donor, { title: `Sourdough ${Date.now()}`, category: 'BAKERY', dietary: ['VEGAN'] });

    const bakery = await findFood(receiver, '&category=BAKERY&dietary=VEGAN');
    expect(bakery.items.some((item) => item.id === bread.id)).toBe(true);
    expect(bakery.items.every((item) => item.category === 'BAKERY' && item.dietary.includes('VEGAN'))).toBe(true);
    const meals = await findFood(receiver, '&category=COOKED_MEAL');
    expect(meals.items.some((item) => item.id === bread.id)).toBe(false);
  });
});
