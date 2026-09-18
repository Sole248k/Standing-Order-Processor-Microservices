package com.ewb.payment.repository;

import com.ewb.payment.entity.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, Long> {
    List<LedgerEntryEntity> findByTransferReference(String transferReference);
    List<LedgerEntryEntity> findByAccountId(String accountId);
}
