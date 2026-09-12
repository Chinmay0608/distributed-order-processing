package com.distributed.orderservice;

import com.distributed.orderservice.exception.IdempotencyPayloadMismatchException;
import com.distributed.orderservice.exception.OrderAlreadyProcessingException;
import com.distributed.orderservice.model.IdempotencyRecord;
import com.distributed.orderservice.model.IdempotencyStatus;
import com.distributed.orderservice.repository.IdempotencyKeyRepository;
import com.distributed.orderservice.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IdempotencyServiceUnitTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(idempotencyKeyRepository);
    }

    @Test
    void testIdempotency_NewKeyStartsInProgress() {
        String key = "test-key-1";
        String hash = idempotencyService.computePayloadHash("prod-1", 1);
        when(idempotencyKeyRepository.findById(key)).thenReturn(Optional.empty());

        Optional<IdempotencyRecord> result = idempotencyService.checkOrStart(key, hash);

        assertTrue(result.isEmpty());
        verify(idempotencyKeyRepository, times(1)).save(any(IdempotencyRecord.class));
    }

    @Test
    void testIdempotency_DuplicateKeyReturnsCachedResponse() {
        String key = "test-key-2";
        String hash = idempotencyService.computePayloadHash("prod-1", 1);
        IdempotencyRecord completed = new IdempotencyRecord(key, IdempotencyStatus.COMPLETED, hash);
        completed.setResponseStatusCode(201);
        when(idempotencyKeyRepository.findById(key)).thenReturn(Optional.of(completed));

        Optional<IdempotencyRecord> result = idempotencyService.checkOrStart(key, hash);

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.COMPLETED, result.get().getStatus());
        verify(idempotencyKeyRepository, never()).save(any(IdempotencyRecord.class));
    }

    @Test
    void testIdempotency_ConcurrentInProgressReturns409() {
        String key = "test-key-3";
        String hash = idempotencyService.computePayloadHash("prod-1", 1);
        IdempotencyRecord inProgress = new IdempotencyRecord(key, IdempotencyStatus.IN_PROGRESS, hash);
        when(idempotencyKeyRepository.findById(key)).thenReturn(Optional.of(inProgress));

        assertThrows(OrderAlreadyProcessingException.class, () ->
                idempotencyService.checkOrStart(key, hash));
    }

    @Test
    void testIdempotency_ReplayWithMismatchedPayloadReturns422() {
        String key = "test-key-4";
        String originalHash = idempotencyService.computePayloadHash("prod-1", 1);
        String alteredHash = idempotencyService.computePayloadHash("prod-1", 5); // Different quantity!

        IdempotencyRecord completed = new IdempotencyRecord(key, IdempotencyStatus.COMPLETED, originalHash);
        when(idempotencyKeyRepository.findById(key)).thenReturn(Optional.of(completed));

        assertThrows(IdempotencyPayloadMismatchException.class, () ->
                idempotencyService.checkOrStart(key, alteredHash));
    }
}
