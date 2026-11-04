package com.fsa.expense.auth;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface UserRepository extends JpaRepository<AppUser, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findByUsername(String username);
}
