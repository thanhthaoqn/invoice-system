package com.fsa.expense.gmail;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface GmailConnectionRepository extends JpaRepository<GmailConnection, Long> {
    Optional<GmailConnection> findByTenantId(Long tenantId);
    List<GmailConnection> findByState(String state);
}
