import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// База "./" - собранный dist/ открывается с любого статического сервера и из любой подпапки.
// preserveSymlinks: папка проекта может быть junction (C:\...\Desktop\prog -> D:\prog); без этого
// Vite разрешает файлы в реальный путь и считает их лежащими вне корня проекта.
export default defineConfig({
  plugins: [vue()],
  base: './',
  resolve: { preserveSymlinks: true },
  server: { fs: { strict: false } },
  build: { target: 'es2022', chunkSizeWarningLimit: 1500 },
});
