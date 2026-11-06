package com.fsa.expense.gmail;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class GmailMimeExtractor {
    public MessageData read(JsonNode message) {
        JsonNode payload=message.path("payload");
        Map<String,String> headers=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        payload.path("headers").forEach(h -> headers.put(h.path("name").asText(),h.path("value").asText()));
        List<Part> parts=new ArrayList<>(); walk(payload,"0",parts);
        Instant received=null;
        try { received=Instant.ofEpochMilli(Long.parseLong(message.path("internalDate").asText())); } catch(RuntimeException ignored) {}
        return new MessageData(message.path("id").asText(),message.path("threadId").asText(null),headers.get("Message-ID"),
            headers.get("From"),headers.get("Subject"),received,parts);
    }
    private void walk(JsonNode node,String path,List<Part> output) {
        String partId=node.path("partId").asText(path);
        String key=partId.isBlank() ? path : partId;
        String filename=node.path("filename").asText("");
        String mime=node.path("mimeType").asText("application/octet-stream");
        JsonNode body=node.path("body");
        String attachmentId=body.path("attachmentId").asText(null);
        String inline=body.path("data").asText(null);
        if (!filename.isBlank() && ((attachmentId!=null && !attachmentId.isBlank()) || (inline!=null && !inline.isBlank())))
            output.add(new Part(key,filename,mime,attachmentId,inline,body.path("size").asLong()));
        int index=0;
        for (JsonNode child : node.path("parts")) walk(child,path+"."+(index++),output);
    }
    public record MessageData(String id,String threadId,String rfcMessageId,String sender,String subject,Instant receivedAt,List<Part> parts) {}
    public record Part(String key,String fileName,String mimeType,String attachmentId,String inlineData,long declaredSize) {}
}
