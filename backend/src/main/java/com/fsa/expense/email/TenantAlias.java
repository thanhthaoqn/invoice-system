package com.fsa.expense.email;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="tenant_aliases",uniqueConstraints=@UniqueConstraint(columnNames={"tenantId","taxCode"}))
@Getter @Setter @NoArgsConstructor
public class TenantAlias {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long tenantId;
    @Column(nullable=false) private String taxCode;
}
