package com.fsa.expense.service;

import com.fsa.expense.dto.AiExtractionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;

@Service
@Slf4j
public class AiExtractionClient {

    @Value("${app.ai-service.url:http://127.0.0.1:8000/api/extract}")
    private String aiServiceUrl;
    @Value("${app.intake.max-pdf-pages:20}") private int maxPdfPages;

    private final RestClient restClient;

    public AiExtractionClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(90000);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public AiExtractionDto extractInvoice(MultipartFile file) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "invoice.jpg";
                }
            };
            body.add("file", fileResource);
            body.add("max_pages", String.valueOf(maxPdfPages));

            log.info("Sending invoice file to AI microservice at {}", aiServiceUrl);
            AiExtractionDto response = restClient.post()
                    .uri(aiServiceUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(AiExtractionDto.class);

            if (response != null) {
                log.info("AI extraction succeeded for invoice: {}", response.getInvoiceNumber());
                return response;
            }
        } catch (org.springframework.web.client.RestClientResponseException e) {
            try {
                String message=new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getResponseBodyAsString()).path("detail").asText();
                if (!message.isBlank() && message.length()<500) throw new IllegalArgumentException(message);
            } catch (java.io.IOException ignored) { }
            throw new IllegalStateException("Dịch vụ trích xuất PDF chưa khả dụng; vui lòng thử lại", e);
        } catch (Exception e) {
            throw new IllegalStateException("Không kết nối được dịch vụ trích xuất PDF; vui lòng thử lại", e);
        }
        throw new IllegalStateException("Dịch vụ không trả dữ liệu PDF hợp lệ");
    }
}
