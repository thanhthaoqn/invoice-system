package com.fsa.expense.auth;

import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService auth;
    private final TenantRepository tenants;
    public AuthInterceptor(AuthService auth, TenantRepository tenants) { this.auth = auth; this.tenants = tenants; }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        response.setHeader("Cache-Control", "no-store");
        if ("OPTIONS".equals(request.getMethod()) || path.equals("/api/auth/login")) return true;
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) throw AuthService.error(401, "Vui lòng đăng nhập");
        AppUser user = auth.user((Long) session.getAttribute("userId"));
        boolean mutate = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        if (mutate && !java.util.Objects.equals(session.getAttribute("csrf"), request.getHeader("X-CSRF-Token"))) throw AuthService.error(403, "Phiên thao tác không hợp lệ");
        if (path.startsWith("/api/auth/")) return true;
        Long tenantId = (Long) session.getAttribute("tenantId");
        if (tenantId == null) throw AuthService.error(403, "Vui lòng chọn doanh nghiệp");
        Membership membership = auth.membership(user.getId(), tenantId);
        if (mutate) {
            if (user.isMustChangePassword() || "GRACE".equals(tenants.findById(tenantId).orElseThrow().getSubscriptionStatus())) throw AuthService.error(403, "Phiên chỉ được phép đọc dữ liệu");
            boolean review = path.endsWith("/review");
            if (review ? !membership.isInvoiceReview() : !membership.isInvoiceCreate()) throw AuthService.error(403, "Không có quyền thực hiện thao tác");
        }
        request.setAttribute("tenantId", tenantId);
        request.setAttribute("tenantTaxCode", tenants.findById(tenantId).orElseThrow().getTaxCode());
        request.setAttribute("username", user.getUsername());
        response.setHeader("Cache-Control", "no-store");
        return true;
    }
}
