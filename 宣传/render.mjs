import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

// Deterministic offline renderer. A new hidden Chromium instance only renders
// local files; no existing browser, user tab, or remote page is touched.
const here = path.dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const opts = {};
for (let i = 2; i < process.argv.length; i++) {
  const arg = process.argv[i];
  if (!arg.startsWith('--')) throw new Error(`Unexpected argument: ${arg}`);
  const key = arg.slice(2);
  if (key === 'help') opts.help = true;
  else opts[key] = process.argv[++i];
}
if (opts.help) {
  console.log('node render.mjs [--html film.html] [--out MDmeow-promo-silent.mp4] [--duration 60] [--fps 30] [--start 0] [--batch 8] [--audio music.wav] [--snapshot 12] [--snapshot-out frame.jpg] [--crf 18] [--preset medium]');
  process.exit(0);
}
const fps = Number(opts.fps ?? 30);
const duration = Number(opts.duration ?? 60);
const start = Number(opts.start ?? 0);
const batch = Number(opts.batch ?? 8);
if (!(fps > 0 && duration > 0 && start >= 0 && batch >= 1 && batch <= 32)) {
  throw new Error('Invalid fps, duration, start, or batch.');
}
const input = path.resolve(here, opts.html ?? 'film.html');
const output = path.resolve(here, opts.out ?? 'miku-camera-promo.mp4');
if (!fs.existsSync(input)) throw new Error(`Animation HTML not found: ${input}`);
fs.mkdirSync(path.dirname(output), { recursive: true });

