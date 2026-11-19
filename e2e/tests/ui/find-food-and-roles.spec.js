const { test, expect } = require('@playwright/test');
const { NOW, loginAs, newListing } = require('../../helpers/api');
const { waitForMail } = require('../../helpers/mail');
const { claimDonor, claimReceiver } = require('../../helpers/pool');

// The browser runs on the real clock, but test listings sit in a pinned evening window. So only the
// Find food calls get X-Test-Now (the app's log-in must stay on the real clock: tokens are checked
// against it), and map tiles are answered with an empty image so a run never loads OpenStreetMap.
test.beforeEach(async ({ page }) => {
  await page.route('**/api/listings**', (route) => route.continue({ headers: { ...route.request().headers(), 'x-test-now': NOW } }));
  await page.route('https://tile.openstreetmap.org/**', (route) => route.fulfill({ status: 204, body: '' }));
});

async function logIn(page, email, password = process.env.E2E_PASSWORD) {
  await page.goto('/');
  await page.getByLabel('Email').fill(email);
  await page.getByRole('textbox', { name: 'Password' }).fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).not.toHaveURL(/127\.0\.0\.1:\d+\/$/);
}

test('a Receiver finds a listing near home and switches to Map: one marker per spot, every listing in its popup', async ({ page, request }) => {
  const listing = await newListing(await loginAs(request, claimDonor()));
  const found = page.waitForResponse((response) => /\/api\/listings\?/.test(response.url()) && response.ok());
  await logIn(page, claimReceiver());
  await expect(page).toHaveURL(/\/home/);
  await expect(page.getByRole('link', { name: new RegExp(listing.title) })).toBeVisible();
  const { items } = await (await found).json();
  const tickets = await page.locator('.tickets > a.ticket').count();
  expect(tickets).toBe(items.length);

  // Listings round to about 1 km, so several can share a spot: one marker each, in first-seen order.
  const spots = [...new Set(items.map((item) => `${item.approxLat},${item.approxLng}`))];
  const atFirstSpot = items.filter((item) => `${item.approxLat},${item.approxLng}` === spots[0]);

  await page.getByRole('button', { name: 'Map' }).click();
  await expect(page).toHaveURL(/view=map/);
  const map = page.getByRole('region', { name: 'Map of food near you' });
  await expect(map).toBeVisible();
  await expect(map.locator('path.food-marker')).toHaveCount(spots.length);
  await expect(map.getByText('OpenStreetMap')).toBeVisible();
  await expect(map.getByText(/Spots are approximate/)).toBeVisible();

  await map.locator('path.food-marker').first().click({ force: true });
  await expect(map.locator('.leaflet-popup a[href^="/listings/"]')).toHaveCount(atFirstSpot.length);

  await page.getByRole('button', { name: 'List' }).click();
  await expect(page).not.toHaveURL(/view=map/);
  await expect(page.locator('.tickets > a.ticket')).toHaveCount(tickets);
});

test('a Donor lands on their board, and the demo Admin reaches Verify donors', async ({ page }) => {
  await logIn(page, claimDonor());
  await expect(page).toHaveURL(/\/donor$/);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Test Kitchen');

  await page.context().clearCookies();
  await page.goto('/demo');
  await page.getByRole('button', { name: /Log in as Admin/ }).click();
  await expect(page).not.toHaveURL(/\/demo$/);
  await page.goto('/admin/donors');
  await expect(page.getByRole('heading', { name: 'Verify donors' })).toBeVisible();
});

test('forgot password: the emailed link sets a new password that then logs in', async ({ page }) => {
  test.setTimeout(90_000);
  const email = claimReceiver();
  await page.goto('/forgot-password');
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', { name: 'Send reset link' }).click();

  const mail = await waitForMail({ to: email, subject: /Reset your FoodLoop password/ });
  const link = mail.html.match(/href="([^"]*\/reset-password\?token=[^"]+)"/)?.[1];
  expect(link, 'reset link in the email').toBeTruthy();
  const { pathname, search } = new URL(link.replace(/&amp;/g, '&'));
  await page.goto(pathname + search);

  const newPassword = 'Fresh-password-42';
  await page.getByRole('textbox', { name: 'New password', exact: true }).fill(newPassword);
  await page.getByRole('textbox', { name: 'Confirm' }).fill(newPassword);
  await page.getByRole('button', { name: 'Save new password' }).click();
  // Wait for the success message, not for the button to vanish: while the request is in flight
  // the button reads "Saving…", so it would vanish before the new password is stored.
  await expect(page.getByText('Your password was changed.')).toBeVisible();

  await logIn(page, email, newPassword);
  await expect(page).toHaveURL(/\/home/);
});
