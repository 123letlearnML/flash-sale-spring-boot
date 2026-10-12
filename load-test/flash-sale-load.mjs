#!/usr/bin/env node
/**
 * Load test cho flash sale — chỉ cần Node 18+ (dùng fetch có sẵn), không cần cài k6/JMeter.
 *
 *   node flash-sale-load.mjs --strategy ATOMIC_UPDATE --requests 20000 --concurrency 500
 *   node flash-sale-load.mjs --all          # chạy lần lượt mọi strategy và in bảng so sánh
 *   node flash-sale-load.mjs --all --warmup 0   # bỏ warm-up (mặc định 10000 request)
 *
 * Mỗi lượt: reset kho -> bắn N request (mỗi request 1 user khác nhau) với C request song song
 * -> đo latency p50/p95/p99, throughput -> đọc lại DB để kiểm tra có bán lố hay không.
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const STRATEGIES = ['NAIVE', 'SYNCHRONIZED', 'PESSIMISTIC', 'OPTIMISTIC', 'ATOMIC_UPDATE'];

function parseArgs(argv) {
  const opts = {
    base: 'http://localhost:8080',
    item: 1,
    stock: 1000,
    requests: 20000,
    concurrency: 500,
    strategy: 'ATOMIC_UPDATE',
    all: false,
    // JVM vừa khởi động chạy chậm hơn nhiều (JIT chưa biên dịch code nóng, pool chưa đầy).
    // Không warm-up thì strategy chạy đầu tiên luôn bị thiệt => so sánh sai.
    warmup: 10000,
  };
  for (let i = 0; i < argv.length; i++) {
    const key = argv[i].replace(/^--/, '');
    if (key === 'all') { opts.all = true; continue; }
    const value = argv[++i];
    opts[key] = ['base', 'strategy'].includes(key) ? value : Number(value);
  }
  return opts;
}

async function postJson(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return { status: res.status, body: await res.json().catch(() => null) };
}

function percentile(sortedMs, p) {
  if (sortedMs.length === 0) return 0;
  const idx = Math.min(sortedMs.length - 1, Math.ceil((p / 100) * sortedMs.length) - 1);
  return sortedMs[idx];
}

async function runOnce(opts, strategy) {
  const reset = await postJson(`${opts.base}/api/admin/items/${opts.item}/reset`, { stock: opts.stock });
  if (reset.status !== 200) throw new Error(`Reset failed: HTTP ${reset.status}`);

  const latencies = [];
  const outcomes = {};
  let next = 0;

  // Worker pool: C "người dùng ảo" chạy song song, mỗi người lấy request tiếp theo khi xong request cũ.
  async function worker() {
    while (true) {
      const i = next++;
      if (i >= opts.requests) return;
      const userId = (i % 100000) + 1; // seed sẵn 100.000 user
      const started = performance.now();
      let key;
      try {
        const res = await postJson(`${opts.base}/api/items/${opts.item}/purchases`, { userId, strategy });
        key = res.body?.outcome ?? `HTTP_${res.status}`;
      } catch (e) {
        key = `NETWORK_ERROR`;
      }
      latencies.push(performance.now() - started);
      outcomes[key] = (outcomes[key] ?? 0) + 1;
    }
  }

  const started = performance.now();
  await Promise.all(Array.from({ length: opts.concurrency }, worker));
  const durationSec = (performance.now() - started) / 1000;

  latencies.sort((a, b) => a - b);
  const stats = await (await fetch(`${opts.base}/api/items/${opts.item}/stats`)).json();

  return {
    strategy,
    requests: opts.requests,
    concurrency: opts.concurrency,
    durationSec: +durationSec.toFixed(2),
    throughputRps: Math.round(opts.requests / durationSec),
    p50Ms: +percentile(latencies, 50).toFixed(1),
    p95Ms: +percentile(latencies, 95).toFixed(1),
    p99Ms: +percentile(latencies, 99).toFixed(1),
    outcomes,
    db: stats,
  };
}

function printTable(results) {
  console.log('\n| Strategy | RPS | p50 (ms) | p95 (ms) | p99 (ms) | Đơn tạo | Kho còn | Bán lố | Mất cập nhật | Kết quả |');
  console.log('|---|---:|---:|---:|---:|---:|---:|---:|---:|---|');
  for (const r of results) {
    const ok = r.db.consistent ? '✅ đúng' : '❌ SAI';
    console.log(`| ${r.strategy} | ${r.throughputRps} | ${r.p50Ms} | ${r.p95Ms} | ${r.p99Ms} | ${r.db.orderCount} | ${r.db.availableStock} | ${r.db.oversold} | ${r.db.lostUpdates} | ${ok} |`);
  }
  console.log('\nChi tiết outcome:');
  for (const r of results) console.log(`  ${r.strategy.padEnd(14)} ${JSON.stringify(r.outcomes)}`);
}

const opts = parseArgs(process.argv.slice(2));
const strategies = opts.all ? STRATEGIES : [opts.strategy];
console.log(`Target ${opts.base} | item=${opts.item} stock=${opts.stock} requests=${opts.requests} concurrency=${opts.concurrency}`);

if (opts.warmup > 0) {
  process.stdout.write(`Warm-up ${opts.warmup} requests (bỏ kết quả)... `);
  const w = await runOnce({ ...opts, requests: opts.warmup }, 'ATOMIC_UPDATE');
  console.log(`${w.throughputRps} req/s`);
}

const results = [];
for (const s of strategies) {
  process.stdout.write(`Running ${s}... `);
  const r = await runOnce(opts, s);
  console.log(`${r.durationSec}s, ${r.throughputRps} req/s`);
  results.push(r);
}
printTable(results);

const outDir = join(dirname(fileURLToPath(import.meta.url)), 'results');
mkdirSync(outDir, { recursive: true });
const file = join(outDir, `run-${new Date().toISOString().replace(/[:.]/g, '-')}.json`);
writeFileSync(file, JSON.stringify({ opts, results }, null, 2));
console.log(`\nĐã lưu kết quả: ${file}`);
