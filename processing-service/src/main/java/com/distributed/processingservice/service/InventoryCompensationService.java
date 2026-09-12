package com.distributed.processingservice.service;

import com.distributed.processingservice.event.OrderPaymentFailedEvent;
import com.distributed.processingservice.model.Order;
import com.distributed.processingservice.model.OrderStatus;
import com.distributed.processingservice.model.Product;
import com.distributed.processingservice.repository.OrderRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class InventoryCompensationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryCompensationService.class);
    private static final String LOCK_PREFIX = "lock:inventory:";

    private final MongoTemplate mongoTemplate;
    private final OrderRepository orderRepository;
    private final RedissonClient redissonClient;

    public InventoryCompensationService(MongoTemplate mongoTemplate,
                                        OrderRepository orderRepository,
                                        RedissonClient redissonClient) {
        this.mongoTemplate = mongoTemplate;
        this.orderRepository = orderRepository;
        this.redissonClient = redissonClient;
    }

    public void compensateInventory(OrderPaymentFailedEvent event) {
        log.warn("Triggering inventory compensation saga for failed orderId='{}', productId='{}', quantity={}",
                event.getOrderId(), event.getProductId(), event.getQuantity());

        String lockKey = LOCK_PREFIX + event.getProductId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(500, 3000, TimeUnit.MILLISECONDS);
            if (acquired) {
                // Replenish stock atomically in MongoDB
                Query query = Query.query(Criteria.where("_id").is(event.getProductId()));
                Update update = new Update().inc("stock", event.getQuantity());
                mongoTemplate.updateFirst(query, update, Product.class);

                log.info("Successfully replenished stock by {} for product '{}'", event.getQuantity(), event.getProductId());

                // Update order status history
                Order order = orderRepository.findByOrderId(event.getOrderId()).orElse(null);
                if (order != null) {
                    order.addStatusHistory(OrderStatus.FAILED,
                            String.format("Inventory compensation completed: %d unit(s) restored to stock", event.getQuantity()));
                    orderRepository.save(order);
                }
            } else {
                log.error("Failed to acquire Redis lock for inventory compensation on product '{}'", event.getProductId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock for compensation on product '{}'", event.getProductId());
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
