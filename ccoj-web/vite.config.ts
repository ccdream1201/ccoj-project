import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
const proxy = {
  '/api/': {
    target: 'http://localhost:8080',
    rewrite: (path: string) => path.replace(/^\/api(?=\/)/, ''),
  },
}

export default defineConfig({
  server: { proxy },
  preview: { proxy },
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
