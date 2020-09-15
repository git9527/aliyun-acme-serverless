package me.git9527;

import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.StreamRequestHandler;
import lombok.extern.slf4j.Slf4j;
import me.git9527.acme.AcmeSigner;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import org.shredzone.acme4j.Account;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
public class FcAcme implements StreamRequestHandler {

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        logger.info("start");
        AcmeSigner signer = new AcmeSigner();
        Account account = signer.initAccount();
        String domainList = EnvUtil.getEnvValue(EnvKeys.DOMAIN_LIST);
        signer.newOrder(account, domainList);
        outputStream.write("OK".getBytes());
    }
}
