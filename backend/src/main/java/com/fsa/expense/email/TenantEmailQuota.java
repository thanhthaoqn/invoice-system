package com.fsa.expense.email;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="tenant_email_quota")
public class TenantEmailQuota {
    @Id private Long tenantId;
    private String monthKey;
    private long usedInvoices;
    private long monthlyLimit;
}
