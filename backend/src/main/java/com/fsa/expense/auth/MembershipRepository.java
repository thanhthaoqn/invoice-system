package com.fsa.expense.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MembershipRepository extends JpaRepository<Membership, Long> {
    List<Membership> findByUserId(Long userId);
    Optional<Membership> findByUserIdAndTenantId(Long userId, Long tenantId);
}
