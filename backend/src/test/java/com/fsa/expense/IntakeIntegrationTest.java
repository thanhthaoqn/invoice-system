package com.fsa.expense;

import com.fasterxml.jackson.databind.*;
import com.fsa.expense.auth.*;
import com.fsa.expense.entity.*;
import com.fsa.expense.intake.*;
import com.fsa.expense.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:intake;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop", "app.bootstrap.username="})
@AutoConfigureMockMvc
class IntakeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired MembershipRepository memberships;
    @Autowired InvoiceRepository invoices;
    @Autowired IntakeItemRepository items;
    @Autowired UploadSessionRepository sessions;
    @Autowired IntakeAttemptRepository attempts;
    @Autowired ErrorLogRepository errors;
    @Autowired IntakeLimits limits;
    @Autowired InvoiceXmlParser parser;
    @Autowired com.fsa.expense.email.EmailIntakeService emailIntake;
    @Autowired com.fsa.expense.email.EmailMessageRepository emailMessages;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @SpyBean com.fsa.expense.service.FileStorageService storage;
    @SpyBean com.fsa.expense.service.AuditValidationService audit;
    @SpyBean com.fsa.expense.service.AiExtractionClient extraction;
    static final String PASSWORD="a-long-test-password";
    static final Path STORAGE;
    static { try { STORAGE=Files.createTempDirectory("smart-expense-intake-test-"); } catch(IOException e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("app.storage.upload-dir", () -> STORAGE.toString()); }
    Tenant a; Tenant b; AppUser user;
    record Login(MockHttpSession session, String csrf) {}
    @BeforeEach void setup() {
        emailMessages.deleteAll(); errors.deleteAll(); invoices.deleteAll(); attempts.deleteAll(); items.deleteAll(); sessions.deleteAll(); memberships.deleteAll(); users.deleteAll(); tenants.deleteAll();
        a=tenant("Công ty A", "0100000001"); b=tenant("Công ty B", "0100000002");
        user=new AppUser(); user.setUsername("accountant"); user.setPasswordHash(Passwords.hash(PASSWORD)); users.save(user);
        member(a, true); limits.setMaxEntries(200); limits.setMaxExpandedBytes(50*1024*1024); limits.setMaxCompressionRatio(100); limits.setMaxFileBytes(15*1024*1024); limits.setMaxAttempts(3);
    }
    Tenant tenant(String name, String tax) { Tenant t=new Tenant(); t.setName(name); t.setTaxCode(tax); return tenants.save(t); }
    Membership member(Tenant t, boolean create) { Membership m=new Membership(); m.setUserId(user.getId()); m.setTenantId(t.getId()); m.setInvoiceCreate(create); m.setInvoiceReview(create); return memberships.save(m); }
    Login login() throws Exception {
        MvcResult result=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsBytes(java.util.Map.of("username", "accountant", "password", PASSWORD))))
            .andExpect(status().isOk()).andReturn();
        return new Login((MockHttpSession) result.getRequest().getSession(), json.readTree(result.getResponse().getContentAsString()).get("csrfToken").asText());
    }
    JsonNode upload(Login login, String name, byte[] bytes) throws Exception {
        var result=mvc.perform(multipart("/api/intake/sessions").file(new MockMultipartFile("files", name, "application/octet-stream", bytes)).session(login.session()).header("X-CSRF-Token", login.csrf()))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    static byte[] xml(String number, String seller, String buyer) {
        return ("<HDon><DLHDon><TTChung><KHMSHDon>1</KHMSHDon><KHHDon>C26TAA</KHHDon><SHDon>"+number+"</SHDon><NLap>2026-11-03</NLap></TTChung><NDHDon><NBan><Ten>Nhà cung cấp</Ten><MST>"+seller+"</MST></NBan><NMua><MST>"+buyer+"</MST></NMua><TToan><TgTCThue>100000</TgTCThue><TgTThue>10000</TgTThue><TgTTTBSo>110000</TgTTTBSo></TToan></NDHDon></DLHDon></HDon>").getBytes(StandardCharsets.UTF_8);
    }
    static byte[] zip(String[] names, byte[][] contents) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)) { for(int i=0;i<names.length;i++) { zip.putNextEntry(new ZipEntry(names[i])); zip.write(contents[i]); zip.closeEntry(); } }
        return bytes.toByteArray();
    }
    @Test void emailRetainsOriginalAndDeduplicatesMessage() throws Exception {
        var request=new MockHttpServletRequest(); request.setAttribute("tenantId",a.getId());request.setAttribute("username","email-test");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            var payload=new com.fsa.expense.email.EmailPayload("<message-1>","vendor@example.com","Invoice",null,
                java.util.List.of(new com.fsa.expense.email.EmailPayload.Attachment("invoice.xml",xml("email-1","0300000001",a.getTaxCode()))));
            var first=emailIntake.receive(payload);
            assertThat(first.getState()).isEqualTo("SUCCESS");
            assertThat(emailIntake.receive(payload).getId()).isEqualTo(first.getId());
            assertThat(emailMessages.count()).isEqualTo(1);
            assertThat(invoices.count()).isEqualTo(1);
        } finally {org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
    @Test void emailStorageFailureDoesNotCommitClaim() {
        var request=new MockHttpServletRequest(); request.setAttribute("tenantId",a.getId());request.setAttribute("username","email-test");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        org.mockito.Mockito.doThrow(new IllegalStateException("disk unavailable")).when(storage).storeBytes(org.mockito.ArgumentMatchers.any());
        try {
            var payload=new com.fsa.expense.email.EmailPayload("<message-2>","vendor@example.com","Invoice",null,java.util.List.of());
            assertThatThrownBy(() -> emailIntake.receive(payload)).isInstanceOf(IllegalStateException.class);
            assertThat(emailMessages.count()).isZero();
        } finally {org.mockito.Mockito.reset(storage);org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
    @Test void webhookIsDisabledWithoutSecret() throws Exception {
        mvc.perform(post("/emails/webhook").contentType("application/json").content("{\"sender\":\"vendor@example.com\",\"subject\":\"invoice\",\"attachments\":[]}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void emailReprocessReadsRetainedBytesAndKeepsPartialInvoicesExactlyOnce() throws Exception {
        var request=new MockHttpServletRequest(); request.setAttribute("tenantId",a.getId());request.setAttribute("username","email-test");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            var payload=new com.fsa.expense.email.EmailPayload("<partial>","vendor@example.com","Batch",null,java.util.List.of(
                new com.fsa.expense.email.EmailPayload.Attachment("good.xml",xml("email-good","0300000001",a.getTaxCode())),
                new com.fsa.expense.email.EmailPayload.Attachment("bad.xml","<broken>".getBytes())));
            var first=emailIntake.receive(payload);
            assertThat(first.getState()).isEqualTo("PARTIAL");
            var retried=emailIntake.retry(first.getId());
            assertThat(retried.getState()).isEqualTo("PARTIAL");
            assertThat(retried.getAttempts()).isEqualTo(2);
            assertThat(invoices.count()).isEqualTo(1);
        } finally {org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
    @Test void emailRejectsBuyerMismatch() {
        var request=new MockHttpServletRequest(); request.setAttribute("tenantId",a.getId());request.setAttribute("username","email-test");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            var payload=new com.fsa.expense.email.EmailPayload("<wrong-buyer>","vendor@example.com","Invoice",null,
                java.util.List.of(new com.fsa.expense.email.EmailPayload.Attachment("invoice.xml",xml("wrong-buyer","0300000001",b.getTaxCode()))));
            assertThat(emailIntake.receive(payload).getState()).isEqualTo("FAILED");
            assertThat(invoices.count()).isZero();
            assertThat(items.findAll()).extracting(IntakeItem::getState).containsExactly("REJECTED_ROUTING");
        } finally {org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
    @Test void xmlMagicBytesAndPdfPairAvoidOcr() throws Exception {
        Login login=login();
        var response=mvc.perform(multipart("/api/intake/sessions")
            .file(new MockMultipartFile("files","invoice.pdf","application/pdf","%PDF-fake".getBytes()))
            .file(new MockMultipartFile("files","invoice.dat","application/octet-stream",xml("magic","0300000001",a.getTaxCode())))
            .session(login.session()).header("X-CSRF-Token",login.csrf())).andExpect(status().isOk()).andReturn();
        var result=json.readTree(response.getResponse().getContentAsString());
        assertThat(result.at("/items/0/state").asText()).isEqualTo("PDF_ATTACHED");
        assertThat(result.at("/items/1/state").asText()).isEqualTo("INGESTED");
        org.mockito.Mockito.verify(extraction,org.mockito.Mockito.never()).extractInvoice(org.mockito.ArgumentMatchers.any());
    }
    @Test void malformedXmlKeepsSourceWithoutCreatingInvoice() throws Exception {
        Login login=login(); JsonNode result=upload(login,"broken.xml", "<HDon>".getBytes());
        assertThat(result.at("/items/0/state").asText()).isEqualTo("PARSE_FAILED");
        assertThat(jdbc.queryForObject("select count(*) from invoices", Long.class)).isZero();
        assertThat(items.findAll()).hasSize(1);
        mvc.perform(get("/api/intake/items/"+result.at("/items/0/id").asLong()+"/file").session(login.session())).andExpect(status().isOk()).andExpect(content().bytes("<HDon>".getBytes()));
    }
    @Test void zipPartialSuccessDedupAndHistorySurviveNewSession() throws Exception {
        Login login=login(); byte[] first=xml("1","0300000001",a.getTaxCode());
        byte[] bytes=zip(new String[]{"one.xml","nested/two.xml","bad.xml"}, new byte[][]{first,xml("2","0300000001",a.getTaxCode()),"broken".getBytes()});
        JsonNode result=upload(login,"batch.zip",bytes);
        assertThat(result.at("/counts/xmlDocuments").asInt()).isEqualTo(3);
        assertThat(result.at("/counts/INGESTED").asInt()).isEqualTo(2);
        assertThat(result.at("/counts/PARSE_FAILED").asInt()).isEqualTo(1);
        JsonNode repeat=upload(login,"batch.zip",bytes);
        assertThat(repeat.at("/counts/ALREADY_RECEIVED").asInt()).isEqualTo(2);
        JsonNode contentChanged=upload(login,"changed.xml",new String(first,StandardCharsets.UTF_8).replace("<Ten>","<Ten> ").getBytes(StandardCharsets.UTF_8));
        assertThat(contentChanged.at("/items/0/state").asText()).isEqualTo("DUPLICATE_IDENTITY");
        Login fresh=login();
        mvc.perform(get("/api/intake/sessions/"+result.at("/session/id").asLong()).session(fresh.session())).andExpect(status().isOk()).andExpect(jsonPath("$.counts.INGESTED").value(2));
    }
    @Test void buyerMismatchStillCreatesPendingAndOutboundIsRejected() throws Exception {
        Login login=login();
        JsonNode mismatch=upload(login,"buyer.xml",xml("3","0300000001", ""));
        assertThat(mismatch.at("/items/0/state").asText()).isEqualTo("INGESTED");
        mvc.perform(get("/api/invoices/"+mismatch.at("/items/0/invoiceId").asLong()).session(login.session())).andExpect(status().isOk())
            .andExpect(jsonPath("$.validationState").value("BUYER_MISMATCH")).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        assertThat(upload(login,"outbound.xml",xml("4",a.getTaxCode(),"0300000001")).at("/items/0/state").asText()).isEqualTo("REJECTED_OUTBOUND");
    }
    @Test void tempSessionAndUnauthorizedTenantCannotAccessInvoices() throws Exception {
        member(b,true); Login login=login();
        mvc.perform(get("/api/invoices").session(login.session())).andExpect(status().isForbidden());
        Tenant other=tenant("Other", "0100000003");
        mvc.perform(post("/api/auth/tenant").session(login.session()).header("X-CSRF-Token",login.csrf()).contentType("application/json").content("{\"tenantId\":"+other.getId()+"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/tenant").session(login.session()).header("X-CSRF-Token",login.csrf()).contentType("application/json").content("{\"tenantId\":"+a.getId()+"}")).andExpect(status().isOk());
        JsonNode result=upload(login,"one.xml",xml("1","0300000001",a.getTaxCode()));
        long invoiceId=result.at("/items/0/invoiceId").asLong();
        mvc.perform(post("/api/auth/tenant").session(login.session()).header("X-CSRF-Token",login.csrf()).contentType("application/json").content("{\"tenantId\":"+b.getId()+"}")).andExpect(status().isOk());
        mvc.perform(get("/api/invoices/"+invoiceId).session(login.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/invoices").session(login.session())).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/intake/sessions/"+result.at("/session/id").asLong()).session(login.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/intake/items/"+result.at("/items/0/id").asLong()+"/file").session(login.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/analytics/summary").session(login.session())).andExpect(status().isOk()).andExpect(jsonPath("$.totalInvoicesCount").value(0));
    }
    @Test void createPermissionCsrfAndLegacyRoutesEnforced() throws Exception {
        Membership m=memberships.findByUserIdAndTenantId(user.getId(),a.getId()).orElseThrow(); m.setInvoiceCreate(false); memberships.save(m);
        Login login=login();
        mvc.perform(multipart("/api/intake/sessions").file(new MockMultipartFile("files","one.xml","text/xml",xml("1","0300000001",a.getTaxCode()))).session(login.session()).header("X-CSRF-Token",login.csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(login.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/invoices/files/anything.xml").session(login.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());
    }
    @Test void retryOnlyErrorsAndLimitAttempts() throws Exception {
        Login login=login(); JsonNode bad=upload(login,"bad.xml","bad".getBytes());
        String retry="/api/intake/sessions/"+bad.at("/session/id").asLong()+"/items/"+bad.at("/items/0/id").asLong()+"/retry";
        for(int n=0;n<2;n++) mvc.perform(post(retry).session(login.session()).header("X-CSRF-Token",login.csrf())).andExpect(status().isOk());
        mvc.perform(post(retry).session(login.session()).header("X-CSRF-Token",login.csrf())).andExpect(status().isConflict());
        assertThat(attempts.findAll()).hasSize(3);
        JsonNode good=upload(login,"good.xml",xml("1","0300000001",a.getTaxCode()));
        mvc.perform(post("/api/intake/sessions/"+good.at("/session/id").asLong()+"/items/"+good.at("/items/0/id").asLong()+"/retry").session(login.session()).header("X-CSRF-Token",login.csrf())).andExpect(status().isConflict());
    }
    @Test void zipEmptyTraversalNestedAndBombBudgets() throws Exception {
        Login login=login();
        assertThat(upload(login,"empty.zip",zip(new String[]{},new byte[][]{})).at("/items/0/state").asText()).isEqualTo("ARCHIVE_EMPTY");
        assertThat(upload(login,"unsafe.zip",zip(new String[]{"../escape.xml"},new byte[][]{xml("1","0300000001",a.getTaxCode())})).at("/items/0/state").asText()).isEqualTo("ARCHIVE_FAILED");
        assertThat(upload(login,"nested.zip",zip(new String[]{"inner.zip"},new byte[][]{zip(new String[]{},new byte[][]{})})).at("/items/1/state").asText()).isEqualTo("UNSUPPORTED");
        limits.setMaxExpandedBytes(10);
        assertThat(upload(login,"limit.zip",zip(new String[]{"one.xml"},new byte[][]{xml("1","0300000001",a.getTaxCode())})).at("/items/0/state").asText()).isEqualTo("ARCHIVE_FAILED");
        limits.setMaxExpandedBytes(50*1024*1024); limits.setMaxEntries(1);
        JsonNode partial=upload(login,"count.zip",zip(new String[]{"one.xml","two.xml"},new byte[][]{xml("1","0300000001",a.getTaxCode()),xml("2","0300000001",a.getTaxCode())}));
        assertThat(partial.at("/counts/INGESTED").asInt()).isEqualTo(1);
        assertThat(partial.at("/items/0/state").asText()).isEqualTo("ARCHIVE_FAILED");
    }
    @Test void contentSniffingAndXxeCannotCreateInvoices() throws Exception {
        Login login=login();
        assertThat(upload(login,"renamed.zip","not a zip".getBytes()).at("/items/0/state").asText()).isEqualTo("INVALID_ARCHIVE");
        assertThat(upload(login,"renamed.xml","%PDF-1.7".getBytes()).at("/items/0/state").asText()).isEqualTo("PARSE_FAILED");
        org.mockito.Mockito.doThrow(new IllegalStateException("PDF hỏng")).when(extraction).extractInvoice(org.mockito.ArgumentMatchers.any());
        assertThat(upload(login,"photo.pdf","%PDF-1.7".getBytes()).at("/items/0/state").asText()).isEqualTo("OCR_FAILED");
        assertThatThrownBy(() -> parser.parse("<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><HDon>&x;</HDon>".getBytes())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void tenFailuresBlockUntilWindowExpiresAndLockedAccountsNeverGetSession() throws Exception {
        for(int n=0;n<10;n++) mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"accountant\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsBytes(java.util.Map.of("username","accountant","password",PASSWORD)))).andExpect(status().isUnauthorized());
        AppUser stored=users.findById(user.getId()).orElseThrow(); stored.setFailureWindow(Instant.now().minusSeconds(901)); users.save(stored);
        login(); stored=users.findById(user.getId()).orElseThrow(); assertThat(stored.getFailedAttempts()).isZero(); stored.setActive(false); users.save(stored);
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsBytes(java.util.Map.of("username","accountant","password",PASSWORD)))).andExpect(status().isUnauthorized());
    }
    @Test void tenantLifecycleAndLogoutAreEnforced() throws Exception {
        Login login=login(); a.setSubscriptionStatus("GRACE"); tenants.save(a);
        mvc.perform(get("/api/invoices").session(login.session())).andExpect(status().isOk());
        mvc.perform(multipart("/api/intake/sessions").file(new MockMultipartFile("files","one.xml","text/xml",xml("1","0300000001",a.getTaxCode()))).session(login.session()).header("X-CSRF-Token",login.csrf())).andExpect(status().isForbidden());
        a.setSubscriptionStatus("EXPIRED"); tenants.save(a);
        mvc.perform(get("/api/invoices").session(login.session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(login.session()).header("X-CSRF-Token",login.csrf())).andExpect(status().isOk());
        assertThat(login.session().isInvalid()).isTrue();
    }

    @Test void storageFailureIsNotDurableAcceptanceAndCanBeUploadedAgain() throws Exception {
        Login login=login();
        org.mockito.Mockito.doThrow(new IllegalStateException("storage unavailable")).when(storage).storeBytes(org.mockito.ArgumentMatchers.any());
        JsonNode failed=upload(login,"one.xml",xml("1","0300000001",a.getTaxCode()));
        assertThat(failed.at("/items/0/state").asText()).isEqualTo("STORAGE_FAILED");
        assertThat(jdbc.queryForObject("select count(*) from invoices", Long.class)).isZero();
        org.mockito.Mockito.doCallRealMethod().when(storage).storeBytes(org.mockito.ArgumentMatchers.any());
        assertThat(upload(login,"one.xml",xml("1","0300000001",a.getTaxCode())).at("/items/0/state").asText()).isEqualTo("INGESTED");
    }
    @Test void postProcessingFailureIsVisibleAndRecheckDoesNotApproveInvoice() throws Exception {
        Login login=login();
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable")).when(audit).auditInvoice(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        JsonNode result=upload(login,"one.xml",xml("1","0300000001",a.getTaxCode()));
        long id=result.at("/items/0/invoiceId").asLong();
        mvc.perform(get("/api/invoices/"+id).session(login.session())).andExpect(status().isOk()).andExpect(jsonPath("$.validationState").value("NOT_CHECKED"));
        org.mockito.Mockito.doCallRealMethod().when(audit).auditInvoice(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/invoices/"+id+"/recheck").session(login.session()).header("X-CSRF-Token",login.csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("$.validationState").value("CHECKED")).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }
    @Test void concurrentUploadsCreateOnlyOneCanonicalInvoice() throws Exception {
        Login login=login(); byte[] bytes=xml("1","0300000001",a.getTaxCode());
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=executor.submit(() -> { start.await(); return upload(login,"one.xml",bytes); });
            var second=executor.submit(() -> { start.await(); return upload(login,"one.xml",bytes); });
            start.countDown();
            JsonNode one=first.get(15,java.util.concurrent.TimeUnit.SECONDS), two=second.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(java.util.List.of(one.at("/items/0/state").asText(),two.at("/items/0/state").asText())).containsExactlyInAnyOrder("INGESTED","ALREADY_RECEIVED");
            assertThat(one.at("/items/0/invoiceId").asLong()).isEqualTo(two.at("/items/0/invoiceId").asLong());
            assertThat(jdbc.queryForObject("select count(*) from invoices",Long.class)).isEqualTo(1);
        }
    }
    @Test void configuredFileAndBatchSizeBoundariesAreInclusive() throws Exception {
        Login login=login(); byte[] bytes=xml("1","0300000001",a.getTaxCode());
        limits.setMaxFileBytes(bytes.length);
        assertThat(upload(login,"boundary.xml",bytes).at("/items/0/state").asText()).isEqualTo("INGESTED");
        limits.setMaxFileBytes(bytes.length-1);
        mvc.perform(multipart("/api/intake/sessions").file(new MockMultipartFile("files","large.xml","text/xml",bytes)).session(login.session()).header("X-CSRF-Token",login.csrf())).andExpect(status().isBadRequest());
        limits.setMaxFileBytes(bytes.length); long previous=limits.getMaxBatchBytes();
        try {
            limits.setMaxBatchBytes(bytes.length*2L);
            mvc.perform(multipart("/api/intake/sessions").file(new MockMultipartFile("files","a.xml","text/xml",bytes)).file(new MockMultipartFile("files","b.xml","text/xml",bytes)).session(login.session()).header("X-CSRF-Token",login.csrf())).andExpect(status().isOk());
            limits.setMaxBatchBytes(bytes.length*2L-1);
            mvc.perform(multipart("/api/intake/sessions").file(new MockMultipartFile("files","a.xml","text/xml",bytes)).file(new MockMultipartFile("files","b.xml","text/xml",bytes)).session(login.session()).header("X-CSRF-Token",login.csrf())).andExpect(status().isBadRequest());
        } finally { limits.setMaxBatchBytes(previous); }
    }
    @Test void directoryEntriesAlsoConsumeExpansionBudget() throws Exception {
        Login login=login(); limits.setMaxExpandedBytes(100);
        byte[] payload=new byte[1000];
        JsonNode result=upload(login,"directory.zip",zip(new String[]{"directory/","one.xml"},new byte[][]{payload,xml("1","0300000001",a.getTaxCode())}));
        assertThat(result.at("/items/0/state").asText()).isEqualTo("ARCHIVE_FAILED");
        assertThat(jdbc.queryForObject("select count(*) from invoices",Long.class)).isZero();
    }
    com.fsa.expense.dto.AiExtractionDto pdfData() {
        return com.fsa.expense.dto.AiExtractionDto.builder().invoiceNumber("00099").invoiceDate("2026-11-04")
            .invoiceTemplate("1").invoiceSeries("C26TAA").vendorName("PDF Supplier").taxCode("0300000001")
            .buyerName("Buyer A").buyerTaxCode(a.getTaxCode()).sellerAddress("Seller street").buyerAddress("Buyer street").currency("VND")
            .subtotalAmount(new java.math.BigDecimal("100000")).vatAmount(new java.math.BigDecimal("10000")).totalAmount(new java.math.BigDecimal("110000"))
            .extractionMethod("PYMUPDF_VECTOR_TEXT").build();
    }
    JsonNode draft(Login login,long id) throws Exception {
        return json.readTree(mvc.perform(get("/api/intake/items/"+id+"/draft").session(login.session())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void pdfPersistsDraftThenConfirmsOnceWithSameFieldsAsXml() throws Exception {
        Login login=login(); org.mockito.Mockito.doReturn(pdfData()).when(extraction).extractInvoice(org.mockito.ArgumentMatchers.any());
        JsonNode result=upload(login,"invoice.pdf","%PDF-1.7 test fixture".getBytes());
        long id=result.at("/items/0/id").asLong();
        assertThat(result.at("/items/0/state").asText()).isEqualTo("OCR_REVIEW");
        assertThat(result.at("/counts/pdfDocuments").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from invoices",Long.class)).isZero();
        JsonNode repeat=upload(login,"invoice.pdf","%PDF-1.7 test fixture".getBytes());
        assertThat(repeat.at("/items/0/originalItemId").asLong()).isEqualTo(id);
        JsonNode data=draft(login,id);
        assertThat(data.at("/data/buyer_name").asText()).isEqualTo("Buyer A");
        long invoiceId=0;
        for(int n=0;n<2;n++) {
            var response=mvc.perform(post("/api/intake/items/"+id+"/confirm").session(login.session()).header("X-CSRF-Token",login.csrf()).contentType("application/json").content(data.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("INGESTED")).andReturn();
            invoiceId=json.readTree(response.getResponse().getContentAsString()).path("invoiceId").asLong();
        }
        assertThat(jdbc.queryForObject("select count(*) from invoices",Long.class)).isEqualTo(1);
        mvc.perform(get("/api/invoices/"+invoiceId).session(login.session())).andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("OCR_CONFIRMED")).andExpect(jsonPath("$.buyerName").value("Buyer A"))
            .andExpect(jsonPath("$.invoiceTemplate").value("1")).andExpect(jsonPath("$.fileType").value("PDF"))
            .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        mvc.perform(get("/api/intake/items/"+id+"/file").session(login.session())).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
    }
    @Test void pdfDraftVersionConflictAndRequiredFieldsAreChecked() throws Exception {
        Login login=login(); var extracted=pdfData(); extracted.setInvoiceNumber(null);
        org.mockito.Mockito.doReturn(extracted).when(extraction).extractInvoice(org.mockito.ArgumentMatchers.any());
        long id=upload(login,"incomplete.pdf","%PDF-1.7 incomplete".getBytes()).at("/items/0/id").asLong();
        JsonNode data=draft(login,id);
        mvc.perform(post("/api/intake/items/"+id+"/confirm").session(login.session()).header("X-CSRF-Token",login.csrf()).contentType("application/json").content(data.toString())).andExpect(status().isBadRequest());
        ((com.fasterxml.jackson.databind.node.ObjectNode)data.path("data")).put("invoice_number","00100");
        mvc.perform(put("/api/intake/items/"+id+"/draft").session(login.session()).header("X-CSRF-Token",login.csrf()).contentType("application/json").content(data.toString())).andExpect(status().isOk());
        mvc.perform(put("/api/intake/items/"+id+"/draft").session(login.session()).header("X-CSRF-Token",login.csrf()).contentType("application/json").content(data.toString())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from invoices",Long.class)).isZero();
    }
    @Test void pdfAndXmlIdentityDedupAndCrossTenantProtection() throws Exception {
        Login login=login(); org.mockito.Mockito.doReturn(pdfData()).when(extraction).extractInvoice(org.mockito.ArgumentMatchers.any());
        JsonNode imported=upload(login,"original.xml",xml("00099","0300000001",a.getTaxCode()));
        long id=upload(login,"representation.pdf","%PDF-1.7 representation".getBytes()).at("/items/0/id").asLong();
        JsonNode data=draft(login,id);
        mvc.perform(post("/api/intake/items/"+id+"/confirm").session(login.session()).header("X-CSRF-Token",login.csrf()).contentType("application/json").content(data.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("DUPLICATE_IDENTITY")).andExpect(jsonPath("$.invoiceId").value(imported.at("/items/0/invoiceId").asInt()));
        member(b,true); login.session().setAttribute("tenantId",b.getId());
        mvc.perform(get("/api/intake/items/"+id+"/draft").session(login.session())).andExpect(status().isNotFound());
        mvc.perform(post("/api/intake/items/"+id+"/confirm").session(login.session()).header("X-CSRF-Token",login.csrf()).contentType("application/json").content(data.toString())).andExpect(status().isNotFound());
    }
    @Test void pdfAndXmlDedupNormalizesLeadingZeroesAndTemplatePrefixInSeries() throws Exception {
        Login login=login();
        var extracted=pdfData();
        extracted.setInvoiceNumber("00091942");
        extracted.setInvoiceSeries("1C26TAA");
        org.mockito.Mockito.doReturn(extracted).when(extraction).extractInvoice(org.mockito.ArgumentMatchers.any());

        JsonNode imported=upload(login,"original.xml",xml("91942","0300000001",a.getTaxCode()));
        JsonNode pdf=upload(login,"representation.pdf","%PDF-1.7 representation-normalized".getBytes());

        assertThat(pdf.at("/items/0/state").asText()).isEqualTo("DUPLICATE_IDENTITY");
        assertThat(pdf.at("/items/0/invoiceId").asLong()).isEqualTo(imported.at("/items/0/invoiceId").asLong());
        assertThat(jdbc.queryForObject("select count(*) from invoices",Long.class)).isEqualTo(1);
    }
}
