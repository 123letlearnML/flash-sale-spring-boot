import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Proxy /api sang Spring Boot => frontend và backend cùng origin, không cần cấu hình CORS.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
