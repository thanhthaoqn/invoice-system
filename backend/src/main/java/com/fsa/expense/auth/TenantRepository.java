package com.fsa.expense.auth;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByTaxCode(String taxCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> lockById(Long id);
}
