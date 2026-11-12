package com.fsa.expense.email;

import com.fsa.expense.auth.*;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class MailboxService {
    private final MailboxAccountRepository accounts;
    private final TenantRepository tenants;
    private final MailPasswordCipher cipher;
    private final Set<String> hosts;
    public MailboxService(MailboxAccountRepository accounts,TenantRepository tenants,MailPasswordCipher cipher,
            @Value("${app.email.imap-hosts:imap.gmail.com,outlook.office365.com,imap.mail.yahoo.com}") String hosts){
        this.accounts=accounts;this.tenants=tenants;this.cipher=cipher;this.hosts=new HashSet<>();
        for(String host:hosts.split(","))this.hosts.add(host.trim().toLowerCase(Locale.ROOT));
    }
    public record Settings(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=253) String host,
        @Min(993) @Max(993) int port,@Size(max=500) String password,boolean active,@NotNull @Size(max=4000) String allowedSenders){}
    public record View(MailboxAccount account,boolean encryptionConfigured,Set<String> allowedHosts){}
    public View get(){return new View(accounts.findByTenantId(TenantContext.id()).orElse(null),cipher.configured(),Set.copyOf(hosts));}
    @Transactional
    public MailboxAccount save(Settings input){
        tenants.lockById(TenantContext.id()).orElseThrow();
        String host=input.host().trim().toLowerCase(Locale.ROOT);
        if(!hosts.contains(host))throw AuthService.error(400,"IMAP host chưa được quản trị hệ thống cho phép");
        var account=accounts.findByTenantId(TenantContext.id()).orElseGet(MailboxAccount::new);
        if(account.getLockedUntil()!=null && account.getLockedUntil().isAfter(java.time.Instant.now()))throw AuthService.error(409,"Hộp thư đang được đồng bộ; thử lại sau");
        boolean changed=!input.email().equalsIgnoreCase(Objects.toString(account.getEmail(),"")) || !host.equals(account.getHost());
        account.setTenantId(TenantContext.id());
        if(input.password()!=null && !input.password().isBlank())account.setPasswordEncrypted(cipher.encrypt(input.password(),account.getTenantId()));
        else if(changed || account.getPasswordEncrypted()==null)throw AuthService.error(400,"Nhập mật khẩu ứng dụng cho kết nối mới");
        var normalized=new LinkedHashSet<String>();
        for(String value:input.allowedSenders().split("[,;\\s]+")){
            if(value.isBlank())continue;
            if(!value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))throw AuthService.error(400,"Địa chỉ whitelist không hợp lệ");
            normalized.add(value.toLowerCase(Locale.ROOT));
        }
        account.setEmail(input.email().trim().toLowerCase(Locale.ROOT));account.setHost(host);account.setPort(993);
        account.setActive(input.active());account.setAllowedSenders(String.join(",",normalized));account.setLastError(null);
        return accounts.saveAndFlush(account);
    }
    public static boolean senderAllowed(MailboxAccount account,String sender){
        return Arrays.stream(account.getAllowedSenders().split(",")).anyMatch(s -> !s.isBlank() && s.equalsIgnoreCase(sender));
    }
}
