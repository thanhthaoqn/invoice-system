package com.fsa.expense.auth;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "tenantId"}))
public class Membership {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long tenantId;
    private boolean invoiceCreate;
    private boolean invoiceReview;
}
