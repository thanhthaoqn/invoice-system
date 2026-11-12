package com.fsa.expense.email;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;

@Service
public class EmailRedisSupport {
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final ObjectMapper json;
    public EmailRedisSupport(StringRedisTemplate redis,ObjectMapper json,@Value("${app.email.redis-enabled:false}") boolean enabled){this.redis=redis;this.json=json;this.enabled=enabled;}
    public String acquire(String key,Duration ttl){
        if(!enabled)return "database-lease";
        String token=UUID.randomUUID().toString();
        if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key,token,ttl)))throw com.fsa.expense.auth.AuthService.error(409,"Email đang được xử lý");
        return token;
    }
    public void release(String key,String token){
        if(!enabled)return;
        try {redis.execute(new DefaultRedisScript<Long>("if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",Long.class),List.of(key),token);}
        catch(RuntimeException ignored){/* Lease expires; durable DB claim remains authoritative. */}
    }
    public void deadLetter(EmailMessage message){
        if(!enabled)return;
        try {redis.opsForValue().set("email:dlq:"+message.getTenantId()+":"+message.getId(),json.writeValueAsString(message),Duration.ofDays(90));}
        catch(Exception ignored){/* DB deadLetter flag is retained for a subsequent delivery attempt. */}
    }
}
