package com.fsa.expense.email;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface OcrJobRepository extends JpaRepository<OcrJob,Long>{
    List<OcrJob> findTop25ByStateOrderById(String state);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select j from OcrJob j where j.id=:id") Optional<OcrJob> lockById(Long id);
    Optional<OcrJob> findByItemId(Long itemId);
}
