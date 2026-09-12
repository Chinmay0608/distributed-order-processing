package com.distributed.orderservice.dto.response;

public class ProductResponse {
    private String id;
    private String name;
    private String description;
    private String category;
    private Double price;
    private Integer stock;

    public ProductResponse() {
    }

    public ProductResponse(String id, String name, String description, Double price, Integer stock) {
        this(id, name, description, "Hardware", price, stock);
    }

    public ProductResponse(String id, String name, String description, String category, Double price, Integer stock) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }
}
