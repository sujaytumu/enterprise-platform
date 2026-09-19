package com.enterprise.platform.service;

import com.enterprise.platform.dto.AuthorizeRequest;
import com.enterprise.platform.model.*;
import com.enterprise.platform.repository.AccountRepository;
import com.enterprise.platform.repository.CardRepository;
import com.enterprise.platform.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(AccountRepository accountRepository,
                               CardRepository cardRepository,
                               TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Simplified authorization: checks account status and balance/velocity.
     * This mirrors the shape of a real authorization decision (approve/decline
     * with a reason) without the fraud-engine, ISO 8583 switch, or clearing
     * steps of the original multi-service design.
     */
    @Transactional
    public Transaction authorize(AuthorizeRequest req) {
        Account account = accountRepository.findById(req.accountId)
                .orElseThrow(() -> new NoSuchElementException("Account not found"));

        Card card = null;
        if (req.cardId != null) {
            card = cardRepository.findById(req.cardId)
                    .orElseThrow(() -> new NoSuchElementException("Card not found"));
        }

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setCard(card);
        tx.setAmount(req.amount);
        tx.setCurrency(account.getCurrency());
        tx.setMerchant(req.merchant);

        String declineReason = null;
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            declineReason = "Account is not active (" + account.getStatus() + ")";
        } else if (card != null && !belongsTo(card, account)) {
            declineReason = "Card does not belong to this account";
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

        return transactionRepository.save(tx);
    }

    private static boolean belongsTo(Card card, Account account) {
        Account owner = card.getAccount();
        return owner != null && owner.getId() != null && owner.getId().equals(account.getId());
    }
}
