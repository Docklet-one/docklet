import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'dist')
const port = Number(process.env.DOCKLET_DIST_PORT) || 8791
const MIME = {
  '.pkg': 'application/octet-stream',
  '.apk': 'application/vnd.android.package-archive',
  '.json': 'application/json; charset=utf-8',
  '.md': 'text/plain; charset=utf-8',
  '.png': 'image/png',
  '.html': 'text/html; charset=utf-8',
}

http.createServer((req, res) => {
  const url = new URL(req.url || '/', 'http://localhost')
  const relative = decodeURIComponent(url.pathname.replace(/^\/+/, '')) || 'index.json'
  const file = path.resolve(root, relative)
  if (!file.startsWith(root + path.sep) && file !== root) {
    res.writeHead(403).end('forbidden')
    return
  }
  fs.readFile(file, (error, data) => {
    if (error) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }).end('not found')
      return
    }
    const ext = path.extname(file)
    const headers = {
      'Content-Type': MIME[ext] || 'application/octet-stream',
      'Cache-Control': 'no-store',
      'Access-Control-Allow-Origin': '*',
      'Content-Length': data.length,
    }
    if (ext === '.apk' || ext === '.pkg') {
      headers['Content-Disposition'] = `attachment; filename="${path.basename(file)}"`
    }
    res.writeHead(200, headers)
    res.end(data)
  })
}).listen(port, '0.0.0.0', () => {
  console.log(`docklet dist: http://localhost:${port}`)
  for (const list of Object.values(os.networkInterfaces())) {
    for (const item of list || []) {
      if (item && item.family === 'IPv4' && !item.internal) console.log(`lan: http://${item.address}:${port}`)
    }
  }
})
