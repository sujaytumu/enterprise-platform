package com.enterprise.platform.repository;

import com.enterprise.platform.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {
    List<Card> findByAccountId(UUID accountId);
}
