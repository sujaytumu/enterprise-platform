package com.enterprise.platform.repository;

import com.enterprise.platform.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
    List<LedgerEntry> findByJournalIdOrderByCreatedAtAsc(UUID journalId);
}
