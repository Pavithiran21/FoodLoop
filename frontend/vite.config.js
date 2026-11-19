import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// The dev server forwards /api to the backend, so the browser sees one origin: the refresh cookie
// fl_refresh (SameSite=Strict, path /api/auth) is sent with no CORS set-up.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:4000',
    },
  },
});
