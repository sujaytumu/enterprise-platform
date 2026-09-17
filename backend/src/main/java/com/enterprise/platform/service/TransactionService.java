package com.enterprise.platform.service;

import com.enterprise.platform.dto.AuthorizeRequest;
import com.enterprise.platform.model.*;
import com.enterprise.platform.repository.AccountRepository;
import com.enterprise.platform.repository.CardRepository;
import com.enterprise.platform.repository.LedgerEntryRepository;
import com.enterprise.platform.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransactionService(AccountRepository accountRepository,
                              CardRepository cardRepository,
                              TransactionRepository transactionRepository,
                              LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public Transaction authorize(AuthorizeRequest req) {
        if (req.amount == null || req.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (req.amount.scale() > 2) {
            throw new IllegalArgumentException("Amount supports at most two decimal places");
        }

        Account account = accountRepository.findByIdForUpdate(req.accountId)
                .orElseThrow(() -> new NoSuchElementException("Account not found"));

        Card card = null;
        if (req.cardId != null) {
            card = cardRepository.findById(req.cardId)
                    .orElseThrow(() -> new NoSuchElementException("Card not found"));
            if (!card.getAccount().getId().equals(account.getId())) {
                throw new IllegalArgumentException("Card does not belong to the selected account");
            }
        }

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setCard(card);
        tx.setAmount(req.amount);
        tx.setCurrency(account.getCurrency());
        tx.setMerchant(req.merchant == null || req.merchant.isBlank() ? "Account transaction" : req.merchant.trim());

        String declineReason = null;
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            declineReason = "Account is not active (" + account.getStatus() + ")";
        } else if (card != null && card.getStatus() != Card.CardStatus.ACTIVE) {
            declineReason = "Card is not active (" + card.getStatus() + ")";
        } else if (account.getBalance().compareTo(req.amount) < 0) {
            declineReason = "Insufficient funds";
        }

        if (declineReason != null) {
            tx.setStatus(Transaction.TransactionStatus.DECLINED);
            tx.setDeclineReason(declineReason);
        } else {
            account.setBalance(account.getBalance().subtract(req.amount));
            accountRepository.save(account);
            tx.setStatus(Transaction.TransactionStatus.APPROVED);
        }

        Transaction saved = transactionRepository.save(tx);
        if (saved.getStatus() == Transaction.TransactionStatus.APPROVED) {
            UUID journalId = UUID.randomUUID();
            LedgerEntry debit = new LedgerEntry();
            debit.setJournalId(journalId);
            debit.setAccountId(account.getId());
            debit.setAccountLabel(account.getAccountNumber());
            debit.setSide(LedgerEntry.EntrySide.DEBIT);
            debit.setAmount(req.amount);
            debit.setCurrency(account.getCurrency());

            LedgerEntry credit = new LedgerEntry();
            credit.setJournalId(journalId);
            credit.setAccountLabel("PLATFORM_SETTLEMENT");
            credit.setSide(LedgerEntry.EntrySide.CREDIT);
            credit.setAmount(req.amount);
            credit.setCurrency(account.getCurrency());

            ledgerEntryRepository.save(debit);
            ledgerEntryRepository.save(credit);
        }
        return saved;
    }
}
