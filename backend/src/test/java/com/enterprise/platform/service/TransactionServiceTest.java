package com.enterprise.platform.service;

import com.enterprise.platform.dto.AuthorizeRequest;
import com.enterprise.platform.model.Account;
import com.enterprise.platform.model.Card;
import com.enterprise.platform.model.LedgerEntry;
import com.enterprise.platform.model.Transaction;
import com.enterprise.platform.repository.AccountRepository;
import com.enterprise.platform.repository.CardRepository;
import com.enterprise.platform.repository.LedgerEntryRepository;
import com.enterprise.platform.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionServiceTest {

    private AccountRepository accountRepository;
    private CardRepository cardRepository;
    private TransactionRepository transactionRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        cardRepository = mock(CardRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        service = new TransactionService(accountRepository, cardRepository, transactionRepository, ledgerEntryRepository);

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Account activeAccount(BigDecimal balance) {
        Account a = new Account();
        a.setBalance(balance);
        a.setStatus(Account.AccountStatus.ACTIVE);
        a.setCurrency("USD");
        return a;
    }

    @Test
    void approvesWhenBalanceIsSufficient() {
        UUID accountId = UUID.randomUUID();
        Account account = activeAccount(new BigDecimal("100.00"));
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        AuthorizeRequest req = new AuthorizeRequest();
        req.accountId = accountId;
        req.amount = new BigDecimal("40.00");
        req.merchant = "Coffee Shop";

        Transaction tx = service.authorize(req);

        assertEquals(Transaction.TransactionStatus.APPROVED, tx.getStatus());
        assertEquals(new BigDecimal("60.00"), account.getBalance());
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    void declinesWhenBalanceIsInsufficient() {
        UUID accountId = UUID.randomUUID();
        Account account = activeAccount(new BigDecimal("10.00"));
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        AuthorizeRequest req = new AuthorizeRequest();
        req.accountId = accountId;
        req.amount = new BigDecimal("40.00");

        Transaction tx = service.authorize(req);

        assertEquals(Transaction.TransactionStatus.DECLINED, tx.getStatus());
        assertEquals("Insufficient funds", tx.getDeclineReason());
        assertEquals(new BigDecimal("10.00"), account.getBalance());
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void declinesWhenAccountIsFrozen() {
        UUID accountId = UUID.randomUUID();
        Account account = activeAccount(new BigDecimal("500.00"));
        account.setStatus(Account.AccountStatus.FROZEN);
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        AuthorizeRequest req = new AuthorizeRequest();
        req.accountId = accountId;
        req.amount = new BigDecimal("5.00");

        Transaction tx = service.authorize(req);

        assertEquals(Transaction.TransactionStatus.DECLINED, tx.getStatus());
        assertTrue(tx.getDeclineReason().contains("not active"));
    }

    @Test
    void declinesWhenCardIsBlocked() {
        UUID accountId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        Account account = activeAccount(new BigDecimal("500.00"));
        Card card = new Card();
        card.setStatus(Card.CardStatus.BLOCKED);
        card.setAccount(account);

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));

        AuthorizeRequest req = new AuthorizeRequest();
        req.accountId = accountId;
        req.cardId = cardId;
        req.amount = new BigDecimal("5.00");

        Transaction tx = service.authorize(req);

        assertEquals(Transaction.TransactionStatus.DECLINED, tx.getStatus());
        assertTrue(tx.getDeclineReason().contains("Card"));
    }

    @Test
    void transferMovesFundsBetweenAccounts() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        Account from = activeAccount(new BigDecimal("100.00"));
        Account to = activeAccount(new BigDecimal("20.00"));
        when(accountRepository.findByIdForUpdate(fromId)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdForUpdate(toId)).thenReturn(Optional.of(to));

        Transaction debitTx = service.transfer(fromId, toId, new BigDecimal("30.00"), "Rent");

        assertEquals(Transaction.TransactionStatus.APPROVED, debitTx.getStatus());
        assertEquals(new BigDecimal("70.00"), from.getBalance());
        assertEquals(new BigDecimal("50.00"), to.getBalance());
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    void transferDeclinesWhenSourceHasInsufficientFunds() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        Account from = activeAccount(new BigDecimal("10.00"));
        Account to = activeAccount(new BigDecimal("20.00"));
        when(accountRepository.findByIdForUpdate(fromId)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdForUpdate(toId)).thenReturn(Optional.of(to));

        Transaction tx = service.transfer(fromId, toId, new BigDecimal("30.00"), null);

        assertEquals(Transaction.TransactionStatus.DECLINED, tx.getStatus());
        assertEquals(new BigDecimal("10.00"), from.getBalance());
        assertEquals(new BigDecimal("20.00"), to.getBalance());
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void creditTopUpIncreasesBalance() {
        UUID accountId = UUID.randomUUID();
        Account account = activeAccount(new BigDecimal("50.00"));
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        Transaction tx = service.creditTopUp(accountId, new BigDecimal("25.00"), "pay_test123");

        assertEquals(Transaction.TransactionStatus.APPROVED, tx.getStatus());
        assertEquals(Transaction.TransactionType.CREDIT, tx.getType());
        assertEquals(new BigDecimal("75.00"), account.getBalance());
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }
}
