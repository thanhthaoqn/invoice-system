package com.fsa.expense.gmail;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailAdminGrantRepository extends JpaRepository<GmailAdminGrant, Long> {
    boolean existsByUserIdAndTenantId(Long userId, Long tenantId);
}
