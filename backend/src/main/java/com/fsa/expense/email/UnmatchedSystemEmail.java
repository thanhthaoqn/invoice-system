package com.fsa.expense.email;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="system_inbox_unmatched") @Getter @Setter @NoArgsConstructor
public class UnmatchedSystemEmail {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true,nullable=false) private String messageHash;
    private String originalPath;
    private String reason;
    private Instant createdAt=Instant.now();
}
