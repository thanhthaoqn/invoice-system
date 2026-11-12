package com.fsa.expense.email;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.*;

@Component
public class MailContentReader {
    public byte[] original(Message message)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        OutputStream bounded=new FilterOutputStream(bytes){
            private long size;
            public void write(int b)throws IOException{if(++size>36L*1024*1024)throw new IOException("Email too large");out.write(b);}
            public void write(byte[] b,int off,int len)throws IOException{if((size+=len)>36L*1024*1024)throw new IOException("Email too large");out.write(b,off,len);}
        };
        message.writeTo(bounded);return bytes.toByteArray();
    }
    public EmailPayload read(Message message)throws Exception{
        List<EmailPayload.Attachment> attachments=new ArrayList<>();StringBuilder body=new StringBuilder();int[] budget={0};
        walk(message,attachments,body,0,budget);
        String[] ids=message.getHeader("Message-ID");
        String sender=message.getFrom()!=null && message.getFrom().length>0 && message.getFrom()[0] instanceof InternetAddress address?address.getAddress():"unknown@invalid.test";
        String subject=Objects.toString(message.getSubject(),"");
        return new EmailPayload(ids!=null && ids.length>0?ids[0]:null,sender,subject.substring(0,Math.min(subject.length(),1000)),body.toString(),attachments);
    }
    private void walk(Part part,List<EmailPayload.Attachment> files,StringBuilder html,int depth,int[] budget)throws Exception{
        if(depth>16 || ++budget[0]>200)throw new IOException("MIME complexity limit exceeded");
        String name=part.getFileName();
        if(name!=null || Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())){
            if(files.size()>=20)throw new IOException("Too many attachments");
            try(var stream=part.getInputStream()){
                byte[] content=stream.readNBytes(15*1024*1024+1);if(content.length>15*1024*1024)throw new IOException("Attachment too large");
                String decoded=name==null?"attachment.bin":MimeUtility.decodeText(name);
                files.add(new EmailPayload.Attachment(decoded.substring(0,Math.min(decoded.length(),200)),content));
            }
        }else if(part.isMimeType("multipart/*")){
            Multipart parts=(Multipart)part.getContent();for(int i=0;i<parts.getCount();i++)walk(parts.getBodyPart(i),files,html,depth+1,budget);
        }else if(part.isMimeType("text/html") || part.isMimeType("text/plain")){
            String text=Objects.toString(part.getContent(),"");int remaining=200000-html.length();if(remaining>0)html.append(text,0,Math.min(text.length(),remaining));
        }
    }
}
