package com.distributed.orderservice.service;

import com.distributed.orderservice.dto.response.ProductResponse;
import com.distributed.orderservice.exception.ResourceNotFoundException;
import com.distributed.orderservice.model.Product;
import com.distributed.orderservice.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(p -> new ProductResponse(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        p.getCategory() != null ? p.getCategory() : "Hardware",
                        p.getPrice(),
                        p.getStock()
                ))
                .collect(Collectors.toList());
    }

    public Product getProduct(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id '" + id + "' not found."));
    }

    public List<ProductResponse> resetCatalog() {
        productRepository.deleteAll();
        productRepository.saveAll(getDefaultCatalog());
        return getAllProducts();
    }

    public static List<Product> getDefaultCatalog() {
        return List.of(
                // Phones
                new Product("prod-iphone-15-pro", "Apple iPhone 15 Pro", "128GB Natural Titanium (Flash Sale Race Demo)", "Phones", 999.00, 1),
                new Product("prod-samsung-s24", "Samsung Galaxy S24 Ultra", "512GB Titanium Black with Galaxy AI", "Phones", 1299.00, 15),
                new Product("prod-pixel-8-pro", "Google Pixel 8 Pro", "128GB Obsidian Tensor G3", "Phones", 899.00, 8),

                // Books
                new Product("prod-book-ddia", "Designing Data-Intensive Applications", "Martin Kleppmann - The definitive distributed systems guide", "Books", 44.99, 50),
                new Product("prod-book-system-design", "System Design Interview (Vol 1 & 2)", "Alex Xu - Essential interview architectural blueprints", "Books", 39.99, 35),
                new Product("prod-book-db-internals", "Database Internals: A Deep Dive", "Alex Petrov - Storage engines, distributed consensus, Raft", "Books", 49.99, 20),

                // Hardware & Audio
                new Product("prod-sony-wh1000xm5", "Sony WH-1000XM5 Headphones", "Wireless Noise Cancelling Headphones (Low Stock Demo)", "Hardware", 349.99, 1),
                new Product("prod-macbook-pro-m3", "Apple MacBook Pro 14\"", "Space Gray M3 Pro 18GB Unified Memory", "Hardware", 1999.00, 5),
                new Product("prod-keychron-k2", "Keychron K2 Mechanical Keyboard", "75% Wireless Mechanical Keyboard Gateron Brown", "Hardware", 89.99, 12),
                new Product("prod-dell-ultrasharp", "Dell UltraSharp 27\" 4K Monitor", "IPS Black Technology Display (Zero Stock Guard)", "Hardware", 549.99, 0),

                // Testing / Chaos Lab
                new Product("prod-fail-payment", "Simulated Payment Fail Item", "Hardcoded trigger to test payment failure & compensation saga", "Chaos Lab", 99.99, 10)
        );
    }
}
