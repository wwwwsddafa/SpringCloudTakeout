import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'

export default defineConfig({
  plugins: [vue(), vueJsx()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5174,
    host: 'localhost',
    open: '/index-admin.html',
    proxy: {
      '/api': {
        target: 'http://localhost:20001',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      '/ws': {
        target: 'ws://localhost:8086',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist-admin',
    rollupOptions: {
      input: {
        main: fileURLToPath(new URL('./index-admin.html', import.meta.url)),
      },
    },
  },
})