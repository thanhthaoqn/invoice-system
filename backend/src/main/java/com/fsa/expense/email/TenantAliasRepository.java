package com.fsa.expense.email;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TenantAliasRepository extends JpaRepository<TenantAlias,Long> {
    boolean existsByTenantIdAndTaxCode(Long tenantId,String taxCode);
}
