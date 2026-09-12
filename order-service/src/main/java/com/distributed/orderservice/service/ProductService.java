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
                .map(p -> new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getStock()))
                .collect(Collectors.toList());
    }

    public Product getProduct(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id '" + id + "' not found."));
    }
}
