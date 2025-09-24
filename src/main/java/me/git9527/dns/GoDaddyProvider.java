package me.git9527.dns;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import me.git9527.dns.pojo.GoDaddyRecord;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import me.git9527.util.HostUtil;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class GoDaddyProvider extends DnsProvider {

    public GoDaddyProvider(String host, String digest) {
        super(host, digest);
    }

    @Override
    public void addTextRecord() {
        String url = getBaseUrlWithSubDomain();
        String current = this.getCurrentTxt(url);
        if (StringUtils.equals(current, digest)) {
            logger.info("txt value already exist:{}", digest);
            return;
        } else {
            logger.info("current txt:[{}], expected:[{}]", current, digest);
        }
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "[{\"data\":\"" + digest + "\",\"ttl\":600}]");
        Request request = new Request.Builder().url(url).put(requestBody).build();
        String response = this.getResponseBody(request);
        logger.info("add text value:{} to sub domain:[{}] for base domain:[{}], result:[{}]", digest, subDomain, baseDomain, response);
        logger.info("you may get result in:{}", url);
        int sleep = NumberUtils.toInt(EnvUtil.getEnvValue(EnvKeys.DNS_SLEEP, "30"));
        logger.info("sleep {} seconds before validation", sleep);
        HostUtil.sleepInSeconds(sleep);
        String afterUpdated = this.getCurrentTxt(url);
        logger.info("txt after update:[{}]", afterUpdated);
    }

    private String getBaseUrlWithSubDomain() {
        return this.getBaseUrlWithoutSubDomain() + "/" + subDomain;
    }

    private String getBaseUrlWithoutSubDomain() {
        return "https://api.godaddy.com/v1/domains/" + baseDomain + "/records/" + RECORD_TYPE;
    }

    @Override
    public void removeValidatedRecord() {
        List<GoDaddyRecord> records = getCurrentRecords();
        logger.info("get {} txt records before remove", records.size());
        List<GoDaddyRecord> notRelated = this.removeAffectRecords(records);
        this.uploadNotRelatedRecords(notRelated);
    }

    private void uploadNotRelatedRecords(List<GoDaddyRecord> notRelated) {
        String body = new Gson().toJson(notRelated);
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body);
        String url = this.getBaseUrlWithoutSubDomain();
        Request request = new Request.Builder().url(url).put(requestBody).build();
        this.getResponseBody(request);
        logger.info("re-upload {} txt records", notRelated.size());
    }

    private List<GoDaddyRecord> removeAffectRecords(List<GoDaddyRecord> sources) {
        List<GoDaddyRecord> notRelated = new ArrayList<>();
        for (GoDaddyRecord source : sources) {
            if (digest.equals(source.getData()) && subDomain.equals(source.getName())) {
                logger.info("found new added record:{} with digest:{}", subDomain, digest);
            } else {
                notRelated.add(source);
            }
        }
        return notRelated;
    }

    private List<GoDaddyRecord> getCurrentRecords() {
        String url = this.getBaseUrlWithoutSubDomain();
        Request request = new Request.Builder().url(url).get().build();
        String body = this.getResponseBody(request);
        Gson gson = new Gson();
        Type recordsType = new TypeToken<List<GoDaddyRecord>>() {
        }.getType();
        return gson.fromJson(body, recordsType);
    }


    private void addCredential(OkHttpClient.Builder clientBuilder) {
        clientBuilder.authenticator((route, response) -> {
            String key = EnvUtil.getEnvValue(EnvKeys.DNS_GODADDY_KEY);
            String secret = EnvUtil.getEnvValue(EnvKeys.DNS_GODADDY_SECRET);
            String fullCredential = "sso-key " + key + ":" + secret;
            return response.request().newBuilder().header("Authorization", fullCredential).header("Accept", "application/json").build();
        });
    }

    private String getCurrentTxt(String url) {
        Request request = new Request.Builder().url(url).get().build();
        String body = this.getResponseBody(request);
        return StringUtils.substringBetween(body, "\"data\":\"", "\"");
    }

    private String getResponseBody(Request request) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        this.addCredential(builder);
        final Call call = builder.build().newCall(request);
        try {
            Response response = call.execute();
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
