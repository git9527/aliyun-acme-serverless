package me.git9527.dns.pojo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoDaddyRecord {

    private String data;

    private String name;

    private int ttl;

    private String type;
}
