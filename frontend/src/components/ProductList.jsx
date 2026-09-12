import React, { useState, useEffect } from 'react';
import { getProducts } from '../api';

export default function ProductList({ onSelectProduct }) {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchCatalog = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getProducts();
      setProducts(data);
    } catch (err) {
      setError(err.message || 'Failed to connect to backend at http://localhost:8080');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCatalog();
  }, []);

  const getStockBadge = (stock) => {
    if (stock === 0) {
      return (
        <span className="sys-badge sys-badge-danger">
          <span className="sys-dot dot-danger" />
          OUT OF STOCK [0]
        </span>
      );
    }
    if (stock <= 3) {
      return (
        <span className="sys-badge sys-badge-warning">
          <span className="sys-dot dot-warning pulse-dot" />
          LOW STOCK [{stock}]
        </span>
      );
    }
    return (
      <span className="sys-badge sys-badge-success">
        <span className="sys-dot dot-success" />
        IN STOCK [{stock}]
      </span>
    );
  };

  return (
    <div className="card">
      <div className="card-header">
        <div>
          <div className="section-eyebrow">CLUSTER INVENTORY TELEMETRY</div>
          <h2>Product Catalog & Live Stock</h2>
          <p className="subtitle">Real-time inventory state synchronized directly from MongoDB via <code>GET /api/products</code></p>
        </div>
        <button className="btn btn-secondary btn-tech" onClick={fetchCatalog} disabled={loading}>
          {loading ? 'SYNCHRONIZING...' : '↻ SYNC STOCK'}
        </button>
      </div>

      {error && (
        <div className="alert alert-error">
          <div className="alert-title">BACKEND CONNECTION OFFLINE</div>
          <div>{error}</div>
          <div className="alert-meta">
            Target: <code>http://localhost:8080/api/products</code> — Verify container <code>order_service</code> is healthy.
          </div>
        </div>
      )}

      {loading && products.length === 0 ? (
        <div className="loading-spinner">
          <div className="spinner-orbit" />
          <span>Polling catalog cluster...</span>
        </div>
      ) : (
        <div className="table-responsive">
          <table className="table">
            <thead>
              <tr>
                <th>PRODUCT // SPECIFICATION</th>
                <th>UNIT PRICE</th>
                <th>LIVE INVENTORY</th>
                <th>ARCHITECTURAL SCENARIO</th>
                <th style={{ textAlign: 'right' }}>ACTION</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id}>
                  <td>
                    <div className="product-title">{p.name}</div>
                    <div className="product-desc">{p.description}</div>
                    <div className="product-sku">SKU: <code>{p.id}</code></div>
                  </td>
                  <td>
                    <span className="tabular-price">${p.price.toFixed(2)}</span>
                  </td>
                  <td>{getStockBadge(p.stock)}</td>
                  <td>
                    {p.stock === 1 && <span className="tech-scenario-tag tag-race">⚡ RACE GUARD (STOCK=1)</span>}
                    {p.id === 'prod-fail-payment' && <span className="tech-scenario-tag tag-fail">🔄 SAGA COMPENSATION</span>}
                    {p.stock === 0 && <span className="tech-scenario-tag tag-empty">∅ ZERO INVENTORY</span>}
                    {p.stock > 3 && <span className="tech-scenario-tag tag-normal">✓ STANDARD FLOW</span>}
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      className="btn btn-primary btn-sm"
                      onClick={() => onSelectProduct(p)}
                    >
                      TEST FLOW →
                    </button>
                  </td>
                </tr>
              ))}
              {products.length === 0 && !loading && !error && (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
                    No product documents returned by cluster gateway.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
