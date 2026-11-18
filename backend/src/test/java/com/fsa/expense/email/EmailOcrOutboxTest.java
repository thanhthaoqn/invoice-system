package com.fsa.expense.email;

import com.fsa.expense.auth.*;
import com.fsa.expense.intake.*;
import com.fsa.expense.service.AiExtractionClient;
import com.fsa.expense.dto.AiExtractionDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.test.context.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:emailocr;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","app.bootstrap.username=","app.email.ocr-async-enabled=true","app.email.ocr-dispatch-initial-delay-ms=3600000","spring.rabbitmq.dynamic=false","spring.rabbitmq.listener.simple.auto-startup=false"})
class EmailOcrOutboxTest {
    @Autowired TenantRepository tenants;
    @Autowired EmailIntakeService intake;
    @Autowired OcrJobRepository jobs;
    @Autowired EmailOcrWorker worker;
    @Autowired IntakeItemRepository items;
    @MockBean RabbitTemplate rabbit;
    @MockBean AiExtractionClient extraction;
    static final Path STORAGE;
    static{try{STORAGE=Files.createTempDirectory("email-ocr-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry registry){registry.add("app.storage.upload-dir",() -> STORAGE.toString());}
    private EmailMessage enqueue(){
        var tenant=new Tenant();tenant.setName("OCR Tenant");tenant.setTaxCode(UUID.randomUUID().toString());tenants.save(tenant);
        AtomicReference<EmailMessage> result=new AtomicReference<>();
        TenantContext.runAs(tenant.getId(),() -> result.set(intake.receive(new EmailPayload(UUID.randomUUID().toString(),"vendor@example.com","PDF",null,List.of(new EmailPayload.Attachment("invoice.pdf","%PDF-test-fixture".getBytes()))))));
        return result.get();
    }
    @Test void pdfAndOutboxAreDurableBeforeAnyNetworkOcr(){
        var email=enqueue();
        assertThat(email.getState()).isEqualTo("NEEDS_REVIEW");
        assertThat(jobs.findTop25ByStateOrderById("PENDING")).anySatisfy(job -> {
            assertThat(job.getTenantId()).isEqualTo(email.getTenantId());
            assertThat(items.findByIdAndTenantId(job.getItemId(),email.getTenantId()).orElseThrow().getState()).isEqualTo("OCR_PENDING");
        });
        verifyNoInteractions(extraction);verifyNoInteractions(rabbit);
    }
    @Test void brokerOutageKeepsOutboxPending(){
        var email=enqueue();
        doThrow(new IllegalStateException("broker offline")).when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(CorrelationData.class));
        worker.publish();
        assertThat(jobs.findTop25ByStateOrderById("PENDING")).anyMatch(job -> job.getTenantId().equals(email.getTenantId()));
    }
    @Test void confirmedPublishAndDuplicateConsumptionDoNotDuplicateOcr(){
        var email=enqueue();
        var job=jobs.findTop25ByStateOrderById("PENDING").stream().filter(j -> j.getTenantId().equals(email.getTenantId())).findFirst().orElseThrow();
        doAnswer(invocation -> {CorrelationData correlation=invocation.getArgument(3);correlation.getFuture().complete(new CorrelationData.Confirm(true,null));return null;})
            .when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(CorrelationData.class));
        worker.publish();assertThat(jobs.findById(job.getId()).orElseThrow().getState()).isEqualTo("PUBLISHED");
        when(extraction.extractInvoice(any())).thenReturn(new AiExtractionDto());
        worker.consume(job.getId().toString());worker.consume(job.getId().toString());
        assertThat(jobs.findById(job.getId()).orElseThrow().getState()).isEqualTo("DONE");
        assertThat(items.findByIdAndTenantId(job.getItemId(),email.getTenantId()).orElseThrow().getState()).isEqualTo("OCR_REVIEW");
        verify(extraction,times(1)).extractInvoice(any());
    }
}
