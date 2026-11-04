package com.fsa.expense.intake;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface IntakeItemRepository extends JpaRepository<IntakeItem, Long> {
    List<IntakeItem> findBySessionIdAndTenantIdOrderById(Long sessionId, Long tenantId);
    Optional<IntakeItem> findByIdAndTenantId(Long id, Long tenantId);
    Optional<IntakeItem> findFirstByTenantIdAndContentHashAndStateOrderById(Long tenantId, String contentHash, String state);
}
