package me.git9527.dns;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import me.git9527.dns.pojo.*;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import me.git9527.util.HostUtil;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;

@Slf4j
public class CloudFlareProvider extends DnsProvider {
    
    private final static String BASE_PREFIX = "https://api.cloudflare.com/client/v4";
    
    private String verifiedId;
    
    private String zoneId;

    public CloudFlareProvider(String host, String digest) {
        super(host, digest);
        logger.info("checking host:{} with digest:{}", host, digest);
    }

    @Override
    public void addTextRecord() {
        String zoneId = getZoneIdForDomain();
        logger.info("Found zone id: {} for domain: {}", zoneId, super.baseDomain);
        this.zoneId = zoneId;
        DnsRecord currentRecord = this.getCurrentTxt();
        String currentValue = currentRecord != null ? currentRecord.getContent() : "";
        if (StringUtils.equals(currentValue, digest)) {
            logger.info("txt value already exist:{}", digest);
            this.verifiedId = currentRecord.getId();
            return;
        }
        logger.info("Found current value: [{}] for subdomain: {}", currentValue, super.subDomain);
        if (currentRecord != null) {
            updateDnsValue(currentRecord);
        } else {
            createDnsValue();   
        }
        int sleep = NumberUtils.toInt(EnvUtil.getEnvValue(EnvKeys.DNS_SLEEP, "30"));
        logger.info("sleep {} seconds before validation", sleep);
        HostUtil.sleepInSeconds(sleep);
        DnsRecord afterUpdated = this.getCurrentTxt();
        logger.info("txt after update:[{}] with id:{}", afterUpdated.getContent(), afterUpdated.getId());
        if (afterUpdated.getContent().equalsIgnoreCase(this.digest)) {
            this.verifiedId = afterUpdated.getId();
        }
    }
    
    @Override
    public void removeValidatedRecord() {
        if (this.verifiedId != null) {
            String url = BASE_PREFIX + "/zones/" + zoneId + "/dns_records/" + this.verifiedId;
            Request.Builder request = new Request.Builder().url(url).delete();
            String body = getResponseBody(request);
            logger.info("remove record result: {}", body);
        } else {
            logger.info("Dns record not verified");
        }
    }
    
    private String getZoneIdForDomain() {
        String url = BASE_PREFIX + "/zones";
        Request.Builder request = new Request.Builder().url(url).get();
        String body = getResponseBody(request);
        ZoneResp zoneResp = toJson(body, ZoneResp.class);
        for (ZoneInfo zoneInfo: zoneResp.getZoneInfos()) {
            if (zoneInfo.getName().endsWith(super.baseDomain)) {
                return zoneInfo.getId();
            }
        }
        throw new RuntimeException("Can't find zone info for domain: " + super.baseDomain);
    }

    private void addCredential(Request.Builder builder) {
        String email = EnvUtil.getEnvValue(EnvKeys.DNS_CF_EMAIL);
        String key = EnvUtil.getEnvValue(EnvKeys.DNS_CF_KEY);
        builder.header("X-Auth-Email", email).header("X-Auth-Key", key).header("Content-Type", "application/json").build();
    }

    private DnsRecord getCurrentTxt() {
        String url = BASE_PREFIX + "/zones/" + zoneId + "/dns_records/";
        Request.Builder request = new Request.Builder().url(url).get();
        String body = this.getResponseBody(request);
        DnsListResp dnsListResp = toJson(body, DnsListResp.class);
        if (dnsListResp.isSuccess()) {
            for (DnsRecord dnsRecord: dnsListResp.getDnsRecords()) {
                if (dnsRecord.getType().equalsIgnoreCase("TXT") && dnsRecord.getName().equalsIgnoreCase(super.toBeVerified())) {
                    return dnsRecord;
                }
            }
        }
        return null;
    }
    
    private <T> T toJson(String content, Class<T> clazz) {
        Gson gson = new Gson();
        return gson.fromJson(content, clazz);
    } 
    
    private void updateDnsValue(DnsRecord record) {
        String url = BASE_PREFIX + "/zones/" + zoneId + "/dns_records/" + record.getId();
        Gson gson = new Gson();
        record.setId(null);
        record.setContent(this.digest);
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), gson.toJson(record));
        Request.Builder builder = new Request.Builder().url(url).put(requestBody);
        String response = this.getResponseBody(builder);
        CloudFlareResp resp = toJson(response, CloudFlareResp.class);
        logger.info("updated txt value:{} to sub domain:[{}] for base domain:[{}], result:[{}]", digest, subDomain, baseDomain, resp.isSuccess());
    }
    
    private void createDnsValue() {
        String url = BASE_PREFIX + "/zones/" + zoneId + "/dns_records/";
        Gson gson = new Gson();
        DnsRecord dnsRecord = new DnsRecord();
        dnsRecord.setContent(this.digest);
        dnsRecord.setName(super.toBeVerified());
        dnsRecord.setProxied(false);
        dnsRecord.setType("TXT");
        dnsRecord.setComment("Auto inserted");
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), gson.toJson(dnsRecord));
        Request.Builder builder = new Request.Builder().url(url).post(requestBody);
        String response = this.getResponseBody(builder);
        CloudFlareResp resp = toJson(response, CloudFlareResp.class);
        logger.info("create txt value:{} to sub domain:[{}] for base domain:[{}], result:[{}]", digest, subDomain, baseDomain, resp.isSuccess());
    }

    private String getResponseBody(Request.Builder builder) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        this.addCredential(builder);
        final Call call = clientBuilder.build().newCall(builder.build());
        try {
            Response response = call.execute();
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
