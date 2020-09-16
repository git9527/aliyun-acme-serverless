package me.git9527.dns;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.alidns.model.v20150109.AddDomainRecordRequest;
import com.aliyuncs.alidns.model.v20150109.AddDomainRecordResponse;
import com.aliyuncs.alidns.model.v20150109.DescribeSubDomainRecordsRequest;
import com.aliyuncs.alidns.model.v20150109.DescribeSubDomainRecordsResponse;
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

    public AliyunProvider(String host) {
        super(host);
    }

    @Override
    public void addTextRecord(String digest) {
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

    private String addNewRecord(String digest) {
        IAcsClient client = this.getClient();
        AddDomainRecordRequest recordRequest = new AddDomainRecordRequest();
        recordRequest.setDomainName(baseDomain);
        recordRequest.setRR(subDomain);
        recordRequest.setType("TXT");
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
