package com.distributed.orderservice.config;

import com.distributed.orderservice.model.Product;
import com.distributed.orderservice.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MongoConfig {

    @Bean
    public CommandLineRunner seedDatabase(ProductRepository productRepository) {
        return args -> {
            if (productRepository.count() == 0) {
                Product p1 = new Product(null, "Sony WH-1000XM5 Headphones", "Wireless Noise Cancelling Headphones (Low Stock Demo)", 349.99, 1);
                Product p2 = new Product(null, "Keychron K2 Mechanical Keyboard", "75% Wireless Mechanical Keyboard", 89.99, 10);
                Product p3 = new Product(null, "Apple MacBook Pro M3", "14-inch Space Gray 16GB Unified Memory", 1999.99, 5);
                Product p4 = new Product(null, "Dell UltraSharp 27 4K Monitor", "IPS Black Technology Display (Zero Stock Demo)", 499.99, 0);
                Product p5 = new Product("prod-fail-payment", "Simulated Payment Fail Item", "Hardcoded trigger to test payment failure & compensation saga", 99.99, 5);

                productRepository.saveAll(List.of(p1, p2, p3, p4, p5));
            }
        };
    }
}
