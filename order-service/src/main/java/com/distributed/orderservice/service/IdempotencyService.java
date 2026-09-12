package com.distributed.orderservice.service;

import com.distributed.orderservice.exception.IdempotencyPayloadMismatchException;
import com.distributed.orderservice.exception.OrderAlreadyProcessingException;
import com.distributed.orderservice.model.IdempotencyRecord;
import com.distributed.orderservice.model.IdempotencyStatus;
import com.distributed.orderservice.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    public String computePayloadHash(String productId, Integer quantity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((productId + ":" + quantity).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Checks existing idempotency state or records an IN_PROGRESS lock.
     * Returns cached IdempotencyRecord if already COMPLETED.
     */
    public Optional<IdempotencyRecord> checkOrStart(String key, String payloadHash) {
        Optional<IdempotencyRecord> existing = idempotencyKeyRepository.findById(key);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                if (!payloadHash.equals(record.getRequestPayloadHash())) {
                    log.warn("Idempotency key '{}' reused with mismatched payload. Stored: {}, incoming: {}",
                            key, record.getRequestPayloadHash(), payloadHash);
                    throw new IdempotencyPayloadMismatchException(
                            "Idempotency key '" + key + "' was previously used with a different request payload.");
                }
                log.info("Idempotency key '{}' already completed. Returning cached response.", key);
                return Optional.of(record);
            } else if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                log.warn("Idempotency key '{}' is currently in progress.", key);
                throw new OrderAlreadyProcessingException(
                        "An order with idempotency key '" + key + "' is currently in progress.");
            }
            // If previous attempt FAILED, update status back to IN_PROGRESS for a clean retry
            record.setStatus(IdempotencyStatus.IN_PROGRESS);
            record.setRequestPayloadHash(payloadHash);
            idempotencyKeyRepository.save(record);
            return Optional.empty();
        }

        try {
            IdempotencyRecord newRecord = new IdempotencyRecord(key, IdempotencyStatus.IN_PROGRESS, payloadHash);
            idempotencyKeyRepository.save(newRecord);
            return Optional.empty();
        } catch (DuplicateKeyException e) {
            // Concurrent insert race condition caught by MongoDB unique index
            throw new OrderAlreadyProcessingException(
                    "An order with idempotency key '" + key + "' is currently in progress.");
        }
    }

    public void markCompleted(String key, String orderId, int statusCode, Object responseBody) {
        idempotencyKeyRepository.findById(key).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setOrderId(orderId);
            record.setResponseStatusCode(statusCode);
            record.setResponseBody(responseBody);
            idempotencyKeyRepository.save(record);
        });
    }

    public void markFailed(String key) {
        idempotencyKeyRepository.findById(key).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            idempotencyKeyRepository.save(record);
        });
    }
}
