package com.fsa.expense.gmail;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GmailOAuthStateRepository extends JpaRepository<GmailOAuthState, Long> {
    Optional<GmailOAuthState> findByTokenHash(String tokenHash);
}
