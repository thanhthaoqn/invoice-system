package com.fsa.expense.email;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.*;
import java.time.*;
@Service
public class EmailQuotaService {
    private final TenantEmailQuotaRepository quotas;
    private final long defaultLimit;
    public EmailQuotaService(TenantEmailQuotaRepository quotas,@Value("${app.email.monthly-invoice-limit:0}") long defaultLimit){this.quotas=quotas;this.defaultLimit=defaultLimit;}
    /** Called while holding tenant's write lock; increment and Invoice commit in same transaction. */
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean consume(Long tenantId){
        var quota=quotas.findById(tenantId).orElseGet(() -> {var q=new TenantEmailQuota();q.setTenantId(tenantId);q.setMonthlyLimit(defaultLimit);return q;});
        String month=YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        if(!month.equals(quota.getMonthKey())){quota.setMonthKey(month);quota.setUsedInvoices(0);}
        if(quota.getMonthlyLimit()>0 && quota.getUsedInvoices()>=quota.getMonthlyLimit())return false;
        quota.setUsedInvoices(quota.getUsedInvoices()+1);quotas.save(quota);return true;
    }
}
