package com.fsa.expense.email;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="risk_enterprises") @Getter @Setter @NoArgsConstructor
public class RiskEnterprise {
    @Id private String taxCode;
    @Column(length=1000) private String reason;
    private boolean active=true;
}
