package com.fsa.expense.gmail;

import com.fsa.expense.auth.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class GmailOAuthService {
    private final GmailProperties properties;
    private final GmailAdminGrantRepository grants;
    private final GmailOAuthStateRepository states;
    private final GmailConnectionRepository connections;
    private final GoogleOAuthClient google;
    private final TokenCipher cipher;
    private final SecureRandom random = new SecureRandom();

    public GmailOAuthService(GmailProperties properties, GmailAdminGrantRepository grants,
            GmailOAuthStateRepository states, GmailConnectionRepository connections,
            GoogleOAuthClient google, TokenCipher cipher) {
        this.properties=properties; this.grants=grants; this.states=states; this.connections=connections;
        this.google=google; this.cipher=cipher;
    }

    public void requireAdmin(Long userId, Long tenantId) {
        if (!grants.existsByUserIdAndTenantId(userId, tenantId)) throw AuthService.error(403, "Chỉ quản trị viên Gmail của doanh nghiệp được thực hiện thao tác này");
    }

    @Transactional
    public String authorizationUrl(Long userId, Long tenantId) {
        requireAdmin(userId, tenantId); properties.requireConfigured();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        GmailOAuthState state = new GmailOAuthState(); state.setTokenHash(hash(raw)); state.setUserId(userId);
        state.setTenantId(tenantId); state.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10))); states.save(state);
        return google.authorizationUrl(raw);
    }

    @Transactional
    public GmailConnection callback(String rawState, String code) {
        if (rawState == null || code == null || rawState.length() > 500 || code.length() > 4000) throw AuthService.error(400, "Phản hồi OAuth không hợp lệ");
        GmailOAuthState state = states.findByTokenHash(hash(rawState)).orElseThrow(() -> AuthService.error(400, "OAuth state không hợp lệ"));
        if (state.getConsumedAt()!=null || state.getExpiresAt().isBefore(Instant.now())) throw AuthService.error(400, "OAuth state đã hết hạn hoặc đã sử dụng");
        state.setConsumedAt(Instant.now()); states.save(state);
        GoogleOAuthClient.Tokens tokens = google.exchange(code);
        if (tokens.scope() == null || !Arrays.asList(tokens.scope().split(" ")).contains(GoogleOAuthClient.GMAIL_READONLY))
            throw AuthService.error(400, "Tài khoản chưa cấp quyền gmail.readonly");
        String email = google.emailAddress(tokens.accessToken());
        GmailConnection connection = connections.findByTenantId(state.getTenantId()).orElseGet(GmailConnection::new);
        String refresh = tokens.refreshToken();
        if ((refresh == null || refresh.isBlank()) && connection.getEncryptedRefreshToken() == null)
            throw AuthService.error(400, "Google không trả refresh token; hãy kết nối lại và cấp quyền offline");
        connection.setTenantId(state.getTenantId()); connection.setConnectedByUserId(state.getUserId());
        connection.setEmailAddress(email); connection.setGrantedScopes(tokens.scope()); connection.setState("ACTIVE");
        connection.setLastError(null); connection.setLabelId(null);
        if (refresh != null && !refresh.isBlank()) connection.setEncryptedRefreshToken(cipher.encrypt(refresh));
        return connections.save(connection);
    }

    @Transactional
    public void disconnect(Long userId, Long tenantId) {
        requireAdmin(userId, tenantId);
        connections.findByTenantId(tenantId).ifPresent(connection -> {
            if (connection.getEncryptedRefreshToken()!=null && !connection.getEncryptedRefreshToken().isBlank())
                google.revoke(cipher.decrypt(connection.getEncryptedRefreshToken()));
            connection.setEncryptedRefreshToken(""); connection.setState("DISCONNECTED");
            connection.setLabelId(null); connection.setLastError(null); connections.save(connection);
        });
    }

    public Status status(Long userId, Long tenantId) {
        boolean admin=grants.existsByUserIdAndTenantId(userId,tenantId);
        var value=connections.findByTenantId(tenantId);
        return value.map(c -> new Status(properties.isEnabled(),admin,"ACTIVE".equals(c.getState()),c.getEmailAddress(),c.getState(),c.getLastSyncAt(),c.getLastError()))
            .orElseGet(() -> new Status(properties.isEnabled(),admin,false,null,"NOT_CONNECTED",null,null));
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public record Status(boolean configured, boolean admin, boolean connected, String emailAddress, String state, Instant lastSyncAt, String lastError) {}
}
