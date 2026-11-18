package com.fsa.expense.email;
import java.util.*;
import java.time.Instant;
import org.springframework.data.jpa.repository.*;
public interface MailboxAccountRepository extends JpaRepository<MailboxAccount,Long> {
    Optional<MailboxAccount> findByTenantId(Long tenantId);
    List<MailboxAccount> findByActiveTrue();
    @Modifying @Query("update MailboxAccount a set a.lockedUntil=:until, a.lockOwner=:owner where a.id=:id and (a.lockedUntil is null or a.lockedUntil<:now)")
    int claim(Long id,Instant now,Instant until,String owner);
    @Modifying @Query("update MailboxAccount a set a.lockedUntil=null, a.lockOwner=null, a.lastPolledAt=:now, a.lastError=:error where a.id=:id and a.lockOwner=:owner")
    int release(Long id,String owner,Instant now,String error);
}
