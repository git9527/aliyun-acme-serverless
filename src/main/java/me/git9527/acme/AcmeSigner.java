package me.git9527.acme;

import lombok.extern.slf4j.Slf4j;
import me.git9527.dns.GoDaddyProvider;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import me.git9527.util.HostUtil;
import org.apache.commons.lang3.StringUtils;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AcmeSigner {

    public Account initAccount() throws IOException, AcmeException {
        String endPoint = EnvUtil.getEnvValue(EnvKeys.SESSION_ENDPOINT, "https://acme-staging-v02.api.letsencrypt.org/directory");
        Session session = new Session(endPoint);
        return this.getAccountInstance(session);
    }

    public void newOrder(Account account, String domainList) throws IOException, AcmeException {
        String[] domains = StringUtils.split(domainList, ",");
        Order order = account.newOrder().domains(domains).create();
        for (Authorization auth : order.getAuthorizations()) {
            if (auth.getStatus() != Status.VALID) {
                Challenge challenge = processAuth(auth);
                this.loopCheckStatus(challenge);
                logger.info("validation SUCCESS for domain:{}", auth.getIdentifier().getDomain());
            }
        }
        this.generateCertification(domains, order);
    }

    private void generateCertification(String[] domains, Order order) throws IOException, AcmeException {
        String domainKey = HostUtil.getHost(domains[0]).replace("*.", "");
        String domainFolder = this.getKeyPairFolder() + "/" + domainKey;
        new File(domainFolder).mkdirs();
        KeyPair domainKeyPair = this.loadOrCreateKeyPair(domainFolder + "/" + domainKey + ".key");
        CSRBuilder builder = new CSRBuilder();
        builder.addDomains(domains);
        builder.sign(domainKeyPair);
        String csrFile = domainFolder + "/" + domainKey + ".csr";
        try (Writer out = new FileWriter(csrFile)) {
            builder.write(out);
            logger.info("write csr file to path:{}", csrFile);
        }
        order.execute(builder.getEncoded());
        this.loopCheckStatus(order);
        Certificate certificate = order.getCertificate();
        logger.info("Success! The certificate for domains {} has been generated!", domains);
        logger.info("Certificate URL: {}", certificate.getLocation());
        String crtFile = domainFolder + "/" + domainKey + ".crt";
        try (FileWriter fw = new FileWriter(crtFile)) {
            certificate.writeCertificate(fw);
        }
    }

    private KeyPair loadOrCreateKeyPair(String keyPairPath) throws IOException {
        if (Files.exists(Paths.get(keyPairPath))) {
            try (FileReader fr = new FileReader(keyPairPath)) {
                return KeyPairUtils.readKeyPair(fr);
            }
        } else {
            KeyPair domainKeyPair = KeyPairUtils.createKeyPair(2048);
            try (FileWriter fw = new FileWriter(keyPairPath)) {
                KeyPairUtils.writeKeyPair(domainKeyPair, fw);
            }
            return domainKeyPair;
        }
    }

    private void loopCheckStatus(AcmeJsonResource resource) {
        int attempts = 10;
        while (resource.getJSON().get("status").asStatus() != Status.VALID && attempts-- > 0) {
            logger.info("failed with status:{}, attempt:{}", resource.getJSON().get("status").asStatus(), attempts);
            try {
                resource.update();
            } catch (AcmeException e) {
                logger.error("fail to update status", e);
            }
            if (resource.getJSON().get("status").asStatus() == Status.INVALID && attempts == 1) {
                throw new RuntimeException("validation failed... Giving up.");
            }
            sleepInSeconds(3);
        }
    }

    private Challenge processAuth(Authorization auth) throws AcmeException {
        Dns01Challenge challenge = auth.findChallenge(Dns01Challenge.TYPE);
        String host = auth.getIdentifier().getDomain();
        String digest = challenge.getDigest();
        GoDaddyProvider provider = new GoDaddyProvider();
        provider.addTextRecord(host, digest);
        logger.info("sleep 3 seconds before validation");
        this.sleepInSeconds(3);
        challenge.trigger();
        return challenge;
    }

    private void sleepInSeconds(int time) {
        try {
            TimeUnit.SECONDS.sleep(time);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Account getAccountInstance(Session session) throws IOException, AcmeException {
        String keyPairFolder = getKeyPairFolder();
        String userKeyPair = keyPairFolder + "/userKey.pem";
        KeyPair keyPair = this.loadOrCreateKeyPair(userKeyPair);
        Account account = new AccountBuilder()
                .agreeToTermsOfService()
                .useKeyPair(keyPair)
                .create(session);
        logger.info("Registered a new user, URL: {}", account.getLocation());
        URL url = account.getLocation();
        Login login = session.login(url, keyPair);
        return login.getAccount();
    }

    private Account getExistingAccount(KeyPair keyPair, Session session) throws AcmeException {
        Account account = new AccountBuilder()
                .onlyExisting()         // Do not create a new account
                .useKeyPair(keyPair)
                .create(session);
        URL url = account.getLocation();
        Login login = session.login(url, keyPair);
        return login.getAccount();
    }

    private Account registerAccount(KeyPair keyPair, Session session) throws AcmeException {
        Login login = new AccountBuilder()
                .addContact(EnvUtil.getEnvValue(EnvKeys.ACCOUNT_CONTACT, "mailto:acme@aliyun-serverless.com"))
                .agreeToTermsOfService()
                .useKeyPair(keyPair)
                .createLogin(session);
        return login.getAccount();
    }

    private String getKeyPairFolder() {
        String folder = EnvUtil.getEnvValue(EnvKeys.KEYPAIR_FOLDER, "/tmp/acme");
        new File(folder).mkdirs();
        return folder;
    }
}