function findModule() {
  const modules = [process.env.PLAYWRIGHT_DIR, 'C:/Users/mamin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright', 'playwright'].filter(Boolean);
  for (const candidate of modules) {
    try { return require(candidate); } catch {}
  }
  throw new Error('Playwright unavailable. Set PLAYWRIGHT_DIR to its package directory.');
}
function findFfmpeg() {
  if (opts.ffmpeg || process.env.FFMPEG_PATH) return opts.ffmpeg || process.env.FFMPEG_PATH;
  const siblingBinary = path.resolve(here, '../../MDmeow/宣传动画/.tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe');
  if (fs.existsSync(siblingBinary)) return siblingBinary;
  const binaryDir = path.join(here, '.tools', 'imageio_ffmpeg', 'binaries');
  if (fs.existsSync(binaryDir)) {
    const match = fs.readdirSync(binaryDir).find(name => /^ffmpeg.*\.exe$/i.test(name));
    if (match) return path.join(binaryDir, match);
  }
  return 'ffmpeg';
}
const { chromium } = findModule();
const chromeCandidates = [opts.browser, process.env.CHROME_PATH, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', chromium.executablePath()].filter(Boolean);
const executablePath = chromeCandidates.find(candidate => fs.existsSync(candidate));
if (!executablePath) throw new Error('Headless Chromium unavailable. Set CHROME_PATH.');

const servedRoot = path.dirname(input);
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp', '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf' };
const server = http.createServer((req, res) => {
  try {
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    const requested = path.resolve(servedRoot, `.${pathname}`);
    const relative = path.relative(servedRoot, requested);
    if (relative.startsWith('..') || path.isAbsolute(relative)) {
      res.writeHead(403).end();
      return;
    }
    if (!fs.existsSync(requested) || !fs.statSync(requested).isFile()) {
      res.writeHead(404).end();
      return;
    }
    res.setHeader('Content-Type', mime[path.extname(requested)] ?? 'application/octet-stream');
    fs.createReadStream(requested).pipe(res);
  } catch {
    res.writeHead(400).end();
  }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const address = server.address();
let browser, encoder;
let stderr = '';
const started = Date.now();
try {
  browser = await chromium.launch({ headless: true, executablePath, args: ['--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--hide-scrollbars'] });
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  page.on('pageerror', error => console.error(`Canvas error: ${error.message}`));
  // Only local animation assets may load during export.
  await page.route('**/*', route => {
    const url = route.request().url();
    if (url.startsWith(`http://127.0.0.1:${address.port}/`) || url.startsWith('data:') || url.startsWith('blob:')) route.continue();
    else route.abort();
  });
  await page.goto(`http://127.0.0.1:${address.port}/${encodeURIComponent(path.basename(input))}`, { waitUntil: 'load' });
  await page.evaluate(async () => { await window.filmReady; await document.fonts.ready; });
  const dimensions = await page.evaluate(() => ({ width: document.querySelector('#film').width, height: document.querySelector('#film').height }));
  if (dimensions.width !== 1920 || dimensions.height !== 1080) throw new Error(`Canvas must be 1920x1080; received ${JSON.stringify(dimensions)}`);

  if (opts.snapshot !== undefined) {
    const data = await page.evaluate(async t => {
      await window.renderAt(t);
      return document.querySelector('#film').toDataURL('image/jpeg', 0.96).split(',')[1];
    }, Number(opts.snapshot));
    const snapshot = path.resolve(here, opts['snapshot-out'] ?? 'frame.jpg');
    fs.mkdirSync(path.dirname(snapshot), { recursive: true });
    fs.writeFileSync(snapshot, Buffer.from(data, 'base64'));
    console.log(`Saved snapshot: ${snapshot}`);
  } else {
    const total = Math.round(duration * fps);
    const args = ['-hide_banner', '-loglevel', 'warning', '-y', '-f', 'image2pipe', '-framerate', String(fps), '-vcodec', 'mjpeg', '-i', 'pipe:0'];
    if (opts.audio) args.push('-i', path.resolve(here, opts.audio));
    args.push('-map', '0:v:0', '-vf', 'scale=in_range=full:out_range=tv:out_color_matrix=bt709,format=yuv420p', '-c:v', 'libx264', '-preset', opts.preset ?? 'medium', '-crf', opts.crf ?? '18', '-pix_fmt', 'yuv420p', '-color_range', 'tv', '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-threads', opts.threads ?? '6');
    if (opts.audio) args.push('-map', '1:a:0', '-c:a', 'aac', '-b:a', '192k');
    else args.push('-an');
    args.push('-t', String(total / fps), '-movflags', '+faststart', output);
    encoder = spawn(findFfmpeg(), args, { windowsHide: true, stdio: ['pipe', 'ignore', 'pipe'] });
    encoder.stderr.on('data', chunk => { stderr += chunk.toString(); });
    encoder.stdin.on('error', () => {});
    const encoderDone = new Promise((resolve, reject) => {
      encoder.once('error', reject);
      encoder.once('close', code => code === 0 ? resolve() : reject(new Error(`FFmpeg exited with ${code}: ${stderr}`)));
    });
    // Surface early encoding failure without an unhandled rejection.
    encoderDone.catch(() => {});
    for (let frame = 0; frame < total; frame += batch) {
      const count = Math.min(batch, total - frame);
      const jpegs = await page.evaluate(async ({ frame, count, fps, start }) => {
        const canvas = document.querySelector('#film');
        const result = [];
        for (let i = 0; i < count; i++) {
          await window.renderAt(start + (frame + i) / fps);
          result.push(canvas.toDataURL('image/jpeg', 0.94).split(',')[1]);
        }
        return result;
      }, { frame, count, fps, start });
      for (const jpeg of jpegs) {
        if (encoder.exitCode !== null) await encoderDone;
        const accepted = encoder.stdin.write(Buffer.from(jpeg, 'base64'));
        if (!accepted) await Promise.race([once(encoder.stdin, 'drain'), encoderDone.then(() => { throw new Error('Encoder closed before all frames were written.'); })]);
      }
      if (frame === 0 || frame + count === total || Math.floor((frame + count) / fps) > Math.floor(frame / fps)) {
        const done = frame + count;
        const elapsed = ((Date.now() - started) / 1000).toFixed(1);
        console.log(`Rendered ${done}/${total} frames (${(done / total * 100).toFixed(1)}%), elapsed ${elapsed}s`);
      }
    }
    encoder.stdin.end();
    await encoderDone;
    console.log(`Saved video: ${output} (${(fs.statSync(output).size / 1024 / 1024).toFixed(1)} MiB)`);
  }
} finally {
  if (encoder && encoder.exitCode === null) encoder.kill();
  if (browser) await browser.close();
  await new Promise(resolve => server.close(resolve));
}
