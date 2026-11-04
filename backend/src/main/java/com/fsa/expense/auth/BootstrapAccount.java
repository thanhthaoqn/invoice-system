package com.fsa.expense.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

/** Optional, explicit first-account provisioning. Never ships a default password. */
@Component
public class BootstrapAccount implements ApplicationRunner {
    private final Environment env;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    public BootstrapAccount(Environment env, UserRepository users, TenantRepository tenants, MembershipRepository memberships) {
        this.env = env; this.users = users; this.tenants = tenants; this.memberships = memberships;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        String username = env.getProperty("app.bootstrap.username", "").trim().toLowerCase(Locale.ROOT);
        if (username.isBlank()) return;
        String tax = env.getRequiredProperty("app.bootstrap.tax-code");
        String name = env.getRequiredProperty("app.bootstrap.tenant-name");
        AppUser user = users.findByUsername(username).orElse(null);
        if (user == null) {
            String password = env.getRequiredProperty("app.bootstrap.password");
            if (password.length() < 12 || password.length() > 256) throw new IllegalArgumentException("Bootstrap password must contain 12–256 characters");
            user = new AppUser(); user.setUsername(username); user.setPasswordHash(Passwords.hash(password)); users.save(user);
        }
        Tenant tenant = tenants.findByTaxCode(tax).orElse(null);
        if (tenant == null) { tenant = new Tenant(); tenant.setName(name); tenant.setTaxCode(tax); tenants.save(tenant); }
        if (memberships.findByUserIdAndTenantId(user.getId(), tenant.getId()).isEmpty()) {
            Membership m = new Membership(); m.setUserId(user.getId()); m.setTenantId(tenant.getId()); m.setInvoiceCreate(true); m.setInvoiceReview(true); memberships.save(m);
        }
    }
}
