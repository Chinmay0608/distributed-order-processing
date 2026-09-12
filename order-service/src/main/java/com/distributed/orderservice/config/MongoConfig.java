package com.distributed.orderservice.config;

import com.distributed.orderservice.repository.ProductRepository;
import com.distributed.orderservice.service.ProductService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {

    @Bean
    public CommandLineRunner seedDatabase(ProductRepository productRepository) {
        return args -> {
            if (productRepository.count() == 0) {
                productRepository.saveAll(ProductService.getDefaultCatalog());
            }
        };
    }
}
