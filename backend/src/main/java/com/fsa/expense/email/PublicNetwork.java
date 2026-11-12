package com.fsa.expense.email;
import java.net.*;
import java.util.*;

/** Reject non-public destinations before dialing; callers must pin returned addresses. */
public final class PublicNetwork {
    private PublicNetwork(){}
    public static boolean publicAddress(InetAddress address){
        if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress())return false;
        byte[] b=address.getAddress(); int a=b[0]&255;
        if(b.length==16) {
            if((a&0xe0)!=0x20)return false;
            if(a==0x20 && (b[1]&255)==0x02)return false; // 6to4 can encode private IPv4 endpoints.
            if(a==0x20 && (b[1]&255)==0x01 && (((b[2]&255)==0x0d && (b[3]&255)==0xb8) || ((b[2]&255)==0 && (b[3]&255)==0)))return false;
            return true;
        }
        int c=b[1]&255;
        return !(a==0 || a==10 || a==127 || a>=224 || (a==100 && c>=64 && c<=127) || (a==169 && c==254)
            || (a==172 && c>=16 && c<=31) || (a==192 && (c==168 || c==0 || c==2))
            || (a==198 && (c==18 || c==19 || c==51)) || (a==203 && c==0));
    }
    public static InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses=InetAddress.getAllByName(host);
        if(addresses.length==0 || Arrays.stream(addresses).anyMatch(a -> !publicAddress(a))) throw new IllegalArgumentException("Địa chỉ mạng nội bộ hoặc không công khai bị chặn");
        return addresses;
    }
}
