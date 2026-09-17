package com.enterprise.platform.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_transaction", columnList = "journal_id"),
        @Index(name = "idx_ledger_account", columnList = "account_id")
})
public class LedgerEntry {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "journal_id", nullable = false, updatable = false)
    private UUID journalId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "account_label", nullable = false, updatable = false)
    private String accountLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EntrySide side;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum EntrySide { DEBIT, CREDIT }

    public UUID getId() { return id; }
    public UUID getJournalId() { return journalId; }
    public void setJournalId(UUID journalId) { this.journalId = journalId; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public String getAccountLabel() { return accountLabel; }
    public void setAccountLabel(String accountLabel) { this.accountLabel = accountLabel; }
    public EntrySide getSide() { return side; }
    public void setSide(EntrySide side) { this.side = side; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public String getCurrencyCode() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getCreatedAt() { return createdAt; }
}
