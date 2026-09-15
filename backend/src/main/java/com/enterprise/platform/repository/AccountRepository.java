package com.enterprise.platform.repository;

import com.enterprise.platform.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
}
