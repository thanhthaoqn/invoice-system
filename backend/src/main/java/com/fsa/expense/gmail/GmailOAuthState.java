package com.fsa.expense.gmail;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor
public class GmailOAuthState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private Instant expiresAt;
    private Instant consumedAt;
}
