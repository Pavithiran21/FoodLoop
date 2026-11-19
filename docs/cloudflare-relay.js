// Optional relay for OpenStreetMap Nominatim, a Cloudflare Worker (free plan).
//
// Free Render shares one outbound address among many apps, and Nominatim refuses that shared address,
// so FoodLoop's address search answers "Address search isn't available right now". This Worker makes the
// same GET request from Cloudflare instead and passes the JSON through. It forwards only /search, only
// for GET, only when the secret in the address matches, and Cloudflare caches each answer for a day
// (Nominatim's usage policy asks for caching and light use).
//
// Deploy: Cloudflare dashboard > Workers & Pages > Create Worker > paste this file > Deploy.
// Then set, on the host that runs FoodLoop:
//   NOMINATIM_BASE_URL=https://<your-worker>.workers.dev/<secret>/nominatim
// Use your own long random string as the secret, and never commit the real one.

const TOKEN = 'replace-with-a-long-random-string';

export default {
  async fetch(request) {
    const url = new URL(request.url);
    const [, token, route, ...rest] = url.pathname.split('/');
    if (request.method !== 'GET' || token !== TOKEN || route !== 'nominatim' || rest.length !== 1 || rest[0] !== 'search') {
      return new Response('Not found', { status: 404 });
    }

    const upstream = await fetch('https://nominatim.openstreetmap.org/search' + url.search, {
      headers: { 'User-Agent': 'FoodLoop-relay/1.0 (portfolio demo)', Accept: 'application/json' },
      cf: { cacheTtl: 86400, cacheEverything: true },
    });
    return new Response(upstream.body, {
      status: upstream.status,
      headers: { 'Content-Type': 'application/json', 'Cache-Control': 'public, max-age=86400' },
    });
  },
};
