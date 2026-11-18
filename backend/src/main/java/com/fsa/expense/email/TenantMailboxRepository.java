package com.fsa.expense.email;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TenantMailboxRepository extends JpaRepository<TenantMailbox,Long>{
    Optional<TenantMailbox> findByTenantId(Long tenantId);
    Optional<TenantMailbox> findByAddressAndActiveTrue(String address);
}
