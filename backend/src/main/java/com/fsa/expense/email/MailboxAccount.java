package com.fsa.expense.email;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity @Table(name="tenant_emails") @Getter @Setter @NoArgsConstructor
public class MailboxAccount {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true) private Long tenantId;
    private String email;
    private String host;
    private int port=993;
    @JsonIgnore @Column(length=2000) private String passwordEncrypted;
    @Column(length=4000) private String allowedSenders="";
    private boolean active;
    private Instant lastPolledAt;
    @Column(length=1000) private String lastError;
    @JsonIgnore private Instant lockedUntil;
    @JsonIgnore private String lockOwner;
    @Version private long version;
}
