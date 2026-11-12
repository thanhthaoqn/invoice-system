package com.fsa.expense.email;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.IOException;

/** Authenticate before Spring/Jackson decodes potentially large Base64 attachments. */
@Component
public class EmailWebhookFilter extends OncePerRequestFilter {
    private final String secret;
    public EmailWebhookFilter(@Value("${app.email.webhook-secret:}") String secret){this.secret=secret;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if("POST".equals(request.getMethod()) && "/emails/webhook".equals(request.getRequestURI())){
            String supplied=request.getHeader("X-Inbot-Email-Webhook-Secret");
            if(secret.isBlank() || supplied==null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){
                response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"message\":\"Webhook secret invalid\"}");return;
            }
            if(request.getContentLengthLong()>36L*1024*1024){response.sendError(413);return;}
        }
        chain.doFilter(request,response);
    }
}
