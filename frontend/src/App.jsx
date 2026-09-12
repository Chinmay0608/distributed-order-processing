import React, { useState, useEffect } from 'react';
import ProductList from './components/ProductList';
import PlaceOrder from './components/PlaceOrder';
import OrderStatus from './components/OrderStatus';
import { getProducts } from './api';

export default function App() {
  const [activeTab, setActiveTab] = useState(() => {
    if (typeof window !== 'undefined') {
      const param = new URLSearchParams(window.location.search).get('tab');
      if (param) return param;
    }
    return 'products';
  });
  const [products, setProducts] = useState([]);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [trackedOrderId, setTrackedOrderId] = useState('');

  const refreshProducts = async () => {
    try {
      const data = await getProducts();
      setProducts(data);
      if (data.length > 0 && !selectedProduct) {
        setSelectedProduct(data[0]);
      }
    } catch (err) {
      // Backend status will be reflected in screen alert
    }
  };

  useEffect(() => {
    refreshProducts();
  }, []);

  const handleSelectProduct = (product) => {
    setSelectedProduct(product);
    setActiveTab('order');
  };

  const handleTrackOrder = (orderId) => {
    setTrackedOrderId(orderId);
    setActiveTab('status');
  };

  return (
    <div className="app-container">
      {/* Top Header: Systems Telemetry Bar */}
      <header className="app-header">
        <div className="header-brand">
          <div className="brand-title-wrap">
            <div className="brand-eyebrow">
              <span className="telemetry-beacon" />
              <span>SYSTEMS TELEMETRY // LOCAL CLUSTER</span>
            </div>
            <h1 className="brand-title">
              <span className="brand-icon">⚡</span> DISTRIBUTED ORDER CONSOLE
            </h1>
          </div>
          <div className="header-status-panel">
            <div className="status-pill connected">
              <span className="status-dot-pulse" />
              <span className="status-label">GATEWAY ONLINE</span>
              <span className="status-target">8080</span>
            </div>
            <div className="tech-chips">
              <span className="tech-chip">REDISSON LOCK</span>
              <span className="tech-chip">KAFKA STREAM</span>
              <span className="tech-chip">MONGO SAGA</span>
            </div>
          </div>
        </div>
        <nav className="nav-tabs">
          <button
            className={`nav-tab ${activeTab === 'products' ? 'active' : ''}`}
            onClick={() => { refreshProducts(); setActiveTab('products'); }}
          >
            <span className="tab-num">01</span>
            <span className="tab-label">Product Inventory</span>
          </button>
          <button
            className={`nav-tab ${activeTab === 'order' ? 'active' : ''}`}
            onClick={() => setActiveTab('order')}
          >
            <span className="tab-num">02</span>
            <span className="tab-label">Order & Concurrency Simulator</span>
          </button>
          <button
            className={`nav-tab ${activeTab === 'status' ? 'active' : ''}`}
            onClick={() => setActiveTab('status')}
          >
            <span className="tab-num">03</span>
            <span className="tab-label">Kafka Pipeline Stepper</span>
          </button>
        </nav>
      </header>

      {/* Main Content View */}
      <main className="main-content">
        {activeTab === 'products' && (
          <ProductList
            onSelectProduct={handleSelectProduct}
            onProductsUpdated={refreshProducts}
          />
        )}

        {activeTab === 'order' && (
          <PlaceOrder
            products={products}
            selectedProduct={selectedProduct}
            onSelectProduct={setSelectedProduct}
            onTrackOrder={handleTrackOrder}
          />
        )}

        {activeTab === 'status' && (
          <OrderStatus
            orderId={trackedOrderId}
          />
        )}
      </main>

      <footer className="app-footer">
        <div>Distributed Systems Architecture: <strong>Redis Redisson Locking</strong> | <strong>Apache Kafka Pipeline</strong> | <strong>MongoDB Event Sagas</strong></div>
      </footer>
    </div>
  );
}
