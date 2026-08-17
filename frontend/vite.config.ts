import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const gatewayProxy = {
  target: 'http://localhost:8080',
  changeOrigin: true,
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': gatewayProxy,
      '/actuator': gatewayProxy,
    },
  },
  preview: {
    port: 4173,
    strictPort: true,
    proxy: {
      '/api': gatewayProxy,
      '/actuator': gatewayProxy,
    },
  },
})
