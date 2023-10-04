package me.git9527.dns;

import me.git9527.util.HostUtil;
import org.apache.commons.lang3.StringUtils;

public abstract class DnsProvider {

    protected final String RECORD_TYPE = "TXT";

    protected String baseDomain;

    protected String subDomain;

    protected String digest;

    public DnsProvider(String host, String digest) {
        this.baseDomain = HostUtil.getBaseDomain(host);
        String subDomain = HostUtil.getSubDomain(host);
        if (StringUtils.isBlank(subDomain)) {
            this.subDomain = "_acme-challenge";
        } else {
            this.subDomain = "_acme-challenge." + subDomain;
        }
        this.digest = digest;
    }
    
    protected String toBeVerified() {
        return this.subDomain + "." + baseDomain;
    }

    public abstract void addTextRecord();

    public abstract void removeValidatedRecord();
}
