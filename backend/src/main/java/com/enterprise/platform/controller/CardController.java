package com.enterprise.platform.controller;

import com.enterprise.platform.dto.IssueCardRequest;
import com.enterprise.platform.model.Account;
import com.enterprise.platform.model.Card;
import com.enterprise.platform.repository.AccountRepository;
import com.enterprise.platform.repository.CardRepository;
import com.enterprise.platform.security.AuthUtil;
import com.enterprise.platform.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Year;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private static final SecureRandom RNG = new SecureRandom();

    public CardController(CardRepository cardRepository, AccountRepository accountRepository) {
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<Card> list(@RequestParam(required = false) UUID accountId) {
        CurrentUser user = AuthUtil.current();
        if (accountId != null) {
            AccountController.requireOwnerOrAdmin(requireAccount(accountId));
            return cardRepository.findByAccountId(accountId);
        }
        if (user.isAdmin()) return cardRepository.findAll();
        return accountRepository.findByOwnerId(user.userId()).stream()
                .flatMap(a -> cardRepository.findByAccountId(a.getId()).stream())
                .toList();
    }

    // Demo-only issuance: generates a synthetic token and last4. This is
    // illustrative of the card-management flow, not a real issuing/vaulting
    // implementation — see README.
    @PostMapping
    public ResponseEntity<Card> issue(@Valid @RequestBody IssueCardRequest req) {
        Account account = requireAccount(req.accountId);
        AccountController.requireOwnerOrAdmin(account);

        Card card = new Card();
        card.setAccount(account);
        String last4 = String.format("%04d", RNG.nextInt(10000));
        card.setLast4(last4);
        card.setCardToken("tok_" + UUID.randomUUID().toString().replace("-", ""));
        int expYear = (Year.now().getValue() + 4) % 100;
        card.setExpiry(String.format("%02d/%02d", RNG.nextInt(12) + 1, expYear));
        return ResponseEntity.ok(cardRepository.save(card));
    }

    @PostMapping("/{id}/block")
    public Card block(@PathVariable UUID id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Card not found"));
        AccountController.requireOwnerOrAdmin(card.getAccount());
        card.setStatus(Card.CardStatus.BLOCKED);
        return cardRepository.save(card);
    }

    private Account requireAccount(UUID id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId is required");
        return accountRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Account not found"));
    }
}
