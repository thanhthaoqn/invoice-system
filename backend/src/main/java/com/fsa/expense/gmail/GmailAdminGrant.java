package com.fsa.expense.gmail;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_gmail_admin_user_tenant", columnNames = {"userId", "tenantId"}))
@Getter @Setter @NoArgsConstructor
public class GmailAdminGrant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long tenantId;
}
