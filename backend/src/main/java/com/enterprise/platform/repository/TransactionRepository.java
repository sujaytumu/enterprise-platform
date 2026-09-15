package com.enterprise.platform.repository;

import com.enterprise.platform.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
    List<Transaction> findAllByOrderByCreatedAtDesc();
}
