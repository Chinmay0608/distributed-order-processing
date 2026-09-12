const API_BASE = 'http://localhost:8080/api';

/**
 * Generate a random UUID v4 for Idempotency-Key header using native Web Crypto.
 */
export function generateUUID() {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : 'idemp-' + Date.now() + '-' + Math.random().toString(36).substring(2, 11);
}

/**
 * Fetch product catalog with live stock counts.
 */
export async function getProducts() {
  const response = await fetch(`${API_BASE}/products`, {
    headers: { Accept: 'application/json' }
  });
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || `Failed to fetch products (HTTP ${response.status})`);
  }
  return response.json();
}

/**
 * Reset and restock catalog back to defaults across all categories.
 */
export async function resetProducts() {
  const response = await fetch(`${API_BASE}/products/reset`, {
    method: 'POST',
    headers: { Accept: 'application/json' }
  });
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || `Failed to reset catalog (HTTP ${response.status})`);
  }
  return response.json();
}

/**
 * Place a single order guarded by Redis distributed lock and Idempotency key.
 */
export async function placeOrder(productId, quantity, idempotencyKey) {
  const response = await fetch(`${API_BASE}/orders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Idempotency-Key': idempotencyKey
    },
    body: JSON.stringify({ productId, quantity: Number(quantity) })
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || `Order failed (HTTP ${response.status})`);
    err.status = response.status;
    err.errorType = data.error || 'ERROR';
    err.data = data;
    throw err;
  }
  return data;
}

/**
 * Poll live status and Kafka transition history for an order.
 */
export async function getOrderStatus(orderId) {
  const response = await fetch(`${API_BASE}/orders/${encodeURIComponent(orderId)}`, {
    headers: { Accept: 'application/json' }
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || `Order '${orderId}' not found (HTTP ${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

/**
 * Fires N concurrent requests simultaneously using Promise.allSettled.
 * Evaluates Redis distributed locking and race condition defense under concurrent load.
 */
export async function simulateConcurrentBuyers(productId, count = 10) {
  // Safety guardrail: clamp between 2 and 50 to prevent browser V8 OOM and OS TCP socket exhaustion
  const safeCount = Math.min(Math.max(Number(count) || 2, 2), 50);
  const requests = Array.from({ length: safeCount }, (_, idx) => {
    const uniqueKey = `race-${Date.now()}-${idx}-${generateUUID().substring(0, 8)}`;
    return placeOrder(productId, 1, uniqueKey)
      .then((data) => ({
        index: idx + 1,
        success: true,
        status: 201,
        orderId: data.orderId,
        message: `Success: Order ${data.orderId} created`
      }))
      .catch((err) => ({
        index: idx + 1,
        success: false,
        status: err.status || 0,
        errorType: err.errorType || 'NETWORK_ERROR',
        message: err.message
      }));
  });

  return Promise.all(requests);
}
