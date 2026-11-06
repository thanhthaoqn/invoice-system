package com.fsa.expense.gmail;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GmailMessageRepository extends JpaRepository<GmailMessage, Long> {
    Optional<GmailMessage> findByConnectionIdAndGmailMessageId(Long connectionId, String gmailMessageId);
}
