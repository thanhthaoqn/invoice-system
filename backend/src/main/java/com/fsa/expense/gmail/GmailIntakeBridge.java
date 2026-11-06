package com.fsa.expense.gmail;

import com.fsa.expense.intake.IntakeService;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class GmailIntakeBridge {
    private final IntakeService intake;
    public GmailIntakeBridge(IntakeService intake) { this.intake=intake; }
    public List<IntakeService.ExternalResult> ingest(Long tenantId,String mailbox,List<Document> documents) {
        List<IntakeService.ExternalResult> results=new ArrayList<>();
        for (Document document : documents) results.addAll(intake.ingestExternal(tenantId,"gmail:"+mailbox,
            List.of(new IntakeService.ExternalDocument(document.fileName(),document.bytes()))));
        return results;
    }
    public record Document(String fileName,byte[] bytes) {}
}
