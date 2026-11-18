package com.fsa.expense.email;

import com.fsa.expense.auth.TenantContext;
import com.fsa.expense.intake.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component @ConditionalOnProperty(name="app.email.ocr-async-enabled",havingValue="true")
public class EmailOcrWorker {
    private final OcrJobRepository jobs;
    private final IntakeItemRepository items;
    private final IntakeProcessor processor;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;
    public EmailOcrWorker(OcrJobRepository jobs,IntakeItemRepository items,IntakeProcessor processor,RabbitTemplate rabbit,PlatformTransactionManager manager){
        this.jobs=jobs;this.items=items;this.processor=processor;this.rabbit=rabbit;tx=new TransactionTemplate(manager);
    }
    @Bean public Declarables emailOcrQueues(){
        return new Declarables(QueueBuilder.durable("email.ocr").withArgument("x-dead-letter-exchange","").withArgument("x-dead-letter-routing-key","email.ocr.dlq").build(),QueueBuilder.durable("email.ocr.dlq").build());
    }
    @Scheduled(fixedDelayString="${app.email.ocr-dispatch-interval-ms:5000}",initialDelayString="${app.email.ocr-dispatch-initial-delay-ms:30000}")
    public void publish(){
        for(var candidate:jobs.findTop25ByStateOrderById("PENDING")){
            try {tx.executeWithoutResult(status -> {
                var job=jobs.lockById(candidate.getId()).orElseThrow();if(!"PENDING".equals(job.getState()))return;
                CorrelationData correlation=new CorrelationData(job.getId().toString());
                rabbit.convertAndSend("","email.ocr",job.getId().toString(),correlation);
                try {
                    var confirm=correlation.getFuture().get(10,TimeUnit.SECONDS);
                    if(!confirm.isAck() || correlation.getReturned()!=null)throw new IllegalStateException("OCR publish not confirmed");
                }catch(Exception e){throw new IllegalStateException("OCR publish failed",e);}
                job.setState("PUBLISHED");job.setUpdatedAt(Instant.now());jobs.save(job);
            });}catch(RuntimeException ignored){break;/* Outbox remains PENDING until broker is reachable. */}
        }
    }
    @RabbitListener(queues="email.ocr")
    public void consume(String jobId){
        Long id;
        try{id=Long.valueOf(jobId);}catch(NumberFormatException e){throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Invalid OCR job");}
        var candidate=jobs.findById(id).orElse(null);if(candidate==null)return;
        TenantContext.runAs(candidate.getTenantId(),() -> {
            var item=items.findByIdAndTenantId(candidate.getItemId(),candidate.getTenantId()).orElseThrow();
            if("OCR_PENDING".equals(item.getState()))processor.process(item.getId(),true);
            tx.executeWithoutResult(status -> {
                var job=jobs.lockById(id).orElseThrow();
                var latest=items.findByIdAndTenantId(job.getItemId(),job.getTenantId()).orElseThrow();
                job.setState("OCR_REVIEW".equals(latest.getState())?"DONE":"FAILED");job.setUpdatedAt(Instant.now());jobs.save(job);
            });
        });
    }
}
