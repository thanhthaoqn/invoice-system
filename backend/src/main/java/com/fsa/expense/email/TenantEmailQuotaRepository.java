package com.fsa.expense.email;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TenantEmailQuotaRepository extends JpaRepository<TenantEmailQuota,Long>{}
