package com.fsa.expense.email;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RiskEnterpriseRepository extends JpaRepository<RiskEnterprise,String>{boolean existsByTaxCodeAndActiveTrue(String taxCode);}
