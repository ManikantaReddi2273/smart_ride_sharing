import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      }
    }
  },
  preview: {
    port: 5173,
    host: true
  },
  optimizeDeps: {
    exclude: ['jspdf'] // Exclude from pre-bundling to avoid import analysis errors
  },
  define: {
    // Polyfill for Node.js global variable (required by sockjs-client)
    global: 'globalThis',
  },
})
