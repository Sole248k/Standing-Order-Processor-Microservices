package com.ewb.payment.repository;

import com.ewb.payment.entity.TransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransferRepository extends JpaRepository<TransferEntity, String> {
    Optional<TransferEntity> findByIdempotencyKey(String idempotencyKey);
}
