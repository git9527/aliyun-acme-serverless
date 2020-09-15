package me.git9527.dns;

public interface DnsProvider {

    void addTextRecord(String host, String textValue);
}
