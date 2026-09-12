package com.distributed.orderservice.service;

import com.distributed.orderservice.exception.LockAcquisitionException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class InventoryLockService {

    private static final Logger log = LoggerFactory.getLogger(InventoryLockService.class);
    private static final String LOCK_PREFIX = "lock:inventory:";
    private static final long WAIT_TIME_MS = 500;
    private static final long LEASE_TIME_MS = 3000;

    private final RedissonClient redissonClient;

    public InventoryLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Attempts to acquire the distributed lock for the specified product.
     * Throws LockAcquisitionException if lock cannot be acquired within 500ms.
     */
    public RLock acquireLock(String productId) {
        String lockKey = LOCK_PREFIX + productId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(WAIT_TIME_MS, LEASE_TIME_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Failed to acquire distributed lock for key '{}' within {}ms", lockKey, WAIT_TIME_MS);
                throw new LockAcquisitionException("High contention on product inventory. Please retry.");
            }
            log.debug("Acquired distributed lock for key '{}'", lockKey);
            return lock;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Thread interrupted while waiting for inventory lock.");
        }
    }

    /**
     * Safely releases the distributed lock if currently held by the executing thread.
     */
    public void releaseLock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
                log.debug("Released distributed lock for key '{}'", lock.getName());
            } catch (Exception e) {
                log.error("Error releasing distributed lock for key '{}': {}", lock.getName(), e.getMessage());
            }
        }
    }
}
