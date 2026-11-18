package com.fsa.expense.email;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record EmailPayload(@Size(max=998) String messageId,
        @NotBlank @Email @Size(max=254) String sender,
        @NotNull @Size(max=1000) String subject,
        @Size(max=200000) String html,
        @NotNull @Size(max=20) List<@Valid Attachment> attachments) {
    public record Attachment(@NotBlank @Size(max=200) String name, @NotNull byte[] content) {}
}
