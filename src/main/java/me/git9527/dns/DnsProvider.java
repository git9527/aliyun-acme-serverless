package me.git9527.dns;

import me.git9527.util.HostUtil;

public abstract class DnsProvider {

    protected final String RECORD_TYPE = "TXT";

    protected String baseDomain;

    protected String subDomain;

    protected String digest;

    public DnsProvider(String host, String digest) {
        this.baseDomain = HostUtil.getBaseDomain(host);
        this.subDomain = "_acme-challenge." + HostUtil.getSubDomain(host);
        this.digest = digest;
    }

    public abstract void addTextRecord();

    public abstract void removeValidatedRecord();
}
