import { spawn } from 'node:child_process'
import { createReadStream, existsSync, statSync } from 'node:fs'
import { createServer, request as httpRequest } from 'node:http'
import { extname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendDir = fileURLToPath(new URL('../', import.meta.url))
const distDir = resolve(frontendDir, 'dist')
const playwrightCli = fileURLToPath(new URL('../node_modules/@playwright/test/cli.js', import.meta.url))
const host = '127.0.0.1'
const port = 3000

if (!existsSync(resolve(distDir, 'index.html'))) {
  throw new Error('缺少 dist/index.html；请先执行 npm run build')
}

const server = createServer((incoming, outgoing) => {
  const url = new URL(incoming.url || '/', `http://${host}:${port}`)
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/uploads/')) {
    proxyToBackend(incoming, outgoing, url)
    return
  }
  serveFrontend(outgoing, url.pathname)
})

await new Promise((resolveReady, reject) => {
  server.once('error', reject)
  server.listen(port, host, resolveReady)
})

let exitCode = 1
try {
  exitCode = await run(process.execPath, [playwrightCli, 'test', ...process.argv.slice(2)])
} finally {
  await new Promise((resolveClose, reject) => {
    server.close((error) => error ? reject(error) : resolveClose())
  })
}

process.exitCode = exitCode

function serveFrontend(response, pathname) {
  let candidate
  try {
    candidate = resolve(distDir, `.${decodeURIComponent(pathname)}`)
  } catch {
    writeText(response, 400, 'Bad request')
    return
  }

  const withinDist = candidate === distDir || candidate.startsWith(distDir + sep)
  if (!withinDist) {
    writeText(response, 403, 'Forbidden')
    return
  }

  const file = existsSync(candidate) && statSync(candidate).isFile()
    ? candidate
    : resolve(distDir, 'index.html')
  response.writeHead(200, {
    'Content-Type': contentType(file),
    'Cache-Control': 'no-store',
  })
  createReadStream(file).pipe(response)
}

function proxyToBackend(incoming, outgoing, url) {
  const proxy = httpRequest({
    hostname: '127.0.0.1',
    port: 8080,
    path: url.pathname + url.search,
    method: incoming.method,
    headers: { ...incoming.headers, host: '127.0.0.1:8080' },
  }, (backendResponse) => {
    outgoing.writeHead(backendResponse.statusCode || 502, backendResponse.headers)
    backendResponse.pipe(outgoing)
  })
  proxy.on('error', (error) => {
    if (!outgoing.headersSent) {
      outgoing.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
    }
    outgoing.end(JSON.stringify({ code: 502, message: `测试后端不可用：${error.message}`, data: null }))
  })
  incoming.pipe(proxy)
}

function run(command, args) {
  return new Promise((resolveRun, reject) => {
    const child = spawn(command, args, {
      cwd: frontendDir,
      stdio: 'inherit',
      windowsHide: true,
    })
    child.once('error', reject)
    child.once('exit', (code, signal) => {
      if (signal) reject(new Error(`Playwright 被信号 ${signal} 终止`))
      else resolveRun(code ?? 1)
    })
  })
}

function contentType(file) {
  return ({
    '.css': 'text/css; charset=utf-8',
    '.gif': 'image/gif',
    '.html': 'text/html; charset=utf-8',
    '.ico': 'image/x-icon',
    '.jpeg': 'image/jpeg',
    '.jpg': 'image/jpeg',
    '.js': 'text/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.png': 'image/png',
    '.svg': 'image/svg+xml',
    '.webp': 'image/webp',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
  })[extname(file).toLowerCase()] || 'application/octet-stream'
}

function writeText(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8' })
  response.end(body)
}
