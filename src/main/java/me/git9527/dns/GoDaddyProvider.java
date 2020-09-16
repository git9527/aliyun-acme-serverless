package me.git9527.dns;

import lombok.extern.slf4j.Slf4j;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import me.git9527.util.HostUtil;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;

@Slf4j
public class GoDaddyProvider extends DnsProvider {

    public GoDaddyProvider(String host) {
        super(host);
    }

    @Override
    public void addTextRecord(String digest) {
        String url = "https://api.godaddy.com/v1/domains/" + baseDomain + "/records/TXT/" + subDomain;
        String current = this.getCurrentTxt(url);
        if (StringUtils.equals(current, digest)) {
            logger.info("txt value already exist:{}", digest);
            return;
        } else {
            logger.info("current txt:[{}], expected:[{}]", current, digest);
        }
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        this.addCredential(clientBuilder);
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "[{\"data\":\"" + digest + "\",\"ttl\":600}]");
        Request request = new Request.Builder().url(url).put(requestBody).build();
        final Call call = clientBuilder.build().newCall(request);
        String response = this.getResponseBody(call);
        logger.info("add text value:{} to sub domain:[{}] for base domain:[{}], result:[{}]", digest, subDomain, baseDomain, response);
        logger.info("you may get result in:{}", url);
        int sleep = NumberUtils.toInt(EnvUtil.getEnvValue(EnvKeys.DNS_SLEEP, "30"));
        logger.info("sleep {} seconds before validation", sleep);
        HostUtil.sleepInSeconds(sleep);
        String afterUpdated = this.getCurrentTxt(url);
        logger.info("txt after update:[{}]", afterUpdated);
    }

    private void addCredential(OkHttpClient.Builder clientBuilder) {
        clientBuilder.authenticator((route, response) -> {
            String key = EnvUtil.getEnvValue(EnvKeys.DNS_GODADDY_KEY);
            String secret = EnvUtil.getEnvValue(EnvKeys.DNS_GODADDY_SECRET);
            String fullCredential = "sso-key " + key + ":" + secret;
            return response.request().newBuilder().header("Authorization", fullCredential).build();
        });
    }

    private String getCurrentTxt(String url) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        this.addCredential(builder);
        Request request = new Request.Builder().url(url).get().build();
        final Call call = builder.build().newCall(request);
        String body = this.getResponseBody(call);
        return StringUtils.substringBetween(body, "\"data\":\"", "\"");
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
