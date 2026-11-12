package com.fsa.expense.email;

import com.fsa.expense.auth.*;
import com.fsa.expense.intake.*;
import com.fsa.expense.repository.InvoiceRepository;
import com.fsa.expense.service.FileStorageService;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.net.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:emailworkflow;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","app.bootstrap.username=","app.email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","app.email.poll-initial-delay-ms=3600000"})
class EmailWorkflowTest {
    @Autowired TenantRepository tenants;
    @Autowired EmailMessageRepository emails;
    @Autowired MailboxAccountRepository accounts;
    @Autowired EmailIntakeService intake;
    @Autowired EmailPollerService poller;
    @Autowired MailboxService settings;
    @Autowired MailPasswordCipher cipher;
    @Autowired InvoiceRepository invoices;
    @Autowired IntakeItemRepository items;
    @Autowired IntakeAttemptRepository attempts;
    @Autowired UploadSessionRepository sessions;
    @Autowired TenantEmailQuotaRepository quotas;
    @Autowired SystemInboxService system;
    @Autowired TenantMailboxRepository routes;
    @Autowired RiskEnterpriseRepository risks;
    @Autowired InvoiceXmlParser parser;
    @SpyBean FileStorageService storage;
    @SpyBean InvoiceLinkDownloader downloader;
    static final Path STORAGE;
    static {try{STORAGE=Files.createTempDirectory("email-workflow-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry registry){registry.add("app.storage.upload-dir",() -> STORAGE.toString());}
    Tenant tenant;
    @BeforeEach void setup(){
        emails.deleteAll();accounts.deleteAll();routes.deleteAll();risks.deleteAll();quotas.deleteAll();
        invoices.deleteAll();attempts.deleteAll();items.deleteAll();sessions.deleteAll();tenants.deleteAll();
        tenant=new Tenant();tenant.setName("Email Tenant");tenant.setTaxCode("0100000001");tenants.save(tenant);
    }
    private <T> T asTenant(java.util.function.Supplier<T> action){AtomicReference<T> result=new AtomicReference<>();TenantContext.runAs(tenant.getId(),() -> result.set(action.get()));return result.get();}
    private byte[] xml(String number){return ("<HDon><DLHDon><TTChung><KHMSHDon>1</KHMSHDon><KHHDon>C26TAA</KHHDon><SHDon>"+number+"</SHDon><NLap>2026-11-10</NLap></TTChung><NDHDon><NBan><Ten>Seller</Ten><MST>0300000001</MST></NBan><NMua><MST>0100000001</MST></NMua><TToan><TgTCThue>100000</TgTCThue><TgTThue>10000</TgTThue><TgTTTBSo>110000</TgTTTBSo></TToan></NDHDon></DLHDon></HDon>").getBytes(StandardCharsets.UTF_8);}
    private EmailPayload payload(String id){return new EmailPayload(id,"vendor@example.com","Invoice",null,List.of(new EmailPayload.Attachment("invoice.xml",xml(id))));}
    private MailboxAccount mailbox(){return asTenant(() -> settings.save(new MailboxService.Settings("billing@example.com","imap.gmail.com",993,"application-password",true,"vendor@example.com")));}
    private MimeMessage mail()throws Exception{
        MimeMessage message=new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("vendor@example.com"));message.setSubject("Invoice");
        MimeMultipart multipart=new MimeMultipart();MimeBodyPart attachment=new MimeBodyPart();
        attachment.setDataHandler(new jakarta.activation.DataHandler(new jakarta.mail.util.ByteArrayDataSource(xml("mime"),"application/xml")));attachment.setFileName("invoice.xml");multipart.addBodyPart(attachment);
        message.setContent(multipart);message.saveChanges();return message;
    }
    @Test void passwordIsRandomizedAuthenticatedAndTenantBound(){
        String first=cipher.encrypt("secret",tenant.getId()),second=cipher.encrypt("secret",tenant.getId());
        assertThat(first).isNotEqualTo(second).doesNotContain("secret");
        assertThat(cipher.decrypt(first,tenant.getId())).isEqualTo("secret");
        assertThatThrownBy(() -> cipher.decrypt(first,tenant.getId()+1)).isInstanceOf(IllegalStateException.class);
        byte[] tampered=Base64.getDecoder().decode(first);tampered[tampered.length-1]^=1;
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(tampered),tenant.getId())).isInstanceOf(IllegalStateException.class);
    }
    @Test void settingsNeverSerializePasswordAndPreserveItOnEdit()throws Exception{
        var first=mailbox();String encrypted=first.getPasswordEncrypted();
        var updated=asTenant(() -> settings.save(new MailboxService.Settings(first.getEmail(),first.getHost(),993,"",false,"VENDOR@example.com")));
        assertThat(updated.getPasswordEncrypted()).isEqualTo(encrypted);
        String serialized=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(updated);
        assertThat(serialized).doesNotContain("passwordEncrypted").doesNotContain("application-password").doesNotContain(encrypted);
    }
    @Test void unapprovedImapHostIsRejected(){
        assertThatThrownBy(() -> asTenant(() -> settings.save(new MailboxService.Settings("billing@example.com","127.0.0.1",993,"secret",true,"")))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void mimeIsRetainedBeforeSeenAndInvoiceGetsEmailSource()throws Exception{
        var account=mailbox();var message=mail();
        asTenant(() -> {try{return poller.processMessage(message,account);}catch(Exception e){throw new RuntimeException(e);}});
        assertThat(message.isSet(Flags.Flag.SEEN)).isTrue();
        assertThat(emails.findAll().getFirst().getOriginalMimePath()).isNotBlank();
        assertThat(asTenant(() -> invoices.findAll())).singleElement().satisfies(i -> {assertThat(i.getSource()).isEqualTo("EMAIL_POLLER");assertThat(i.getSignatureState()).isEqualTo("MISSING");});
    }
    @Test void originalStorageFailureNeverSetsSeenOrCommitsClaim()throws Exception{
        var account=mailbox();var message=mail();
        doThrow(new IllegalStateException("disk full")).when(storage).storeBytes(any());
        assertThatThrownBy(() -> asTenant(() -> {try{return poller.processMessage(message,account);}catch(Exception e){throw new RuntimeException(e);}})).isInstanceOf(RuntimeException.class);
        assertThat(message.isSet(Flags.Flag.SEEN)).isFalse();assertThat(emails.count()).isZero();assertThat(asTenant(() -> invoices.count())).isZero();
    }
    @Test void rejectedSenderIsAuditedAndCanReprocessAfterWhitelistCorrection(){
        var account=mailbox();account.setAllowedSenders("another@example.com");account=accounts.save(account);
        var rejected=asTenant(() -> intake.receive(payload("whitelist"),"OWN_IMAP",false));
        assertThat(rejected.getState()).isEqualTo("REJECTED_SENDER");assertThat(rejected.getRawPath()).isNotBlank();assertThat(asTenant(() -> invoices.count())).isZero();
        account.setAllowedSenders("vendor@example.com");accounts.save(account);
        var result=asTenant(() -> intake.retry(rejected.getId()));
        assertThat(result.getState()).isEqualTo("SUCCESS");assertThat(result.getReprocessCount()).isEqualTo(1);
    }
    @Test void transientDownloadRetriesThreeTimesThenDeadLettersButManualRecoveryRemainsAvailable()throws Exception{
        URI link=URI.create("https://meinvoice.vn/download.xml");
        doThrow(new InvoiceLinkDownloader.TransientDownloadException("timeout")).when(downloader).download(link);
        var body=new EmailPayload("retry","vendor@example.com","Invoice",link.toString(),List.of());
        var result=asTenant(() -> intake.receive(body));assertThat(result.getState()).isEqualTo("PAUSE");
        for(int count=1;count<=3;count++){
            result.setNextRetryAt(java.time.Instant.now().minusSeconds(1));emails.save(result);Long id=result.getId();
            result=asTenant(() -> intake.retry(id,true));assertThat(result.getRetryCount()).isEqualTo(count);
        }
        assertThat(result.getState()).isEqualTo("FAILED");assertThat(result.isDeadLetter()).isTrue();
        doReturn(new EmailPayload.Attachment("download.xml",xml("recovered"))).when(downloader).download(link);
        Long id=result.getId();result=asTenant(() -> intake.retry(id));
        assertThat(result.getState()).isEqualTo("SUCCESS");assertThat(result.isDeadLetter()).isFalse();assertThat(result.getReprocessCount()).isEqualTo(1);
    }
    @Test void quotaIsConsumedExactlyOnceAndRiskSupplierIsFlagged(){
        var quota=new TenantEmailQuota();quota.setTenantId(tenant.getId());quota.setMonthKey(java.time.YearMonth.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toString());quota.setMonthlyLimit(1);quotas.save(quota);
        var risk=new RiskEnterprise();risk.setTaxCode("0300000001");risk.setReason("Test risk feed");risks.save(risk);
        assertThat(asTenant(() -> intake.receive(payload("first"))).getState()).isEqualTo("SUCCESS");
        var duplicate=new EmailPayload("other-message","vendor@example.com","Invoice",null,payload("first").attachments());
        assertThat(asTenant(() -> intake.receive(duplicate)).getState()).isEqualTo("SUCCESS");
        assertThat(quotas.findById(tenant.getId()).orElseThrow().getUsedInvoices()).isEqualTo(1);
        assertThat(asTenant(() -> intake.receive(payload("over-limit"))).getState()).isEqualTo("PAUSE");
        assertThat(asTenant(() -> invoices.findAll())).singleElement().satisfies(i -> assertThat(i.getTaxRiskState()).isEqualTo("HIGH_TAX_RISK"));
    }
    @Test void systemRoutingRespectsRecipientHeaderPriorityAndQuarantinesAmbiguity()throws Exception{
        var route=new TenantMailbox();route.setTenantId(tenant.getId());route.setAddress("tenant@invoices.example");routes.save(route);
        var message=mail();message.setHeader("Delivered-To",route.getAddress());message.setHeader("To","wrong@example.com");
        assertThat(system.route(message)).contains(tenant.getId());
        var other=new TenantMailbox();other.setTenantId(tenant.getId()+1);other.setAddress("other@invoices.example");routes.save(other);
        message.setHeader("Delivered-To",route.getAddress()+", "+other.getAddress());assertThat(system.route(message)).isEmpty();
    }
    @Test void parserAcceptsLowercaseTagsAndDoesNotClaimUnsignedXmlIsVerified(){
        String lower=new String(xml("lower"),StandardCharsets.UTF_8).replaceAll("(?<=<)(/?)([A-Za-z0-9]+)","$1$2").toLowerCase(Locale.ROOT);
        var invoice=parser.parse(lower.getBytes(StandardCharsets.UTF_8));assertThat(invoice.getInvoiceNumber()).isEqualTo("lower");assertThat(invoice.getSignatureState()).isEqualTo("MISSING");
    }
    @Test void linkPolicyRejectsPrivateIpAndLookalikeDomains()throws Exception{
        for(String ip:List.of("127.0.0.1","10.0.0.1","172.16.1.1","192.168.1.1","169.254.169.254","100.64.0.1","0.0.0.0","::1","fc00::1","::ffff:127.0.0.1"))assertThat(PublicNetwork.publicAddress(InetAddress.getByName(ip))).as(ip).isFalse();
        assertThat(PublicNetwork.publicAddress(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(downloader.extract("https://meinvoice.vn.evil.test/a https://meinvoice.vn@evil.test/a http://meinvoice.vn/a https://meinvoice.vn:8443/a")).isEmpty();
        assertThatThrownBy(() -> downloader.download(URI.create("https://127.0.0.1/invoice.xml"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void vnptPortalLinkFailureDoesNotPreventLaterDirectDownload()throws Exception{
        URI portal=URI.create("https://fpt-tt78.vnpt-invoice.com.vn/Email/EmailInvoiceView?token=test");
        URI xmlLink=URI.create("https://fpt-tt78.vnpt-invoice.com.vn/invoice/getinvoice?token=test");
        doReturn(List.of(portal,xmlLink)).when(downloader).extract(anyString());
        doThrow(new IllegalArgumentException("HTML portal")).when(downloader).download(portal);
        doReturn(new EmailPayload.Attachment("download.xml",xml("vnpt"))).when(downloader).download(xmlLink);
        var body=new EmailPayload("vnpt-links","vendor@example.com","Invoice",portal+" "+xmlLink,List.of());
        assertThat(asTenant(() -> intake.receive(body)).getState()).isEqualTo("SUCCESS");
        assertThat(asTenant(() -> invoices.count())).isEqualTo(1);
    }
    @Test void vnptExtractorSkipsHomepageAndPrioritizesInvoiceDownload(){
        var links=downloader.extract("https://fpt-tt78.vnpt-invoice.com.vn/ https://fpt-tt78.vnpt-invoice.com.vn/Email/EmailInvoiceView?token=test https://fpt-tt78.vnpt-invoice.com.vn/invoice/getinvoice?token=test");
        assertThat(links).hasSize(2);assertThat(links.getFirst().getPath()).isEqualTo("/invoice/getinvoice");
    }
}
