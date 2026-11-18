package com.fsa.expense.email;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="tenant_mailboxes") @Getter @Setter @NoArgsConstructor
public class TenantMailbox {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true) private Long tenantId;
    @Column(nullable=false,unique=true) private String address;
    private boolean active=true;
}
