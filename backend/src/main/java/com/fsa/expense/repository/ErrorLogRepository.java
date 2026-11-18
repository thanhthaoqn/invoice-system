package com.fsa.expense.repository;

import com.fsa.expense.entity.ErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    @Query("select e from ErrorLog e where e.invoiceId in (select i.id from Invoice i where i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()}) order by e.createdAt desc")
    List<ErrorLog> findAllByOrderByCreatedAtDesc();

    @Override
    @Query("select e from ErrorLog e where e.invoiceId in (select i.id from Invoice i where i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()})")
    List<ErrorLog> findAll();

    @Override
    @Query("select count(e) from ErrorLog e where e.invoiceId in (select i.id from Invoice i where i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()})")
    long count();

    List<ErrorLog> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    @Query("SELECT e.fieldName, COUNT(e) FROM ErrorLog e GROUP BY e.fieldName ORDER BY COUNT(e) DESC")
    List<Object[]> getErrorCountByField();
}
