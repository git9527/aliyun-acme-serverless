package me.git9527.dns;

import me.git9527.util.HostUtil;

public abstract class DnsProvider {

    protected String baseDomain;

    protected String subDomain;

    public DnsProvider(String host) {
        this.baseDomain = HostUtil.getBaseDomain(host);
        this.subDomain = "_acme-challenge." + HostUtil.getSubDomain(host);
    }

    abstract void addTextRecord(String digest);
}
