package com.fsa.expense.gmail;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GmailApiClient {
    private static final String BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    private final RestClient http = RestClient.create();

    public JsonNode labels(String accessToken) { return get(accessToken, BASE + "/labels"); }
    public JsonNode messages(String accessToken,String labelId,String pageToken,int pageSize) {
        var uri=UriComponentsBuilder.fromUriString(BASE+"/messages").queryParam("labelIds",labelId)
            .queryParam("maxResults",Math.max(1,Math.min(pageSize,500)));
        if (pageToken!=null && !pageToken.isBlank()) uri.queryParam("pageToken",pageToken);
        return get(accessToken,uri.build().encode().toUriString());
    }
    public JsonNode message(String accessToken,String id) {
        return get(accessToken,UriComponentsBuilder.fromUriString(BASE+"/messages/{id}").queryParam("format","full").buildAndExpand(id).encode().toUriString());
    }
    public byte[] attachment(String accessToken,String messageId,String attachmentId) {
        String uri=UriComponentsBuilder.fromUriString(BASE+"/messages/{message}/attachments/{attachment}")
            .buildAndExpand(messageId,attachmentId).encode().toUriString();
        JsonNode value=get(accessToken,uri);
        String data=value.path("data").asText("");
        if (data.isBlank()) throw new IllegalStateException("Gmail attachment không có dữ liệu");
        return decode(data);
    }
    public static byte[] decode(String data) {
        try { return java.util.Base64.getUrlDecoder().decode(data); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("Gmail trả attachment base64url không hợp lệ",e); }
    }
    private JsonNode get(String token,String uri) {
        JsonNode value=http.get().uri(uri).header("Authorization","Bearer "+token).retrieve().body(JsonNode.class);
        if (value==null) throw new IllegalStateException("Gmail API không trả dữ liệu");
        return value;
    }
}
