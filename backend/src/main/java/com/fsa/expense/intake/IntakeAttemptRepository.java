package com.fsa.expense.intake;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface IntakeAttemptRepository extends JpaRepository<IntakeAttempt, Long> {
    List<IntakeAttempt> findByItemIdAndTenantIdOrderById(Long itemId, Long tenantId);
}
