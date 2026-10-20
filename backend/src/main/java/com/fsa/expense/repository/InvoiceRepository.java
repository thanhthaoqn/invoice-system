package com.fsa.expense.repository;

import com.fsa.expense.entity.Invoice;
import com.fsa.expense.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findAllByOrderByCreatedAtDesc();

    List<Invoice> findByStatusOrderByCreatedAtDesc(InvoiceStatus status);

    long countByStatus(InvoiceStatus status);

    @Query("SELECT i FROM Invoice i WHERE i.invoiceNumber = :invoiceNumber AND i.taxCode = :taxCode AND (:excludeId IS NULL OR i.id != :excludeId) AND i.status != 'REJECTED'")
    List<Invoice> findPotentialDuplicates(
            @Param("invoiceNumber") String invoiceNumber,
            @Param("taxCode") String taxCode,
            @Param("excludeId") Long excludeId
    );

    @Query("SELECT i FROM Invoice i WHERE i.contentHash = :contentHash AND (:excludeId IS NULL OR i.id != :excludeId) AND i.status != 'REJECTED'")
    List<Invoice> findByContentHashExcluding(
            @Param("contentHash") String contentHash,
            @Param("excludeId") Long excludeId
    );

    @Query("SELECT SUM(i.totalAmount) FROM Invoice i WHERE i.status = 'APPROVED'")
    BigDecimal sumApprovedTotalAmount();

    @Query("SELECT i.category, SUM(i.totalAmount), COUNT(i) FROM Invoice i WHERE i.status = 'APPROVED' GROUP BY i.category")
    List<Object[]> getSpendByCategory();

    @Query("SELECT i.vendorName, SUM(i.totalAmount), COUNT(i) FROM Invoice i WHERE i.status = 'APPROVED' GROUP BY i.vendorName ORDER BY SUM(i.totalAmount) DESC")
    List<Object[]> getTopVendors();
}
