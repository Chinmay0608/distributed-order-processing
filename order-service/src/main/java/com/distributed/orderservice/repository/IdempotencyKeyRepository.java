package com.distributed.orderservice.repository;

import com.distributed.orderservice.model.IdempotencyRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyKeyRepository extends MongoRepository<IdempotencyRecord, String> {
}
