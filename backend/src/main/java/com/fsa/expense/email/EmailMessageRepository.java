package com.fsa.expense.email;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EmailMessageRepository extends JpaRepository<EmailMessage,Long> {
    Optional<EmailMessage> findByTenantIdAndMessageKey(Long tenantId,String messageKey);
    Optional<EmailMessage> findByIdAndTenantId(Long id,Long tenantId);
    List<EmailMessage> findTop100ByTenantIdOrderByCreatedAtDesc(Long tenantId);
    boolean existsByTenantIdAndSessionId(Long tenantId,Long sessionId);
    List<EmailMessage> findTop50ByStateAndNextRetryAtBeforeOrderByNextRetryAt(String state,java.time.Instant now);
    List<EmailMessage> findTop50ByDeadLetterTrueOrderByUpdatedAtDesc();
}
