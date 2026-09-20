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
            postLedgerPair(account.getId(), account.getAccountNumber(), LedgerEntry.EntrySide.DEBIT,
                    null, "PLATFORM_SETTLEMENT", LedgerEntry.EntrySide.CREDIT, req.amount, account.getCurrency());
        }
        return saved;
    }

    /** Moves funds between two accounts, with real double-entry ledger postings. */
    @Transactional
    public Transaction transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount supports at most two decimal places");
        }
        if (fromAccountId == null || toAccountId == null || fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }

        // Lock accounts in a stable order to avoid deadlocks between concurrent transfers.
        UUID first = fromAccountId.compareTo(toAccountId) < 0 ? fromAccountId : toAccountId;
        UUID second = fromAccountId.compareTo(toAccountId) < 0 ? toAccountId : fromAccountId;
        Account a1 = accountRepository.findByIdForUpdate(first)
                .orElseThrow(() -> new NoSuchElementException("Account not found"));
        Account a2 = accountRepository.findByIdForUpdate(second)
                .orElseThrow(() -> new NoSuchElementException("Account not found"));
        Account from = first.equals(fromAccountId) ? a1 : a2;
        Account to = first.equals(fromAccountId) ? a2 : a1;

        Transaction tx = new Transaction();
        tx.setAccount(from);
        tx.setAmount(amount);
        tx.setCurrency(from.getCurrency());
        tx.setType(Transaction.TransactionType.DEBIT);
        tx.setMerchant((note == null || note.isBlank() ? "Transfer" : note.trim()) + " → " + to.getAccountNumber());

        String declineReason = null;
        if (from.getStatus() != Account.AccountStatus.ACTIVE) {
            declineReason = "Source account is not active (" + from.getStatus() + ")";
        } else if (to.getStatus() != Account.AccountStatus.ACTIVE) {
            declineReason = "Destination account is not active (" + to.getStatus() + ")";
        } else if (from.getBalance().compareTo(amount) < 0) {
            declineReason = "Insufficient funds";
        }

        if (declineReason != null) {
            tx.setStatus(Transaction.TransactionStatus.DECLINED);
            tx.setDeclineReason(declineReason);
            return transactionRepository.save(tx);
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        accountRepository.save(from);
        accountRepository.save(to);
        tx.setStatus(Transaction.TransactionStatus.APPROVED);
        Transaction savedDebit = transactionRepository.save(tx);

        Transaction credit = new Transaction();
        credit.setAccount(to);
        credit.setAmount(amount);
        credit.setCurrency(to.getCurrency());
        credit.setType(Transaction.TransactionType.CREDIT);
        credit.setStatus(Transaction.TransactionStatus.APPROVED);
        credit.setMerchant((note == null || note.isBlank() ? "Transfer" : note.trim()) + " ← " + from.getAccountNumber());
        transactionRepository.save(credit);

        postLedgerPair(from.getId(), from.getAccountNumber(), LedgerEntry.EntrySide.DEBIT,
                to.getId(), to.getAccountNumber(), LedgerEntry.EntrySide.CREDIT, amount, from.getCurrency());
        return savedDebit;
    }

    /** Credits an account after a verified Razorpay payment (wallet top-up). */
    @Transactional
    public Transaction creditTopUp(UUID accountId, BigDecimal amount, String reference) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NoSuchElementException("Account not found"));
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active (" + account.getStatus() + ")");
        }
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setAmount(amount);
        tx.setCurrency(account.getCurrency());
        tx.setType(Transaction.TransactionType.CREDIT);
        tx.setStatus(Transaction.TransactionStatus.APPROVED);
        tx.setMerchant("Razorpay top-up (" + reference + ")");
        Transaction saved = transactionRepository.save(tx);

        postLedgerPair(account.getId(), account.getAccountNumber(), LedgerEntry.EntrySide.CREDIT,
                null, "PLATFORM_SETTLEMENT", LedgerEntry.EntrySide.DEBIT, amount, account.getCurrency());
        return saved;
    }

    private void postLedgerPair(UUID primaryAccountId, String primaryLabel, LedgerEntry.EntrySide primarySide,
                                 UUID counterAccountId, String counterLabel, LedgerEntry.EntrySide counterSide,
                                 BigDecimal amount, String currency) {
        UUID journalId = UUID.randomUUID();
        LedgerEntry primary = new LedgerEntry();
        primary.setJournalId(journalId);
        primary.setAccountId(primaryAccountId);
        primary.setAccountLabel(primaryLabel);
        primary.setSide(primarySide);
        primary.setAmount(amount);
        primary.setCurrency(currency);

        LedgerEntry counter = new LedgerEntry();
        counter.setJournalId(journalId);
        counter.setAccountId(counterAccountId);
        counter.setAccountLabel(counterLabel);
        counter.setSide(counterSide);
        counter.setAmount(amount);
        counter.setCurrency(currency);

        ledgerEntryRepository.save(primary);
        ledgerEntryRepository.save(counter);
    }
}
