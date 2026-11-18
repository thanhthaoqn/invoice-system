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

    @Override
    @Query("select i from Invoice i where i.id = :id and i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()}")
    Optional<Invoice> findById(@Param("id") Long id);

    Optional<Invoice> findByTenantIdAndContentHash(Long tenantId, String contentHash);
    Optional<Invoice> findByTenantIdAndIdentityKey(Long tenantId, String identityKey);
    List<Invoice> findByTenantIdAndTaxCode(Long tenantId, String taxCode);

    @Override
    @Query("select i from Invoice i where i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()}")
    List<Invoice> findAll();

    @Override
    @Query("select count(i) from Invoice i where i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()}")
    long count();

    @Query("select i from Invoice i where i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()} order by i.createdAt desc")
    List<Invoice> findAllByOrderByCreatedAtDesc();

    @Query("select i from Invoice i where i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()} and i.status = :status order by i.createdAt desc")
    List<Invoice> findByStatusOrderByCreatedAtDesc(InvoiceStatus status);

    @Query("select count(i) from Invoice i where i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()} and i.status = :status")
    long countByStatus(InvoiceStatus status);

    default List<Invoice> findPotentialDuplicates(String invoiceNumber, String taxCode, String template, String series, Long excludeId) {
        String key = com.fsa.expense.service.InvoiceIdentity.key(taxCode, template, series, invoiceNumber);
        if (key == null) return List.of();
        return findAll().stream()
            .filter(i -> !java.util.Objects.equals(i.getId(), excludeId))
            .filter(i -> key.equals(com.fsa.expense.service.InvoiceIdentity.key(i.getTaxCode(), i.getInvoiceTemplate(), i.getInvoiceSeries(), i.getInvoiceNumber())))
            .sorted(java.util.Comparator.comparing(Invoice::getId)).toList();
    }

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()} AND i.contentHash = :contentHash AND (:excludeId IS NULL OR i.id != :excludeId) AND i.status != 'REJECTED'")
    List<Invoice> findByContentHashExcluding(
            @Param("contentHash") String contentHash,
            @Param("excludeId") Long excludeId
    );

    @Query("SELECT SUM(i.totalAmount) FROM Invoice i WHERE i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()} AND i.status = 'APPROVED'")
    BigDecimal sumApprovedTotalAmount();

    @Query("SELECT i.category, SUM(i.totalAmount), COUNT(i) FROM Invoice i WHERE i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()} AND i.status = 'APPROVED' GROUP BY i.category")
    List<Object[]> getSpendByCategory();

    @Query("SELECT i.vendorName, SUM(i.totalAmount), COUNT(i) FROM Invoice i WHERE i.tenantId = ?#{T(com.fsa.expense.auth.TenantContext).id()} AND i.status = 'APPROVED' GROUP BY i.vendorName ORDER BY SUM(i.totalAmount) DESC")
    List<Object[]> getTopVendors();
}
