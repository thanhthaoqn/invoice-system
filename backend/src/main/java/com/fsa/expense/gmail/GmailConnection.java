package com.fsa.expense.gmail;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_gmail_connection_tenant", columnNames = "tenantId"))
@Getter @Setter @NoArgsConstructor
public class GmailConnection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private Long connectedByUserId;
    @Column(nullable = false, length = 320) private String emailAddress;
    @Column(nullable = false, length = 4000) private String encryptedRefreshToken;
    @Column(length = 1000) private String grantedScopes;
    @Column(nullable = false, length = 30) private String state = "ACTIVE";
    private String labelId;
    private Instant lastSyncAt;
    @Column(length = 1000) private String lastError;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
