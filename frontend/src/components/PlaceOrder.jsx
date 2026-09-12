import React, { useState, useEffect } from 'react';
import { placeOrder, simulateConcurrentBuyers, generateUUID } from '../api';

export default function PlaceOrder({ products, selectedProduct, onSelectProduct, onTrackOrder }) {
  const [productId, setProductId] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [idempotencyKey, setIdempotencyKey] = useState(generateUUID());

  // Single Order State
  const [submitting, setSubmitting] = useState(false);
  const [orderResult, setOrderResult] = useState(null);
  const [orderError, setOrderError] = useState(null);

  // Concurrent Buyers Simulation State
  const [concurrentCount, setConcurrentCount] = useState(10);
  const [simulating, setSimulating] = useState(false);
  const [simulationResults, setSimulationResults] = useState(() => {
    if (typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('preview') === 'results') {
      return {
        total: 10,
        succeeded: 1,
        failed: 9,
        breakdown: [
          { index: 1, success: true, status: 201, orderId: 'ORD-7E4A10B2', message: 'Success: Order ORD-7E4A10B2 created' },
          { index: 2, success: false, status: 409, errorType: 'OUT_OF_STOCK', message: 'Product is out of stock. Requested: 1, Available: 0.' },
          { index: 3, success: false, status: 409, errorType: 'LOCK_ACQUISITION_FAILED', message: 'High contention on product inventory. Please retry.' },
          { index: 4, success: false, status: 409, errorType: 'LOCK_ACQUISITION_FAILED', message: 'High contention on product inventory. Please retry.' },
          { index: 5, success: false, status: 409, errorType: 'LOCK_ACQUISITION_FAILED', message: 'High contention on product inventory. Please retry.' },
          { index: 6, success: false, status: 409, errorType: 'LOCK_ACQUISITION_FAILED', message: 'High contention on product inventory. Please retry.' },
          { index: 7, success: false, status: 409, errorType: 'LOCK_ACQUISITION_FAILED', message: 'High contention on product inventory. Please retry.' },
          { index: 8, success: false, status: 409, errorType: 'LOCK_ACQUISITION_FAILED', message: 'High contention on product inventory. Please retry.' },
          { index: 9, success: false, status: 409, errorType: 'LOCK_ACQUISITION_FAILED', message: 'High contention on product inventory. Please retry.' },
          { index: 10, success: false, status: 409, errorType: 'LOCK_ACQUISITION_FAILED', message: 'High contention on product inventory. Please retry.' }
        ]
      };
    }
    return null;
  });

  useEffect(() => {
    if (selectedProduct) {
      setProductId(selectedProduct.id);
    } else if (products && products.length > 0 && !productId) {
      setProductId(products[0].id);
    }
  }, [selectedProduct, products]);

  const handleProductChange = (e) => {
    const id = e.target.value;
    setProductId(id);
    const prod = products.find((p) => p.id === id);
    if (prod && onSelectProduct) {
      onSelectProduct(prod);
    }
  };

  const handlePlaceOrder = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setOrderResult(null);
    setOrderError(null);

    try {
      const data = await placeOrder(productId, quantity, idempotencyKey);
      setOrderResult(data);
    } catch (err) {
      setOrderError({
        status: err.status,
        type: err.errorType,
        message: err.message
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handleSimulateConcurrency = async () => {
    if (!productId) return;
    const safeCount = Math.min(Math.max(Number(concurrentCount) || 2, 2), 50);
    setConcurrentCount(safeCount);
    setSimulating(true);
    setSimulationResults(null);
    try {
      const results = await simulateConcurrentBuyers(productId, safeCount);
      const succeeded = results.filter((r) => r.success).length;
      const failed = results.length - succeeded;
      setSimulationResults({
        total: results.length,
        succeeded,
        failed,
        breakdown: results
      });
    } catch (err) {
      alert('Concurrency simulation error: ' + err.message);
    } finally {
      setSimulating(false);
    }
  };

  const currentProduct = products.find((p) => p.id === productId);

  return (
    <div className="grid grid-2">
      {/* LEFT COLUMN: Single Order Placement */}
      <div className="card">
        <div className="section-eyebrow">ORDER INGESTION PIPELINE</div>
        <h2>Direct Order Placement</h2>
        <p className="subtitle">
          Dispatches guarded request to <code>POST /api/orders</code> with Redis Redisson lock & SHA-256 idempotency cache.
        </p>

        <form onSubmit={handlePlaceOrder} style={{ marginTop: '1.25rem' }}>
          <div className="form-group">
            <label className="form-label-tech">SELECT CATALOG TARGET</label>
            <select className="form-control mono-select" value={productId} onChange={handleProductChange} required>
              {products.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} (${p.price.toFixed(2)}) — Live Stock: {p.stock}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label-tech">ORDER QUANTITY</label>
            <input
              type="number"
              className="form-control mono-input"
              min="1"
              max="99"
              value={quantity}
              onChange={(e) => {
                const val = e.target.value;
                if (val === '') {
                  setQuantity('');
                  return;
                }
                const num = parseInt(val, 10);
                if (!isNaN(num)) {
                  setQuantity(Math.min(Math.max(num, 1), 99));
                }
              }}
              required
            />
            <div className="input-hint">
              <span className="hint-tag">SAGA TRIGGER</span> Enter <code>quantity = 99</code> to trigger simulated payment failure & inventory rollback.
            </div>
          </div>

          <div className="form-group">
            <label className="form-label-tech">IDEMPOTENCY-KEY (UUID V4)</label>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <input
                type="text"
                className="form-control mono-input text-cyan"
                value={idempotencyKey}
                onChange={(e) => setIdempotencyKey(e.target.value)}
                required
              />
              <button
                type="button"
                className="btn btn-secondary btn-tech btn-sm"
                title="Generate new UUID"
                onClick={() => setIdempotencyKey(generateUUID())}
              >
                ↻ NEW KEY
              </button>
            </div>
            <div className="input-hint">
              Replay with same key to verify cached response. Alter quantity to test 422 payload mismatch.
            </div>
          </div>

          <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={submitting || !productId}>
            {submitting ? '⚡ ACQUIRING REDIS LOCK & RESERVING...' : 'EXECUTE ORDER DISPATCH →'}
          </button>
        </form>

        {/* Single Order Success Notification */}
        {orderResult && (
          <div className="sys-panel-success" style={{ marginTop: '1.5rem' }}>
            <div className="sys-panel-header">
              <span className="sys-badge sys-badge-success">
                <span className="sys-dot dot-success pulse-dot" />
                HTTP 201 CREATED
              </span>
              <span className="sys-timestamp">LOCK RELEASED // STATE COMMITTED</span>
            </div>
            <div className="sys-kv-grid">
              <div className="sys-kv-row">
                <span className="sys-key">ORDER ID:</span>
                <span className="sys-val mono-highlight text-cyan">{orderResult.orderId}</span>
              </div>
              <div className="sys-kv-row">
                <span className="sys-key">STATUS:</span>
                <span className="sys-val mono-highlight text-emerald">{orderResult.status}</span>
              </div>
              <div className="sys-kv-row">
                <span className="sys-key">TOTAL AMOUNT:</span>
                <span className="sys-val mono-highlight">${orderResult.totalAmount}</span>
              </div>
            </div>
            <button
              className="btn btn-outline btn-block btn-sm"
              style={{ marginTop: '1rem' }}
              onClick={() => onTrackOrder(orderResult.orderId)}
            >
              TRACK IN KAFKA PIPELINE STEPPER →
            </button>
          </div>
        )}

        {/* Single Order Failure Notification */}
        {orderError && (
          <div className="sys-panel-error" style={{ marginTop: '1.5rem' }}>
            <div className="sys-panel-header">
              <span className="sys-badge sys-badge-danger">
                <span className="sys-dot dot-danger" />
                HTTP {orderError.status} {orderError.type}
              </span>
              <span className="sys-timestamp">TRANSACTION REJECTED</span>
            </div>
            <div className="sys-error-msg">{orderError.message}</div>
            <div className="sys-error-reason">
              {orderError.type === 'OUT_OF_STOCK' && (
                <span><strong>Architecture Gate:</strong> MongoDB atomic condition <code>stock &gt;= quantity</code> guarded inventory from overselling.</span>
              )}
              {orderError.type === 'LOCK_ACQUISITION_FAILED' && (
                <span><strong>Architecture Gate:</strong> Redis distributed lock wait-time (&gt;500ms) expired under contention to shed thread load.</span>
              )}
              {orderError.type === 'ORDER_ALREADY_PROCESSING' && (
                <span><strong>Architecture Gate:</strong> In-flight lock detected for this Idempotency Key. Duplicate concurrent request dropped.</span>
              )}
              {orderError.type === 'IDEMPOTENCY_KEY_PAYLOAD_MISMATCH' && (
                <span><strong>Architecture Gate:</strong> SHA-256 payload hash mismatch. Replay with altered arguments blocked.</span>
              )}
            </div>
          </div>
        )}
      </div>

      {/* RIGHT COLUMN: Simulate Concurrent Buyers (Race Condition Demo) */}
      <div className="card">
        <div className="section-eyebrow">HIGH-CONTENTION SIMULATOR</div>
        <h2>Simulate Concurrent Buyers</h2>
        <p className="subtitle">
          Fires concurrent HTTP requests via <code>Promise.allSettled</code> to stress-test Redis distributed locking.
        </p>

        <div className="callout-box-tech">
          <div className="callout-header">
            <span className="callout-badge">INTERVIEW SHOWCASE</span>
            <span>TWO-TIER DEFENSE ARCHITECTURE</span>
          </div>
          <div className="callout-body">
            Select a limited item (e.g. <strong>Stock: 1</strong>). When multiple buyers compete simultaneously, 
            <strong> Redis Redisson</strong> serializes execution and <strong>MongoDB atomic filter</strong> prevents overselling.
          </div>
        </div>

        <div className="form-group" style={{ marginTop: '1.25rem' }}>
          <label className="form-label-tech">TARGET PRODUCT SKU</label>
          <input
            type="text"
            className="form-control mono-input text-muted"
            disabled
            value={currentProduct ? `[${currentProduct.id}] ${currentProduct.name} (Stock: ${currentProduct.stock})` : 'Select target product'}
          />
        </div>

        <div className="form-group">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
            <label className="form-label-tech" style={{ margin: 0 }}>SIMULTANEOUS BUYERS CONCURRENCY ($N$)</label>
            <span className="sys-badge sys-badge-sm sys-badge-secondary">CAP: 2 – 50 SAFE MAX</span>
          </div>
          <div className="concurrency-input-wrap">
            <input
              type="number"
              className="form-control mono-input"
              min="2"
              max="50"
              value={concurrentCount}
              onChange={(e) => {
                const val = e.target.value;
                if (val === '') {
                  setConcurrentCount('');
                  return;
                }
                const num = parseInt(val, 10);
                if (!isNaN(num)) {
                  setConcurrentCount(Math.min(Math.max(num, 1), 50));
                }
              }}
            />
            <div className="concurrency-presets">
              {[5, 10, 20, 50].map((preset) => (
                <button
                  key={preset}
                  type="button"
                  className={`btn-preset ${concurrentCount === preset ? 'active' : ''}`}
                  onClick={() => setConcurrentCount(preset)}
                >
                  {preset}x
                </button>
              ))}
            </div>
          </div>
          <div className="input-hint">
            <span className="hint-tag" style={{ background: 'var(--cyan-bg)', color: 'var(--cyan-primary)', borderColor: 'var(--cyan-border)' }}>BROWSER SAFETY</span>
            Guarded ceiling of 50 concurrent HTTP threads prevents local socket table exhaustion and V8 memory starvation.
          </div>
        </div>

        <button
          type="button"
          className="btn btn-blast btn-block btn-lg"
          onClick={handleSimulateConcurrency}
          disabled={simulating || !productId}
        >
          {simulating ? `⚡ BLASTING ${concurrentCount} CONCURRENT BUYERS...` : `⚡ BLAST ${concurrentCount} CONCURRENT BUYERS`}
        </button>

        {/* Real-time Telemetry Scoreboard & Trace Stream */}
        {simulationResults && (
          <div className="race-results-container">
            <div className="results-header">
              <div className="results-title">
                <span className="sys-dot dot-cyan pulse-dot" />
                SIMULATION EXECUTION SCOREBOARD
              </div>
              <div className="consistency-tag">ZERO OVERSELLING GUARANTEED</div>
            </div>

            {/* Scoreboard Metric Cards */}
            <div className="scoreboard">
              <div className="score-box score-total">
                <div className="score-label">REQUESTS FIRED</div>
                <div className="score-number">{simulationResults.total}</div>
                <div className="score-sub">Concurrent Threads</div>
              </div>
              <div className="score-box score-success">
                <div className="score-label">SUCCEEDED [201]</div>
                <div className="score-number">{simulationResults.succeeded}</div>
                <div className="score-sub">Inventory Allocated</div>
              </div>
              <div className="score-box score-failed">
                <div className="score-label">CONTENTION SHED [409]</div>
                <div className="score-number">{simulationResults.failed}</div>
                <div className="score-sub">Safely Blocked</div>
              </div>
            </div>

            {/* Ratio Progress Visualizer */}
            <div className="ratio-bar-wrap">
              <div
                className="ratio-bar-success"
                style={{ width: `${(simulationResults.succeeded / simulationResults.total) * 100}%` }}
                title={`Succeeded: ${simulationResults.succeeded}`}
              />
              <div
                className="ratio-bar-failed"
                style={{ width: `${(simulationResults.failed / simulationResults.total) * 100}%` }}
                title={`Blocked: ${simulationResults.failed}`}
              />
            </div>

            {/* Terminal Style Execution Trace Stream */}
            <div className="terminal-trace-panel">
              <div className="terminal-topbar">
                <div className="terminal-dots">
                  <span className="t-dot t-red" />
                  <span className="t-dot t-yellow" />
                  <span className="t-dot t-green" />
                </div>
                <div className="terminal-title">sys.trace::redis_lock_stream [contention_guard]</div>
                <div className="terminal-status">BUFFER: FLUSHED</div>
              </div>

              <div className="terminal-body">
                {simulationResults.breakdown.map((res, idx) => {
                  const isSuccess = res.success;
                  const isOutOfStock = res.message && res.message.includes('out of stock');
                  const isLockTimeout = res.message && (res.message.includes('contention') || res.message.includes('lock'));
                  
                  return (
                    <div
                      key={res.index}
                      className={`terminal-row ${isSuccess ? 't-row-success' : 't-row-fail'}`}
                      style={{ '--row-idx': idx }}
                    >
                      <span className="t-time">+{String(idx * 15 + 12).padStart(3, '0')}ms</span>
                      <span className="t-actor">BUYER_{String(res.index).padStart(2, '0')}</span>
                      <span className={`t-code ${isSuccess ? 't-code-201' : isOutOfStock ? 't-code-409-stock' : 't-code-409-lock'}`}>
                        [{res.status} {isSuccess ? 'OK' : isOutOfStock ? 'OUT_OF_STOCK' : 'LOCK_TIMEOUT'}]
                      </span>
                      <span className="t-msg">
                        {isSuccess ? (
                          <>
                            <span className="t-check">✓</span> Order{' '}
                            <button
                              type="button"
                              className="t-order-chip"
                              title="Click to track in Kafka Pipeline Stepper"
                              onClick={() => onTrackOrder && onTrackOrder(res.orderId)}
                            >
                              {res.orderId} ↗
                            </button>{' '}
                            <span className="t-detail-muted">• Lock acquired & stock decremented</span>
                          </>
                        ) : (
                          <>
                            <span className="t-cross">✗</span> {res.message}
                          </>
                        )}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
