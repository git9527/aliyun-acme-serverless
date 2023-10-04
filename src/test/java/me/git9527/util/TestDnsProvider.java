package me.git9527.util;

import me.git9527.dns.AliyunProvider;
import me.git9527.dns.CloudFlareProvider;
import me.git9527.dns.GoDaddyProvider;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class TestDnsProvider {

    @Test
    public void testAliyun() {
        AliyunProvider provider = new AliyunProvider("testa.zhangsn.me", RandomStringUtils.randomAlphanumeric(10));
        provider.addTextRecord();
        provider.removeValidatedRecord();
    }

    @Test
    public void testGodaddy() {
        GoDaddyProvider provider = new GoDaddyProvider("a3.cteio.com", "d9JaTKbQC40SuX9Do5FnuMmSiaA9Tjjsdae1Gg4jy2Q");
        provider.addTextRecord();
        provider.removeValidatedRecord();
    }
    
    @Test
    public void testCloudFlare() {
        CloudFlareProvider provider = new CloudFlareProvider("something2.zhangsn.me", "bbbbbbb");
        provider.addTextRecord();
        provider.removeValidatedRecord();
    }
}
