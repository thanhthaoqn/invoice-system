package com.fsa.expense.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class AuthService {
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final TenantRepository tenants;
    private final String dummyHash = Passwords.hash(UUID.randomUUID().toString());
    public AuthService(UserRepository users, MembershipRepository memberships, TenantRepository tenants) {
        this.users = users; this.memberships = memberships; this.tenants = tenants;
    }
    // Return failure instead of throwing so the failed-attempt transaction is committed.
    @Transactional
    public AppUser authenticate(String username, String password) {
        AppUser user = users.findByUsername(username.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null) { Passwords.matches(password, dummyHash); return null; }
        Instant now = Instant.now();
        if (user.getFailureWindow() == null || !now.isBefore(user.getFailureWindow().plus(Duration.ofMinutes(15)))) {
            user.setFailureWindow(now); user.setFailedAttempts(0);
        }
        if (user.getFailedAttempts() >= 10) return null;
        boolean correct = Passwords.matches(password, user.getPasswordHash());
        if (!correct || !user.isActive()) { user.setFailedAttempts(user.getFailedAttempts() + 1); return null; }
        user.setFailedAttempts(0); user.setFailureWindow(null);
        return user;
    }
    public AppUser user(Long id) { return users.findById(id).filter(AppUser::isActive).orElseThrow(() -> error(401, "Phiên không hợp lệ")); }
    public List<Tenant> tenants(Long userId) {
        return memberships.findByUserId(userId).stream().map(m -> tenants.findById(m.getTenantId()).orElse(null))
            .filter(Objects::nonNull).filter(this::allowed).toList();
    }
    public boolean allowed(Tenant tenant) {
        return Set.of("CONFIGURING", "READY", "ACTIVE").contains(tenant.getStatus()) && !"EXPIRED".equals(tenant.getSubscriptionStatus());
    }
    public Membership membership(Long userId, Long tenantId) {
        Membership membership = memberships.findByUserIdAndTenantId(userId, tenantId).orElseThrow(() -> error(403, "Không có quyền truy cập doanh nghiệp"));
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> error(403, "Doanh nghiệp không khả dụng"));
        if (!allowed(tenant)) throw error(403, "Doanh nghiệp không khả dụng");
        return membership;
    }
    public static org.springframework.web.server.ResponseStatusException error(int status, String message) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(status), message);
    }
}
