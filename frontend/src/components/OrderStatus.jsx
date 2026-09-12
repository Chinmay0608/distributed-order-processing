import React, { useState, useEffect, useRef } from 'react';
import { getOrderStatus } from '../api';

export default function OrderStatus({ orderId: initialOrderId }) {
  const [orderId, setOrderId] = useState(initialOrderId || '');
  const [orderData, setOrderData] = useState(() => {
    if (typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('preview') === 'stepper') {
      return {
        orderId: 'ORD-7E4A10B2',
        productId: '6aa4d664c4a7835bc162201d',
        productName: 'Sony WH-1000XM5 Headphones',
        quantity: 1,
        totalAmount: 349.99,
        status: 'SHIPPED',
        statusHistory: [
          { status: 'PLACED', detail: 'Order ingested and inventory lock verified by Order Service', timestamp: Date.now() - 45000 },
          { status: 'PAYMENT_PROCESSED', detail: 'Payment confirmed by Kafka payment-service worker', timestamp: Date.now() - 25000 },
          { status: 'SHIPPED', detail: 'Carrier dispatch confirmed by Kafka shipping worker', timestamp: Date.now() - 5000 }
        ]
      };
    }
    return null;
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [isPolling, setIsPolling] = useState(false);

  const pollTimerRef = useRef(null);

  useEffect(() => {
    if (initialOrderId) {
      setOrderId(initialOrderId);
      fetchStatus(initialOrderId);
      setIsPolling(true);
    }
    return () => {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    };
  }, [initialOrderId]);

  const fetchStatus = async (idToFetch) => {
    const id = idToFetch || orderId;
    if (!id || !id.trim()) return;

    try {
      const data = await getOrderStatus(id.trim());
      setOrderData(data);
      setError(null);

      // Auto-stop polling when terminal state is reached
      if (data.status === 'SHIPPED' || data.status === 'FAILED' || data.status === 'CANCELLED') {
        setIsPolling(false);
      }
    } catch (err) {
      setError(err.message || 'Order not found');
      setIsPolling(false);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isPolling && orderId.trim()) {
      pollTimerRef.current = setInterval(() => {
        fetchStatus(orderId.trim());
      }, 1500);
    } else {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    }
    return () => {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    };
  }, [isPolling, orderId]);

  const handleManualSearch = (e) => {
    e.preventDefault();
    setLoading(true);
    fetchStatus(orderId);
    setIsPolling(true);
  };

  const getStepStatus = (stepName) => {
    if (!orderData) return 'pending';
    const current = orderData.status;

    if (current === 'FAILED' || current === 'CANCELLED') {
      return stepName === 'PLACED' ? 'completed' : 'failed';
    }

    const order = ['PLACED', 'PAYMENT_PROCESSED', 'SHIPPED'];
    const currentIndex = order.indexOf(current);
    const stepIndex = order.indexOf(stepName);

    if (stepIndex < currentIndex) return 'completed';
    if (stepIndex === currentIndex) return current === 'SHIPPED' ? 'completed' : 'active';
    return 'pending';
  };

  const getLineStatus = (lineIndex) => {
    if (!orderData) return 'pending';
    const current = orderData.status;
    if (current === 'FAILED' || current === 'CANCELLED') {
      return lineIndex === 1 ? 'failed' : 'pending';
    }
    const order = ['PLACED', 'PAYMENT_PROCESSED', 'SHIPPED'];
    const currentIndex = order.indexOf(current);
    if (currentIndex >= lineIndex) return 'completed';
    if (currentIndex === lineIndex - 1) return 'active';
    return 'pending';
  };

  return (
    <div className="card">
      <div className="section-eyebrow">ASYNCHRONOUS EVENT STREAMING</div>
      <h2>Order Telemetry & Kafka Pipeline</h2>
      <p className="subtitle">
        Visualizes async choreography (<code>order-placed</code> → <code>order-payment-processed</code> → <code>order-shipped</code>) with live consumer polling.
      </p>

      {/* Search & Polling Controls */}
      <form onSubmit={handleManualSearch} className="telemetry-search-bar" style={{ marginTop: '1.25rem', marginBottom: '1.5rem' }}>
        <input
          type="text"
          className="form-control mono-input text-cyan"
          placeholder="Enter Order ID (e.g. ORD-12345678)"
          style={{ maxWidth: '420px' }}
          value={orderId}
          onChange={(e) => setOrderId(e.target.value)}
          required
        />
        <button type="submit" className="btn btn-primary btn-tech" disabled={loading}>
          {loading ? 'LOCATING...' : '🔍 QUERY TRACE'}
        </button>
        {orderData && (
          <button
            type="button"
            className={`btn btn-tech ${isPolling ? 'btn-secondary' : 'btn-outline'}`}
            onClick={() => setIsPolling(!isPolling)}
          >
            {isPolling ? '⏸ PAUSE POLLING' : '▶ RESUME STREAM (1.5s)'}
          </button>
        )}
      </form>

      {error && (
        <div className="alert alert-error">
          <div className="alert-title">RECORD NOT FOUND</div>
          <div>{error}</div>
        </div>
      )}

      {orderData && (
        <div>
          {/* Top Status Banner */}
          <div className="status-header">
            <div>
              <div className="status-header-sub">ORDER TRACKING IDENTIFIER</div>
              <h3 className="status-header-id mono-title">{orderData.orderId}</h3>
              <div className="status-header-meta">
                <span>PRODUCT: <strong>{orderData.productName}</strong></span>
                <span className="sep">•</span>
                <span>QTY: <strong className="mono-num">{orderData.quantity}</strong></span>
                <span className="sep">•</span>
                <span>AMOUNT: <strong className="mono-num">${orderData.totalAmount}</strong></span>
              </div>
            </div>
            <div>
              <span className={`sys-badge sys-badge-lg ${
                orderData.status === 'SHIPPED' ? 'sys-badge-success' :
                orderData.status === 'FAILED' ? 'sys-badge-danger' : 'sys-badge-warning'
              }`}>
                <span className={`sys-dot ${
                  orderData.status === 'SHIPPED' ? 'dot-success' :
                  orderData.status === 'FAILED' ? 'dot-danger' : 'dot-warning'
                } ${isPolling ? 'pulse-dot' : ''}`} />
                {orderData.status}
              </span>
            </div>
          </div>

          {/* Visual Kafka Pipeline Stepper with Filling Connecting Lines */}
          <div className="pipeline-stepper-wrap">
            <div className="pipeline-stepper">
              <div className={`step-item ${getStepStatus('PLACED')}`}>
                <div className="step-circle">
                  {getStepStatus('PLACED') === 'completed' ? '✓' : '1'}
                </div>
                <div className="step-label">01. PLACED</div>
                <div className="step-sub">Order Service Accepted</div>
                <div className="step-topic">topic: order-placed</div>
              </div>

              <div className={`step-line-container ${getLineStatus(1)}`}>
                <div className="step-line-track" />
                <div className="step-line-fill" />
              </div>

              <div className={`step-item ${getStepStatus('PAYMENT_PROCESSED')}`}>
                <div className="step-circle">
                  {getStepStatus('PAYMENT_PROCESSED') === 'completed' ? '✓' : '2'}
                </div>
                <div className="step-label">02. PAYMENT_PROCESSED</div>
                <div className="step-sub">Kafka Payment Worker</div>
                <div className="step-topic">topic: order-payment-processed</div>
              </div>

              <div className={`step-line-container ${getLineStatus(2)}`}>
                <div className="step-line-track" />
                <div className="step-line-fill" />
              </div>

              <div className={`step-item ${getStepStatus('SHIPPED')}`}>
                <div className="step-circle">
                  {getStepStatus('SHIPPED') === 'completed' ? '✓' : '3'}
                </div>
                <div className="step-label">03. SHIPPED</div>
                <div className="step-sub">Kafka Shipping Worker</div>
                <div className="step-topic">terminal stage</div>
              </div>
            </div>
          </div>

          {orderData.status === 'FAILED' && (
            <div className="sys-panel-error" style={{ marginTop: '1.5rem' }}>
              <div className="sys-panel-header">
                <span className="sys-badge sys-badge-danger">SAGA COMPENSATION COMPLETE</span>
                <span className="sys-timestamp">INVENTORY RESTORED</span>
              </div>
              <div className="sys-error-msg">
                Payment failed during Kafka processing. The <code>CompensationConsumer</code> acquired the Redis distributed lock and replenished inventory in MongoDB.
              </div>
            </div>
          )}

          {/* Chronological Audit Timeline */}
          <div style={{ marginTop: '2.5rem' }}>
            <div className="section-eyebrow">DISTRIBUTED EVENT SAGA JOURNAL</div>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-primary)' }}>
              Kafka Pipeline Transition Audit Log
            </h3>
            <p className="subtitle">
              Immutable state transitions committed by Kafka consumers into MongoDB <code>statusHistory</code>
            </p>

            <div className="terminal-audit-panel">
              <div className="audit-list">
                {orderData.statusHistory && orderData.statusHistory.map((entry, idx) => (
                  <div key={idx} className="audit-item">
                    <div className="audit-connector">
                      <span className="audit-dot" />
                      {idx < orderData.statusHistory.length - 1 && <span className="audit-line" />}
                    </div>
                    <div className="audit-card">
                      <div className="audit-card-top">
                        <span className="sys-badge sys-badge-sm sys-badge-secondary">
                          {entry.status}
                        </span>
                        <span className="audit-timestamp mono-num">
                          {new Date(entry.timestamp).toISOString().replace('T', ' ').substring(0, 19)} UTC
                        </span>
                      </div>
                      <div className="audit-desc mono-log">{entry.detail}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
