package me.git9527;

import lombok.extern.slf4j.Slf4j;
import me.git9527.acme.AcmeSigner;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.exception.AcmeException;

import java.io.IOException;

@Slf4j
public class ServerlessMain {

    public static void main(String[] args) throws IOException, AcmeException {
        logger.info("start");
        AcmeSigner signer = new AcmeSigner();
        Account account = signer.initAccount();
        String domainList = EnvUtil.getEnvValue(EnvKeys.DOMAIN_LIST);
        signer.newOrder(account, domainList);
    }
}
