import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev: `npm run dev` serves the SPA on :5173 and proxies /api to the Quarkus
// backend on :8080, so the browser sees a single origin and no CORS setup is
// needed.
//
// Build: `npm run build` writes straight into the Quarkus static-resource
// directory (META-INF/resources under target/classes). `mvn package` runs this
// automatically; the output ends up inside the runnable jar.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Vite 6 only answers requests whose Host header it recognises (a guard
    // against DNS-rebinding attacks). `true` accepts any hostname, so the dev
    // server can be reached through an IP address or a reverse proxy instead of
    // only via localhost. Narrow this to a list of hostnames for a shared
    // server.
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../target/classes/META-INF/resources',
    emptyOutDir: true,
  },
})
