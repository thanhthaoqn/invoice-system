package com.fsa.expense.email;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name="emails_processed", uniqueConstraints=@UniqueConstraint(columnNames={"tenantId","messageKey"}))
public class EmailMessage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long tenantId;
    @Column(nullable=false) private String messageKey;
    private String sender;
    @Column(length=1000) private String subject;
    private String state="RECEIVED";
    @JsonIgnore private String rawPath;
    @JsonIgnore private String originalMimePath;
    private Long sessionId;
    private int attempts;
    private String channel;
    private Instant nextRetryAt;
    @Column(nullable=false,columnDefinition="integer default 0") private int retryCount;
    @Column(nullable=false,columnDefinition="integer default 0") private int reprocessCount;
    @Column(nullable=false,columnDefinition="boolean default false") private boolean deadLetter;
    @Column(length=1000) private String note;
    private Instant createdAt=Instant.now();
    private Instant updatedAt=Instant.now();
    @Version private long version;
}
