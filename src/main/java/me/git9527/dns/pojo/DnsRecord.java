package me.git9527.dns.pojo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DnsRecord {
    
    private String id;
    
    private String name;
    
    private String content;
    
    private boolean proxied;
    
    private String type;
    
    private String comment;
    
    private int ttl;
}
