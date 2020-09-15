package me.git9527.util;

import org.junit.Assert;
import org.junit.Test;

public class TestHostUtil {

    @Test
    public void test_get_host() {
        String url = "https://c.a.baidu.com/test/sample";
        Assert.assertEquals("c.a.baidu.com", HostUtil.getHost(url));
        Assert.assertEquals("baidu.com", HostUtil.getBaseDomain(url));
        Assert.assertEquals("baidu.com", HostUtil.getBaseDomain("c.a.baidu.com"));
        Assert.assertEquals("c.a", HostUtil.getSubDomain(url));
        Assert.assertEquals("", HostUtil.getSubDomain("baidu.com"));
    }

}
