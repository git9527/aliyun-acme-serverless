package me.git9527.acme;

import lombok.extern.slf4j.Slf4j;
import me.git9527.dns.GoDaddyProvider;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import org.apache.commons.lang3.StringUtils;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AcmeSigner {

    public Account initAccount() throws IOException {
        String endPoint = EnvUtil.getEnvValue(EnvKeys.SESSION_ENDPOINT, "https://acme-staging-v02.api.letsencrypt.org/directory");
        Session session = new Session(endPoint);
        return this.getAccountInstance(session);
    }

    public void newOrder(Account account, String domainList) {
        Order order = null;
        try {
            order = account.newOrder().domains(StringUtils.split(domainList, ",")).create();
        } catch (AcmeException e) {
            throw new RuntimeException(e);
        }
        for (Authorization auth : order.getAuthorizations()) {
            if (auth.getStatus() != Status.VALID) {
                processAuth(auth);
                while (auth.getStatus() != Status.VALID) {
                    this.sleepInSeconds(3);
                    try {
                        auth.update();
                    } catch (AcmeException e) {
                        throw new RuntimeException(e);
                    }
                    logger.info("domain:[{}] get auth result:{}", auth.getIdentifier().getDomain(), auth.getStatus());
                }
            }
        }
    }

    private void processAuth(Authorization auth) {
        Dns01Challenge challenge = auth.findChallenge(Dns01Challenge.TYPE);
        String host = auth.getIdentifier().getDomain();
        String digest = challenge.getDigest();
        GoDaddyProvider provider = new GoDaddyProvider();
        provider.addTextRecord(host, digest);
        logger.info("sleep 3 seconds before validation");
        this.sleepInSeconds(3);
        try {
            challenge.trigger();
        } catch (AcmeException e) {
            throw new RuntimeException(e);
        }
    }

    private void sleepInSeconds(int time) {
        try {
            TimeUnit.SECONDS.sleep(time);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Account getAccountInstance(Session session) throws IOException {
        String keyPairPath = EnvUtil.getEnvValue(EnvKeys.KEYPAIR_PATH, "/tmp/keypair.pem");
        if (Files.exists(Paths.get(keyPairPath))) {
            logger.info("key pair key already exist");
            try (FileReader fr = new FileReader(keyPairPath)) {
                KeyPair keyPair = KeyPairUtils.readKeyPair(fr);
                return this.getExistingAccount(keyPair, session);
            }
        } else {
            logger.info("key pair key not exist, creating");
            KeyPair keyPair = KeyPairUtils.createKeyPair(2048);
            try (FileWriter fw = new FileWriter(keyPairPath)) {
                KeyPairUtils.writeKeyPair(keyPair, fw);
                return this.registerAccount(keyPair, session);
            }
        }
    }

    private Account getExistingAccount(KeyPair keyPair, Session session) {
        Account account = null;
        try {
            account = new AccountBuilder()
                    .onlyExisting()         // Do not create a new account
                    .useKeyPair(keyPair)
                    .create(session);
        } catch (AcmeException e) {
            throw new RuntimeException(e);
        }
        URL url = account.getLocation();
        Login login = session.login(url, keyPair);
        return login.getAccount();
    }

    private Account registerAccount(KeyPair keyPair, Session session) {
        Login login = null;
        try {
            login = new AccountBuilder()
                    .addContact(EnvUtil.getEnvValue(EnvKeys.ACCOUNT_CONTACT, "mailto:acme@aliyun-serverless.com"))
                    .agreeToTermsOfService()
                    .useKeyPair(keyPair)
                    .createLogin(session);
        } catch (AcmeException e) {
            throw new RuntimeException(e);
        }
        return login.getAccount();
    }
}
