package com.fsa.expense.intake;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
public class IntakeItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long sessionId;
    @Column(length=1000) private String fileName;
    @JsonIgnore private String storagePath;
    private String contentHash;
    private long fileSize;
    private boolean xmlDocument;
    private String documentType;
    @JsonIgnore @Column(columnDefinition="TEXT") private String rawExtraction;
    @JsonIgnore @Column(columnDefinition="TEXT") private String draftJson;
    @JsonIgnore @Column(columnDefinition="TEXT") private String confirmedSnapshot;
    private String confirmedBy;
    private Instant confirmedAt;
    public boolean isRetryable() {
        return "PARSE_FAILED".equals(state) || "PROCESSING_FAILED".equals(state) || "OCR_FAILED".equals(state)
            || ("RECEIVED".equals(state) && updatedAt.isBefore(Instant.now().minusSeconds(300)));
    }
    private String state = "RECEIVED";
    @Column(length=1000) private String message;
    private Long invoiceId;
    private Long originalItemId;
    private int attempts;
    private Instant updatedAt = Instant.now();
    @Version private long version;
}
