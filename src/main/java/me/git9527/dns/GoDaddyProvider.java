package me.git9527.dns;

import lombok.extern.slf4j.Slf4j;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import me.git9527.util.HostUtil;
import okhttp3.*;

import java.io.IOException;

@Slf4j
public class GoDaddyProvider implements DnsProvider {

    @Override
    public void addTextRecord(String host, String textValue) {
        String baseDomain = HostUtil.getBaseDomain(host);
        String subDomain = "_acme-challenge." + HostUtil.getSubDomain(host);
        String url = "https://api.godaddy.com/v1/domains/" + baseDomain + "/records/TXT/" + subDomain;

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "[{\"data\":\"" + textValue + "\",\"ttl\":600}]");
        Request request = new Request.Builder().url(url).put(requestBody).build();
        clientBuilder.authenticator((route, response) -> {
            String key = EnvUtil.getEnvValue(EnvKeys.GODADDY_KEY);
            String secret = EnvUtil.getEnvValue(EnvKeys.GODADDY_SECRET);
            String fullCredential = "sso-key " + key + ":" + secret;
            return response.request().newBuilder().header("Authorization", fullCredential).build();
        });
        final Call call = clientBuilder.build().newCall(request);
        Response response = null;
        try {
            response = call.execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        logger.info("add text value:{} to sub domain:[{}] for base domain:[{}], result:[{}]", textValue, subDomain, baseDomain, response.code());
        logger.info("you may get result in:{}", url);
    }
}
