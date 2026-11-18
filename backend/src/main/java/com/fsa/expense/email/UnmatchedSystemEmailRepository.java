package com.fsa.expense.email;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UnmatchedSystemEmailRepository extends JpaRepository<UnmatchedSystemEmail,Long>{boolean existsByMessageHash(String hash);}
