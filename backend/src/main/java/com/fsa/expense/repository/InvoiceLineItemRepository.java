package com.fsa.expense.repository;

import com.fsa.expense.entity.InvoiceLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, Long> {
    List<InvoiceLineItem> findByInvoiceIdOrderByLineNumberAsc(Long invoiceId);
}
