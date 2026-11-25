import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'

// 构建产物输出到后端 static 目录，由 Spring Boot 统一提供（http://localhost:8080）
export default defineConfig({
  plugins: [vue()],
  base: './',
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/uploads': 'http://localhost:8080'
    }
  },
  build: {
    outDir: '../phoebe-web/src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 2000
  }
})
