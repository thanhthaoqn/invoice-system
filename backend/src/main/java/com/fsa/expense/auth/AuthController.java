package com.fsa.expense.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final TenantRepository tenants;
    public AuthController(AuthService auth, TenantRepository tenants) { this.auth = auth; this.tenants = tenants; }
    public record Login(@NotBlank @Size(max=255) String username, @NotBlank @Size(max=256) String password) {}
    public record Select(@NotNull Long tenantId) {}
    public record SessionView(String username, List<Tenant> tenants, Long tenantId, boolean invoiceCreate, boolean invoiceReview, String csrfToken) {}
    @PostMapping("/login")
    public SessionView login(@Valid @RequestBody Login login, HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        AppUser user = auth.authenticate(login.username(), login.password());
        if (user == null) throw AuthService.error(401, "Tài khoản hoặc mật khẩu không hợp lệ");
        var available = auth.tenants(user.getId());
        if (available.isEmpty()) throw AuthService.error(403, "Không có doanh nghiệp được phép sử dụng");
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", user.getId());
        session.setAttribute("csrf", UUID.randomUUID().toString());
        if (available.size() == 1) session.setAttribute("tenantId", available.getFirst().getId());
        return view(session);
    }
    @GetMapping("/me") public SessionView me(HttpSession session) { return view(session); }
    @PostMapping("/tenant") public SessionView select(@Valid @RequestBody Select input, HttpSession session) {
        auth.membership((Long) session.getAttribute("userId"), input.tenantId());
        session.setAttribute("tenantId", input.tenantId());
        return view(session);
    }
    @PostMapping("/logout") public void logout(HttpSession session) { session.invalidate(); }
    private SessionView view(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) throw AuthService.error(401, "Vui lòng đăng nhập");
        AppUser user = auth.user(userId);
        Long tenantId = (Long) session.getAttribute("tenantId");
        Membership m = tenantId == null ? null : auth.membership(userId, tenantId);
        boolean write = tenantId != null && !user.isMustChangePassword() && !"GRACE".equals(tenants.findById(tenantId).orElseThrow().getSubscriptionStatus());
        return new SessionView(user.getUsername(), auth.tenants(userId), tenantId, write && m.isInvoiceCreate(), write && m.isInvoiceReview(), (String) session.getAttribute("csrf"));
    }
}
