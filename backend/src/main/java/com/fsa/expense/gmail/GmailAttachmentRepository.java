package com.fsa.expense.gmail;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GmailAttachmentRepository extends JpaRepository<GmailAttachment, Long> {
    Optional<GmailAttachment> findByMessageIdAndPartKey(Long messageId, String partKey);
}
