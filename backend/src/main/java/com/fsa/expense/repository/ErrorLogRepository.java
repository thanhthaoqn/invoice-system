package com.fsa.expense.repository;

import com.fsa.expense.entity.ErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    List<ErrorLog> findAllByOrderByCreatedAtDesc();

    List<ErrorLog> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    @Query("SELECT e.fieldName, COUNT(e) FROM ErrorLog e GROUP BY e.fieldName ORDER BY COUNT(e) DESC")
    List<Object[]> getErrorCountByField();
}
