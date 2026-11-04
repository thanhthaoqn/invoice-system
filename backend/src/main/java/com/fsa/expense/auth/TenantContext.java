package com.fsa.expense.auth;

import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

public final class TenantContext {
    private TenantContext() {}
    public static Long id() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        Object id = attributes.getRequest().getAttribute("tenantId");
        if (!(id instanceof Long)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chưa chọn doanh nghiệp");
        return (Long) id;
    }
    public static String username() {
        return (String) ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getAttribute("username");
    }
}
