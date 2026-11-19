/**
 * A stand-in for OpenStreetMap Nominatim's /search, so a run never calls the real service (its usage
 * policy allows one request a second at most). make-env.js points NOMINATIM_BASE_URL here.
 *
 * GET /search?q=... answers with one fixed Bengaluru place per known word, or none:
 *   "indiranagar" -> Indiranagar, "koramangala" -> Koramangala, anything else -> [].
 * Start it on its own with: node e2e/mocks/nominatim-stub.js (port 8089)
 */
const http = require('node:http');

const PORT = Number(process.env.NOMINATIM_STUB_PORT ?? 8089);

const PLACES = {
  indiranagar: { lat: '12.9719', lon: '77.6412', suburb: 'Indiranagar', display_name: 'Indiranagar, Bengaluru, Karnataka, 560038, India' },
  koramangala: { lat: '12.9352', lon: '77.6245', suburb: 'Koramangala', display_name: 'Koramangala, Bengaluru, Karnataka, 560034, India' },
};

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  res.setHeader('Content-Type', 'application/json');
  if (url.pathname === '/__health') return res.end('{"ok":true}');
  if (url.pathname !== '/search') {
    res.statusCode = 404;
    return res.end('[]');
  }
  const query = (url.searchParams.get('q') ?? '').toLowerCase();
  const hits = Object.entries(PLACES)
    .filter(([word]) => query.includes(word))
    .map(([, place]) => ({
      display_name: place.display_name,
      lat: place.lat,
      lon: place.lon,
      address: { suburb: place.suburb, city: 'Bengaluru', state: 'Karnataka', country: 'India' },
    }));
  return res.end(JSON.stringify(hits));
});

server.listen(PORT, '127.0.0.1', () => console.log(`Nominatim stub on http://127.0.0.1:${PORT}`));
