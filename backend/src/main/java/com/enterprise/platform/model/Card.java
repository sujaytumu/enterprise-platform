package com.enterprise.platform.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // Demo-only synthetic token — never a real PAN. See README for why this
    // is illustrative, not a real tokenization vault.
    @Column(nullable = false, unique = true)
    private String cardToken;

    @Column(nullable = false)
    private String last4;

    @Column(nullable = false)
    private String expiry; // MM/YY

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status = CardStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum CardStatus { ACTIVE, BLOCKED, EXPIRED }

    public UUID getId() { return id; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public String getCardToken() { return cardToken; }
    public void setCardToken(String cardToken) { this.cardToken = cardToken; }
    public String getLast4() { return last4; }
    public void setLast4(String last4) { this.last4 = last4; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public CardStatus getStatus() { return status; }
    public void setStatus(CardStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
