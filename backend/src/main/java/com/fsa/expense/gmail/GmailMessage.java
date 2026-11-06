package com.fsa.expense.gmail;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_gmail_message_connection_id", columnNames = {"connectionId", "gmailMessageId"}))
@Getter @Setter @NoArgsConstructor
public class GmailMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private Long connectionId;
    @Column(nullable = false, length = 200) private String gmailMessageId;
    private String threadId;
    @Column(length = 1000) private String rfcMessageId;
    @Column(length = 1000) private String sender;
    @Column(length = 1000) private String subject;
    private Instant receivedAt;
    @Column(nullable = false, length = 30) private String state = "DISCOVERED";
    private int attempts;
    private Instant nextRetryAt;
    @Column(length = 1000) private String lastError;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
