package com.fsa.expense.email;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="email_ocr_outbox")
public class OcrJob {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    @Column(unique=true) private Long itemId;
    private String state="PENDING";
    private Instant createdAt=Instant.now();
    private Instant updatedAt=Instant.now();
    @Version private long version;
}
