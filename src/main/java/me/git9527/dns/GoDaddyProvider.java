package me.git9527.dns;

import lombok.extern.slf4j.Slf4j;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import me.git9527.util.HostUtil;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

@Slf4j
public class GoDaddyProvider implements DnsProvider {

    @Override
    public void addTextRecord(String host, String textValue) {
        String baseDomain = HostUtil.getBaseDomain(host);
        String subDomain = "_acme-challenge." + HostUtil.getSubDomain(host);
        String url = "https://api.godaddy.com/v1/domains/" + baseDomain + "/records/TXT/" + subDomain;
        if (this.checkIfAlreadyExist(url, textValue)) {
            return;
        }
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        this.addCredential(clientBuilder);
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "[{\"data\":\"" + textValue + "\",\"ttl\":600}]");
        Request request = new Request.Builder().url(url).put(requestBody).build();
        final Call call = clientBuilder.build().newCall(request);
        String response = this.getResponseBody(call);
        logger.info("add text value:{} to sub domain:[{}] for base domain:[{}], result:[{}]", textValue, subDomain, baseDomain, response);
        logger.info("you may get result in:{}", url);
        logger.info("sleep 20 seconds before validation");
        HostUtil.sleepInSeconds(20);
    }

    private void addCredential(OkHttpClient.Builder clientBuilder) {
        clientBuilder.authenticator((route, response) -> {
            String key = EnvUtil.getEnvValue(EnvKeys.GODADDY_KEY);
            String secret = EnvUtil.getEnvValue(EnvKeys.GODADDY_SECRET);
            String fullCredential = "sso-key " + key + ":" + secret;
            return response.request().newBuilder().header("Authorization", fullCredential).build();
        });
    }

    private boolean checkIfAlreadyExist(String url, String digest) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        this.addCredential(builder);
        Request request = new Request.Builder().url(url).get().build();
        final Call call = builder.build().newCall(request);
        String body = this.getResponseBody(call);
        if (StringUtils.contains(body, digest)) {
            logger.info("record already exist with digest:{}", digest);
            return true;
        } else {
            logger.info("current text content:{}, not match", body);
            return false;
        }
    }

    private String getResponseBody(Call call) {
        try {
            Response response = call.execute();
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
