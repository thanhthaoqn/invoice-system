package com.fsa.expense.intake;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
public class UploadSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long tenantId;
    private String submittedBy;
    private Instant createdAt = Instant.now();
    private int uploadedFiles;
    private String state = "PROCESSING";
}
