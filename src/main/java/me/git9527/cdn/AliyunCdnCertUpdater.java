package me.git9527.cdn;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.cdn.model.v20180510.SetDomainServerCertificateRequest;
import com.aliyuncs.cdn.model.v20180510.SetDomainServerCertificateResponse;
import com.aliyuncs.profile.DefaultProfile;
import lombok.extern.slf4j.Slf4j;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class AliyunCdnCertUpdater {

    private final DefaultAcsClient acsClient;

    private final String certContent;

    private final String keyContent;

    public AliyunCdnCertUpdater(String certFilePath, String keyFilePath) throws IOException {
        String regionId = EnvUtil.getEnvValue(EnvKeys.CDN_ALI_REGION, "cn-shanghai");
        String regionKey = EnvUtil.getEnvValue(EnvKeys.CDN_ALI_ACCESS_KEY);
        logger.info("Use Aliyun CDN with regionId: {}, accessKey: {}", regionId, regionKey);
        String regionSecret = EnvUtil.getEnvValue(EnvKeys.CDN_ALI_ACCESS_SECRET);
        DefaultProfile profile = DefaultProfile.getProfile(regionId, regionKey, regionSecret);
        acsClient = new DefaultAcsClient(profile);

        certContent = Files.readString(Path.of(certFilePath));
        keyContent = Files.readString(Path.of(keyFilePath));
    }


    public void updateCertificates(String domains) {
        logger.info("Updating CDN certificates for domains: {}", domains);
        List<String> domainList = Arrays.asList(domains.split(","));
        for (String domain : domainList) {
            SetDomainServerCertificateRequest request = new SetDomainServerCertificateRequest();
            request.setDomainName(domain);
            request.setCertType("upload");
            String certName = "my-cert-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
            request.setCertName(certName);
            request.setServerCertificate(certContent);
            request.setPrivateKey(keyContent);
            request.setForceSet("1"); // 覆盖已有配置
            request.setServerCertificateStatus("on");

            try {
                SetDomainServerCertificateResponse response = acsClient.getAcsResponse(request);
                logger.info("Certificate update response for domain {}: RequestId={}, CertName={}", domain, response.getRequestId(), certName);
            } catch (Exception e) {
                logger.error("Failed to update certificate for domain: {}", domain, e);
            }
        }
    }
}
