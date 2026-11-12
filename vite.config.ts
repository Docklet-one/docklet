import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const now = new Date()
const pad = (value: number) => String(value).padStart(2, '0')
const buildId = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}`

export default defineConfig({
  plugins: [react()],
  define: {
    __BUILD_ID__: JSON.stringify(buildId),
  },
  build: {
    outDir: 'dist',
    target: 'chrome83',
  },
})
