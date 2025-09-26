package me.git9527;

import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.StreamRequestHandler;
import lombok.extern.slf4j.Slf4j;
import me.git9527.acme.AcmeSigner;
import me.git9527.cdn.AliyunCdnCertUpdater;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import org.apache.commons.lang3.StringUtils;
import org.shredzone.acme4j.Account;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
public class FcAcme implements StreamRequestHandler {

    public static void main(String[] args) throws IOException {
        FcAcme fcAcme = new FcAcme();
        fcAcme.handleRequest(null, null, null);
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        logger.info("acme signer start...");
        AcmeSigner signer = new AcmeSigner();
        String domainList = EnvUtil.getEnvValue(EnvKeys.DOMAIN_LIST, "");
        try {
            if (StringUtils.isNotBlank(domainList) && signer.needOrderNewCertificate(domainList)) {
                Account account = signer.initAccount();
                signer.newOrder(account, domainList);
            } else {
                String cdnDomainList = EnvUtil.getEnvValue(EnvKeys.CDN_ALI_DOMAIN_LIST, "");
                if (StringUtils.isNotBlank(cdnDomainList)) {
                    String domainKey = signer.getDomainKey(domainList);
                    String crtFilePath = signer.getCrtFile(domainKey);
                    String keyFilePath = signer.getKeyFile(domainKey);
                    AliyunCdnCertUpdater updater = new AliyunCdnCertUpdater(crtFilePath, keyFilePath);
                    updater.updateCertificates(domainKey, cdnDomainList);
                } else {
                    logger.info("nothing else to do, ending....");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
