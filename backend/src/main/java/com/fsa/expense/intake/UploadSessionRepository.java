package com.fsa.expense.intake;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UploadSessionRepository extends JpaRepository<UploadSession, Long> {
    List<UploadSession> findTop100ByTenantIdOrderByCreatedAtDesc(Long tenantId);
    Optional<UploadSession> findByIdAndTenantId(Long id, Long tenantId);
}
