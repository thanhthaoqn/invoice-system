package com.fsa.expense.auth;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class Tenant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column(nullable = false, unique = true) private String taxCode;
    @Column(nullable = false) private String status = "ACTIVE";
    @Column(nullable = false) private String subscriptionStatus = "ACTIVE";
}
