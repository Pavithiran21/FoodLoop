const fs = require('node:fs');
const path = require('node:path');

// SeedTestRunner makes 400 verified Donors (donor-0001..), 2,000 Receivers (rcv-0001..) and three Admins,
// all @e2e.foodloop.test with E2E_PASSWORD. Every Donor picks up at the same Indiranagar spot
// (12.9719, 77.6412) and every Receiver's home is near it (12.9784, 77.6408).
//
// A test claims the people it needs by creating e2e/.slots/<pool>-<n> with the "wx" flag, which fails
// if the file exists: atomic across Playwright's worker processes. prepare.js clears the folder.

const DOMAIN = '@e2e.foodloop.test';
const POOLS = { donor: { prefix: 'donor', size: 400 }, receiver: { prefix: 'rcv', size: 2000 } };
const ADMINS = [1, 2, 3].map((n) => `admin${n}${DOMAIN}`);
const DONOR_SPOT = { lat: 12.9719, lng: 77.6412 };
const slotsDir = path.join(__dirname, '..', '.slots');

function claim(poolName) {
  const pool = POOLS[poolName];
  fs.mkdirSync(slotsDir, { recursive: true });
  for (let slot = 1; slot <= pool.size; slot += 1) {
    try {
      fs.closeSync(fs.openSync(path.join(slotsDir, `${poolName}-${slot}`), 'wx'));
      return `${pool.prefix}-${String(slot).padStart(4, '0')}${DOMAIN}`;
    } catch (error) {
      if (error.code !== 'EEXIST') throw error;
    }
  }
  throw new Error(`Every ${poolName} slot is taken; run prepare.js again.`);
}

const claimDonor = () => claim('donor');
const claimReceiver = () => claim('receiver');
const claimReceivers = (count) => Array.from({ length: count }, claimReceiver);

module.exports = { ADMINS, DONOR_SPOT, claimDonor, claimReceiver, claimReceivers };
