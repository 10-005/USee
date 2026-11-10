import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': { target: 'https://k15d106.p.ssafy.io', changeOrigin: true, ws: true } },
  },
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  test: {
    environment: 'jsdom',
    env: { VITE_DEMO_MODE: 'true' },
    setupFiles: ['./src/test/setup.ts'],
    clearMocks: true,
    restoreMocks: true,
  },
});
