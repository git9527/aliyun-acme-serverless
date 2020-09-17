package me.git9527.dns;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.alidns.model.v20150109.*;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import lombok.extern.slf4j.Slf4j;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import me.git9527.util.HostUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.List;

@Slf4j
public class AliyunProvider extends DnsProvider {

    public AliyunProvider(String host, String digest) {
        super(host, digest);
    }

    @Override
    public void addTextRecord() {
        String current = this.getCurrentTxt();
        if (StringUtils.equals(current, digest)) {
            logger.info("txt value already exist:[{}]", digest);
            return;
        } else {
            logger.info("current txt:[{}], expected:[{}]", current, digest);
        }
        String recordId = this.addNewRecord(digest);
        logger.info("add text value:{} to sub domain:[{}] for base domain:[{}], record id:{}", digest, subDomain, baseDomain, recordId);
        int sleep = NumberUtils.toInt(EnvUtil.getEnvValue(EnvKeys.DNS_SLEEP, "30"));
        logger.info("sleep {} seconds before validation", sleep);
        HostUtil.sleepInSeconds(sleep);
        String afterUpdated = this.getCurrentTxt();
        logger.info("txt after update:[{}]", afterUpdated);
    }

    @Override
    public void removeValidatedRecord() {
        IAcsClient client = this.getClient();
        DeleteSubDomainRecordsRequest request = new DeleteSubDomainRecordsRequest();
        request.setDomainName(baseDomain);
        request.setRR(subDomain);
        request.setType(RECORD_TYPE);
        try {
            DeleteSubDomainRecordsResponse recordResponse = client.getAcsResponse(request);
            String requestId = recordResponse.getRequestId();
            logger.info("remove validated record with request id:{}", requestId);
        } catch (ClientException e) {
            logger.error("fail to remove record", e);
        }
    }

    private String addNewRecord(String digest) {
        IAcsClient client = this.getClient();
        AddDomainRecordRequest recordRequest = new AddDomainRecordRequest();
        recordRequest.setDomainName(baseDomain);
        recordRequest.setRR(subDomain);
        recordRequest.setType(RECORD_TYPE);
        recordRequest.setValue(digest);
        try {
            AddDomainRecordResponse recordResponse = client.getAcsResponse(recordRequest);
            return recordResponse.getRecordId();
        } catch (ClientException e) {
            throw new RuntimeException(e);
        }
    }

    private String getCurrentTxt() {
        IAcsClient client = this.getClient();
        DescribeSubDomainRecordsRequest request = new DescribeSubDomainRecordsRequest();
        request.setSubDomain(subDomain + "." + baseDomain);
        try {
            DescribeSubDomainRecordsResponse response = client.getAcsResponse(request);
            List<DescribeSubDomainRecordsResponse.Record> records = response.getDomainRecords();
            if (records == null || records.size() == 0) {
                return "";
            } else {
                return records.get(0).getValue();
            }
        } catch (ClientException e) {
            logger.error("fail to get current txt", e);
            return "";
        }

    }

    private IAcsClient getClient() {
        String accessKey = EnvUtil.getEnvValue(EnvKeys.DNS_ALI_ACCESS_KEY);
        String accessSecret = EnvUtil.getEnvValue(EnvKeys.DNS_ALI_ACCESS_SECRET);
        IClientProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKey, accessSecret);
        return new DefaultAcsClient(profile);
    }
}
