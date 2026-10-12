import { useEffect, useState } from 'react'
import { api, STRATEGIES, type ItemStats, type PurchaseResult, type Strategy } from './api'

const POLL_INTERVAL_MS = 500

interface LogEntry {
  id: number
  text: string
  ok: boolean
}

export default function App() {
  const [items, setItems] = useState<ItemStats[]>([])
  const [error, setError] = useState<string | null>(null)

  // Poll liên tục để thấy kho giảm theo thời gian thực khi đang chạy load test.
  useEffect(() => {
    let cancelled = false
    const tick = async () => {
      try {
        const data = await api.listItems()
        if (!cancelled) {
          setItems(data)
          setError(null)
        }
      } catch (e) {
        if (!cancelled) setError((e as Error).message)
      }
    }
    tick()
    const timer = setInterval(tick, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [])

  return (
    <main className="page">
      <header>
        <h1>Flash Sale Lab</h1>
        <p className="muted">
          1 sản phẩm, hàng nghìn người mua cùng lúc. Chạy load test rồi xem chiến lược nào bán lố.
        </p>
      </header>

      {error && <div className="banner error">Không kết nối được backend: {error}</div>}

      {items.map((item) => (
        <ItemPanel key={item.itemId} item={item} />
      ))}

      <section className="card">
        <h2>Load test</h2>
        <p className="muted">Mở terminal ở thư mục <code>01-flash-sale/load-test</code>:</p>
        <pre>node flash-sale-load.mjs --all --requests 20000 --concurrency 500</pre>
      </section>
    </main>
  )
}

function ItemPanel({ item }: { item: ItemStats }) {
  const soldPercent = item.totalStock === 0 ? 0 : (item.orderCount / item.totalStock) * 100

  return (
    <section className="card">
      <div className="row space-between">
        <h2>{item.name}</h2>
        <span className={`badge ${item.consistent ? 'ok' : 'bad'}`}>
          {item.consistent ? 'Dữ liệu nhất quán' : 'PHÁT HIỆN LỖI'}
        </span>
      </div>

      <div className="progress" aria-label="Tỉ lệ đã bán">
        <div
          className={`progress-fill ${item.oversold > 0 ? 'bad' : ''}`}
          style={{ width: `${Math.min(soldPercent, 100)}%` }}
        />
      </div>

      <dl className="stats">
        <Stat label="Tổng kho" value={item.totalStock} />
        <Stat label="Kho còn (DB)" value={item.availableStock} />
        <Stat label="Đơn đã tạo" value={item.orderCount} />
        <Stat label="Bán lố" value={item.oversold} danger={item.oversold > 0} />
        <Stat label="Mất cập nhật" value={item.lostUpdates} danger={item.lostUpdates !== 0} />
      </dl>

      <div className="grid">
        <ResetForm itemId={item.itemId} />
        <BuyForm itemId={item.itemId} />
      </div>
    </section>
  )
}

function Stat({ label, value, danger = false }: { label: string; value: number; danger?: boolean }) {
  return (
    <div className={`stat ${danger ? 'danger' : ''}`}>
      <dt>{label}</dt>
      <dd>{value.toLocaleString('vi-VN')}</dd>
    </div>
  )
}

function ResetForm({ itemId }: { itemId: number }) {
  const [stock, setStock] = useState(1000)
  const [busy, setBusy] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    try {
      await api.reset(itemId, stock)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="form">
      <h3>Đặt lại kho</h3>
      <label>
        Số lượng
        <input type="number" min={1} value={stock} onChange={(e) => setStock(Number(e.target.value))} />
      </label>
      <button type="submit" disabled={busy}>
        Reset (xoá toàn bộ đơn)
      </button>
    </form>
  )
}

function BuyForm({ itemId }: { itemId: number }) {
  const [userId, setUserId] = useState(1)
  const [strategy, setStrategy] = useState<Strategy>('ATOMIC_UPDATE')
  const [log, setLog] = useState<LogEntry[]>([])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    let entry: LogEntry
    try {
      const result: PurchaseResult = await api.purchase(itemId, userId, strategy)
      const ok = result.outcome === 'SUCCESS'
      entry = {
        id: Date.now(),
        ok,
        text: `user ${userId} · ${strategy} → ${result.outcome}${ok ? ` (đơn #${result.orderId})` : ''}`,
      }
    } catch (err) {
      entry = { id: Date.now(), ok: false, text: `user ${userId} → lỗi: ${(err as Error).message}` }
    }
    setLog((prev) => [entry, ...prev].slice(0, 8))
    setUserId((id) => id + 1)
  }

  return (
    <form onSubmit={submit} className="form">
      <h3>Mua thử 1 lần</h3>
      <label>
        User ID (1 – 100.000)
        <input type="number" min={1} max={100000} value={userId} onChange={(e) => setUserId(Number(e.target.value))} />
      </label>
      <label>
        Chiến lược
        <select value={strategy} onChange={(e) => setStrategy(e.target.value as Strategy)}>
          {STRATEGIES.map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
      </label>
      <button type="submit">Mua</button>
      <ul className="log">
        {log.map((l) => (
          <li key={l.id} className={l.ok ? 'ok' : 'bad'}>
            {l.text}
          </li>
        ))}
      </ul>
    </form>
  )
}
