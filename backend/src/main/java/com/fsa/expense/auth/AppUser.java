package com.fsa.expense.auth;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String username;
    @JsonIgnore @Column(nullable = false, length = 500) private String passwordHash;
    private boolean active = true;
    private boolean mustChangePassword;
    private int failedAttempts;
    private Instant failureWindow;
}
