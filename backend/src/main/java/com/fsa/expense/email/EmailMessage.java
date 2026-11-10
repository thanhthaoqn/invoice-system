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
    private Long sessionId;
    private int attempts;
    @Column(length=1000) private String note;
    private Instant createdAt=Instant.now();
    private Instant updatedAt=Instant.now();
    @Version private long version;
}
