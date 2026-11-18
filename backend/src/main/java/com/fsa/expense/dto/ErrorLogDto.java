package com.fsa.expense.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorLogDto {
    private Long id;
    private Long invoiceId;
    private String invoiceNumber;
    private String fieldName;
    private String originalAiValue;
    private String correctedValue;
    private String reason;
    private String correctedBy;
    private LocalDateTime createdAt;
}
