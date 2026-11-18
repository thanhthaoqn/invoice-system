package com.fsa.expense.intake;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Getter @Setter @NoArgsConstructor
public class IntakeAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long itemId;
    private String state;
    @Column(length=1000) private String message;
    private Instant createdAt = Instant.now();
}
