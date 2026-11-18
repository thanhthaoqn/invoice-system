package com.fsa.expense.auth;

import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

public final class TenantContext {
    private record Background(Long tenantId, String username) {}
    private static final ThreadLocal<Background> BACKGROUND = new ThreadLocal<>();
    public static void runAs(Long tenantId, Runnable action) {
        Background previous=BACKGROUND.get(); BACKGROUND.set(new Background(tenantId,"email-poller"));
        try { action.run(); } finally { if(previous==null) BACKGROUND.remove(); else BACKGROUND.set(previous); }
    }
    private TenantContext() {}
    public static Long id() {
        if(BACKGROUND.get()!=null) return BACKGROUND.get().tenantId();
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        Object id = attributes.getRequest().getAttribute("tenantId");
        if (!(id instanceof Long)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chưa chọn doanh nghiệp");
        return (Long) id;
    }
    public static String username() {
        if(BACKGROUND.get()!=null) return BACKGROUND.get().username();
        return (String) ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getAttribute("username");
    }
    private static final ThreadLocal<Boolean> EMAIL = new ThreadLocal<>();
    public static boolean emailIntake() { return Boolean.TRUE.equals(EMAIL.get()); }
    public static void withEmail(Runnable action) {
        Boolean previous=EMAIL.get(); EMAIL.set(true);
        try { action.run(); } finally {if(previous==null) EMAIL.remove();else EMAIL.set(previous);}
    }
}
