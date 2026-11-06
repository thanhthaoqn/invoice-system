package com.fsa.expense.gmail;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_gmail_attachment_part", columnNames = {"messageId", "partKey"}))
@Getter @Setter @NoArgsConstructor
public class GmailAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private Long messageId;
    @Column(nullable = false, length = 500) private String partKey;
    @Column(length = 1000) private String fileName;
    @Column(length = 200) private String mimeType;
    private long fileSize;
    @Column(length = 64) private String contentHash;
    private Long intakeItemId;
    private Long invoiceId;
    @Column(nullable = false, length = 30) private String state = "DISCOVERED";
    @Column(length = 1000) private String lastError;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
