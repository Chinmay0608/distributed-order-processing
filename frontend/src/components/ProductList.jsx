import React, { useState, useEffect } from 'react';
import { getProducts, resetProducts } from '../api';

export default function ProductList({ onSelectProduct, onProductsUpdated }) {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [restocking, setRestocking] = useState(false);
  const [error, setError] = useState(null);
  const [restockNotice, setRestockNotice] = useState(null);
  const [selectedCategory, setSelectedCategory] = useState('ALL');

  const fetchCatalog = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getProducts();
      setProducts(data);
      if (onProductsUpdated) onProductsUpdated();
    } catch (err) {
      setError(err.message || 'Failed to connect to backend at http://localhost:8080');
    } finally {
      setLoading(false);
    }
  };

  const handleRestock = async () => {
    setRestocking(true);
    setError(null);
    try {
      const data = await resetProducts();
      setProducts(data);
      if (onProductsUpdated) onProductsUpdated();
      setRestockNotice('Catalog restocked to baseline across all categories!');
      setTimeout(() => setRestockNotice(null), 3500);
    } catch (err) {
      setError(err.message || 'Failed to restock catalog');
    } finally {
      setRestocking(false);
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

  // Distinct categories dynamically derived from catalog
  const distinctCategories = ['ALL', ...Array.from(new Set(products.map((p) => p.category || 'Hardware')))];

  const filteredProducts = selectedCategory === 'ALL'
    ? products
    : products.filter((p) => (p.category || 'Hardware').toLowerCase() === selectedCategory.toLowerCase());

  const getCategoryCount = (cat) => {
    if (cat === 'ALL') return products.length;
    return products.filter((p) => (p.category || 'Hardware').toLowerCase() === cat.toLowerCase()).length;
  };

  return (
    <div className="card">
      <div className="card-header">
        <div>
          <div className="section-eyebrow">CLUSTER INVENTORY TELEMETRY</div>
          <h2>Product Catalog & Live Stock</h2>
          <p className="subtitle">Real-time inventory state synchronized directly from MongoDB via <code>GET /api/products</code></p>
        </div>
        <div className="catalog-actions">
          <button className="btn btn-secondary btn-tech" onClick={fetchCatalog} disabled={loading || restocking}>
            {loading ? 'SYNCHRONIZING...' : '↻ SYNC STOCK'}
          </button>
          <button className="btn btn-outline btn-tech btn-restock" onClick={handleRestock} disabled={loading || restocking} title="Reset all product stocks to defaults">
            {restocking ? 'RESTOCKING...' : '↺ RESTOCK CATALOG'}
          </button>
        </div>
      </div>

      {restockNotice && (
        <div className="alert alert-success" style={{ marginBottom: '1.25rem' }}>
          <div className="alert-title">CATALOG RESET SUCCESSFUL</div>
          <div>{restockNotice}</div>
        </div>
      )}

      {error && (
        <div className="alert alert-error">
          <div className="alert-title">BACKEND CONNECTION ERROR</div>
          <div>{error}</div>
          <div className="alert-meta">
            Target: <code>http://localhost:8080/api/products</code> — Verify container <code>order_service</code> is healthy.
          </div>
        </div>
      )}

      {/* Category Filter Tabs */}
      {products.length > 0 && (
        <div className="category-filter-bar">
          <span className="category-bar-label">CATEGORY FILTER:</span>
          <div className="category-tabs">
            {distinctCategories.map((cat) => {
              const count = getCategoryCount(cat);
              const isActive = selectedCategory.toLowerCase() === cat.toLowerCase();
              return (
                <button
                  key={cat}
                  className={`category-tab-btn ${isActive ? 'active' : ''}`}
                  onClick={() => setSelectedCategory(cat)}
                >
                  <span className="cat-name">{cat.toUpperCase()}</span>
                  <span className="cat-count">{count}</span>
                </button>
              );
            })}
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
                <th>CATEGORY</th>
                <th>UNIT PRICE</th>
                <th>LIVE INVENTORY</th>
                <th>ARCHITECTURAL SCENARIO</th>
                <th style={{ textAlign: 'right' }}>ACTION</th>
              </tr>
            </thead>
            <tbody>
              {filteredProducts.map((p) => {
                const category = p.category || 'Hardware';
                const catKey = category.toLowerCase().replace(/\s+/g, '-');

                return (
                  <tr key={p.id}>
                    <td>
                      <div className="product-title">{p.name}</div>
                      <div className="product-desc">{p.description}</div>
                      <div className="product-sku">SKU: <code>{p.id}</code></div>
                    </td>
                    <td>
                      <span className={`cat-pill cat-${catKey}`}>{category}</span>
                    </td>
                    <td>
                      <span className="tabular-price">${p.price.toFixed(2)}</span>
                    </td>
                    <td>{getStockBadge(p.stock)}</td>
                    <td>
                      {p.stock === 1 && <span className="tech-scenario-tag tag-race">⚡ RACE GUARD (STOCK=1)</span>}
                      {p.id === 'prod-fail-payment' && <span className="tech-scenario-tag tag-fail">🔄 SAGA COMPENSATION</span>}
                      {p.stock === 0 && <span className="tech-scenario-tag tag-empty">∅ ZERO INVENTORY</span>}
                      {category === 'Books' && p.stock > 1 && <span className="tech-scenario-tag tag-bulk">📦 BULK INVENTORY</span>}
                      {category !== 'Books' && p.stock > 1 && p.id !== 'prod-fail-payment' && (
                        <span className="tech-scenario-tag tag-normal">✓ STANDARD FLOW</span>
                      )}
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
                );
              })}
              {filteredProducts.length === 0 && !loading && !error && (
                <tr>
                  <td colSpan="6" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
                    No products found in category "{selectedCategory}".
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
