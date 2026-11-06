package com.fsa.expense.gmail;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GoogleOAuthClient {
    public static final String GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly";
    private final GmailProperties properties;
    private final RestClient http = RestClient.create();

    public GoogleOAuthClient(GmailProperties properties) { this.properties = properties; }

    public String authorizationUrl(String state) {
        properties.requireConfigured();
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
            .queryParam("client_id", properties.getClientId())
            .queryParam("redirect_uri", properties.getRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", GMAIL_READONLY)
            .queryParam("access_type", "offline")
            .queryParam("include_granted_scopes", "true")
            .queryParam("prompt", "consent")
            .queryParam("state", state).build().encode().toUriString();
    }

    public Tokens exchange(String code) {
        var form = baseForm();
        form.add("code", code);
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("grant_type", "authorization_code");
        return token(form);
    }

    public Tokens refresh(String refreshToken) {
        var form = baseForm();
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        return token(form);
    }

    public String emailAddress(String accessToken) {
        JsonNode profile = http.get().uri("https://gmail.googleapis.com/gmail/v1/users/me/profile")
            .header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class);
        String email = profile == null ? "" : profile.path("emailAddress").asText("");
        if (email.isBlank() || email.length() > 320) throw new IllegalStateException("Google không trả về địa chỉ Gmail hợp lệ");
        return email;
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        var form = new LinkedMultiValueMap<String, String>(); form.add("token", token);
        try { http.post().uri("https://oauth2.googleapis.com/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity(); }
        catch (RuntimeException ignored) { /* Local disconnect must still complete. */ }
    }

    private LinkedMultiValueMap<String, String> baseForm() {
        properties.requireConfigured();
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", properties.getClientId()); form.add("client_secret", properties.getClientSecret());
        return form;
    }
    private Tokens token(LinkedMultiValueMap<String, String> form) {
        Tokens value = http.post().uri("https://oauth2.googleapis.com/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Tokens.class);
        if (value == null || value.accessToken() == null || value.accessToken().isBlank()) throw new IllegalStateException("Google không trả access token");
        return value;
    }

    public record Tokens(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("refresh_token") String refreshToken,
                         @JsonProperty("expires_in") Long expiresIn,
                         String scope,
                         @JsonProperty("token_type") String tokenType) {}
}
