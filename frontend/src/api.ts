export const STRATEGIES = ['NAIVE', 'SYNCHRONIZED', 'PESSIMISTIC', 'OPTIMISTIC', 'ATOMIC_UPDATE'] as const
export type Strategy = (typeof STRATEGIES)[number]

export interface ItemStats {
  itemId: number
  name: string
  totalStock: number
  availableStock: number
  orderCount: number
  oversold: number
  lostUpdates: number
  consistent: boolean
}

export interface PurchaseResult {
  outcome: 'SUCCESS' | 'SOLD_OUT' | 'ALREADY_PURCHASED' | 'RETRY_EXHAUSTED'
  orderId: number | null
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  const body = await res.json().catch(() => null)
  // 409 vẫn là kết quả nghiệp vụ hợp lệ (hết hàng, đã mua) nên trả body về cho UI hiển thị.
  if (!res.ok && res.status !== 409) {
    throw new Error(body?.detail ?? `HTTP ${res.status}`)
  }
  return body as T
}

export const api = {
  listItems: () => request<ItemStats[]>('/api/items'),
  purchase: (itemId: number, userId: number, strategy: Strategy) =>
    request<PurchaseResult>(`/api/items/${itemId}/purchases`, {
      method: 'POST',
      body: JSON.stringify({ userId, strategy }),
    }),
  reset: (itemId: number, stock: number) =>
    request<ItemStats>(`/api/admin/items/${itemId}/reset`, {
      method: 'POST',
      body: JSON.stringify({ stock }),
    }),
}
