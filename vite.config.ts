import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { copyFileSync } from 'fs'

// Cloudflare Pages SPA: copy index.html as 404.html after build
// so direct URL access (e.g. /result/D1) loads the Vue app
function cloudflarePages404() {
  return {
    name: 'cloudflare-pages-404',
    closeBundle() {
      try {
        copyFileSync('dist/index.html', 'dist/404.html')
        console.log('✅ Copied index.html → 404.html (Cloudflare Pages SPA)')
      } catch (e) {
        console.warn('⚠️ Could not copy index.html to 404.html:', e)
      }
    }
  }
}

export default defineConfig({
  plugins: [vue(), cloudflarePages404()],
  base: './', // Relative paths — works for both file:// (Android assets) and https:// (Cloudflare Pages)
  server: {
    port: 5173,
    host: true
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    minify: true,
    rollupOptions: {
      output: {
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: 'assets/[ext]/[name]-[hash].[ext]'
      }
    }
  },
})
