package com.fsa.expense.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.util.Timeout;
import java.net.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class InvoiceLinkDownloader {
    private final Set<String> domains;
    private final long maxBytes;
    public InvoiceLinkDownloader(@Value("${app.email.download-domains:meinvoice.vn,misainvoice.vn,vnpt-invoice.com.vn,sinvoice.viettel.vn,van.ehoadon.vn}") String domains,
            @Value("${app.intake.max-file-bytes:15728640}") long maxBytes){
        this.domains=new HashSet<>();for(String domain:domains.split(","))if(!domain.isBlank())this.domains.add(domain.trim().toLowerCase(Locale.ROOT));this.maxBytes=maxBytes;
    }
    public List<URI> extract(String html){
        if(html==null)return List.of();
        var matches=Pattern.compile("https://[^\\s<>\"']+",Pattern.CASE_INSENSITIVE).matcher(html);
        Set<URI> result=new LinkedHashSet<>();
        while(matches.find() && result.size()<10){
            try {URI uri=URI.create(matches.group().replace("&amp;","&"));if(allowed(uri))result.add(uri);}
            catch(IllegalArgumentException ignored){}
        }
        return result.stream().filter(uri -> uri.getRawQuery()!=null || (uri.getPath()!=null && !uri.getPath().isBlank() && !uri.getPath().equals("/")))
            .sorted(Comparator.comparingInt(InvoiceLinkDownloader::priority)).toList();
    }
    private static int priority(URI uri){
        String path=Objects.toString(uri.getPath(),"").toLowerCase(Locale.ROOT);
        if(path.contains("getinvoice") || path.contains("xml") || path.endsWith(".zip"))return 0;
        if(path.contains("pdfdownload") || path.endsWith(".pdf"))return 1;
        return 2;
    }
    boolean allowed(URI uri){
        String host=uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo()==null && (uri.getPort()==-1 || uri.getPort()==443) && host!=null
            && domains.stream().anyMatch(d -> host.equalsIgnoreCase(d) || host.toLowerCase(Locale.ROOT).endsWith("."+d));
    }
    public EmailPayload.Attachment download(URI start) throws java.io.IOException {
        URI uri=start;
        for(int redirect=0;redirect<=5;redirect++){
            if(!allowed(uri))throw new IllegalArgumentException("Tên miền tải hóa đơn chưa được cho phép");
            String host=uri.getHost();InetAddress[] addresses=PublicNetwork.resolve(host);
            DnsResolver pinned=new DnsResolver(){
                public InetAddress[] resolve(String queried)throws UnknownHostException{
                    if(!queried.equalsIgnoreCase(host))throw new UnknownHostException("Unexpected destination");return addresses.clone();
                }
                public String resolveCanonicalHostname(String queried)throws UnknownHostException{return queried;}
            };
            var manager=PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(pinned).build();
            var config=RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(10)).setConnectionRequestTimeout(Timeout.ofSeconds(10)).setResponseTimeout(Timeout.ofSeconds(20)).build();
            URI current=uri;
            try(var client=HttpClients.custom().setConnectionManager(manager).setDefaultRequestConfig(config).disableRedirectHandling().disableAutomaticRetries().disableContentCompression().build()){
                record Result(String redirect,byte[] bytes){}
                Result result=client.execute(new HttpGet(current),response -> {
                    int status=response.getCode();
                    if(Set.of(301,302,303,307,308).contains(status)){
                        var location=response.getFirstHeader("Location");if(location==null)throw new java.io.IOException("Chuyển hướng thiếu URL");return new Result(location.getValue(),null);
                    }
                    if(status==429 || status>=500)throw new TransientDownloadException("Cổng hóa đơn tạm thời không sẵn sàng");
                    if(status!=200 || response.getEntity()==null)throw new java.io.IOException("Tải hóa đơn thất bại HTTP "+status);
                    if(response.getEntity().getContentLength()>maxBytes)throw new IllegalArgumentException("File tải về vượt giới hạn");
                    try(var input=response.getEntity().getContent()){
                        byte[] bytes=input.readNBytes((int)Math.min(maxBytes,Integer.MAX_VALUE-1)+1);
                        if(bytes.length>maxBytes)throw new IllegalArgumentException("File tải về vượt giới hạn");
                        return new Result(null,bytes);
                    }
                });
                if(result.redirect()!=null){uri=current.resolve(result.redirect());continue;}
                byte[] bytes=result.bytes();
                String prefix=new String(bytes,0,Math.min(bytes.length,256),java.nio.charset.StandardCharsets.UTF_8).stripLeading();
                String name=bytes.length>3 && bytes[0]=='P' && bytes[1]=='K'?"download.zip":prefix.startsWith("%PDF-")?"download.pdf":
                    (prefix.startsWith("<?xml") || prefix.startsWith("<HDon") || prefix.startsWith("<DLHDon"))?"download.xml":null;
                if(name==null)throw new IllegalArgumentException("Link trả về trang tra cứu HTML; cần link tải XML/ZIP/PDF trực tiếp");
                return new EmailPayload.Attachment(name,bytes);
            }
        }
        throw new IllegalArgumentException("Link vượt quá 5 lần chuyển hướng");
    }
    public static class TransientDownloadException extends java.io.IOException {
        public TransientDownloadException(String message){super(message);}
    }
}
