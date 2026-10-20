package com.fsa.expense.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "error_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "original_ai_value", columnDefinition = "TEXT")
    private String originalAiValue;

    @Column(name = "corrected_value", columnDefinition = "TEXT")
    private String correctedValue;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "corrected_by", length = 100)
    private String correctedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
