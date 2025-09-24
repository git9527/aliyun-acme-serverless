package me.git9527.acme;

import lombok.extern.slf4j.Slf4j;
import me.git9527.dns.AliyunProvider;
import me.git9527.dns.CloudFlareProvider;
import me.git9527.dns.DnsProvider;
import me.git9527.dns.GoDaddyProvider;
import me.git9527.oss.AliyunStorer;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import me.git9527.util.HostUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

@Slf4j
public class AcmeSigner {

    private final AliyunStorer storer = new AliyunStorer();

    public Account initAccount() throws IOException, AcmeException {
        String endPoint = EnvUtil.getEnvValue(EnvKeys.SESSION_ENDPOINT, "https://acme-staging-v02.api.letsencrypt.org/directory");
        Session session = new Session(endPoint);
        return this.getAccountInstance(session);
    }

    public String getDomainKey(String domainList) {
        String[] domains = StringUtils.split(domainList, ",");
        return HostUtil.getHost(domains[0]).replace("*.", "");
    }

    public String getCrtFile(String domainKey) {
        String domainFolder = this.getKeyPairFolder() + "/" + domainKey;
        return domainFolder + "/" + domainKey + ".crt";
    }

    public String getKeyFile(String domainKey) {
        String domainFolder = this.getKeyPairFolder() + "/" + domainKey;
        String localKey = domainFolder + "/" + domainKey + ".key";
        if (Files.exists(Path.of(localKey))) {
            return localKey;
        } else {
            storer.downloadFile(localKey);
            return localKey;
        }
    }

    public boolean needOrderNewCertificate(String domainList) {
        String domainKey = this.getDomainKey(domainList);
        String crtFile = getCrtFile(domainKey);
        if (storer.isFileExist(crtFile)) {
            storer.downloadFile(crtFile);
            return isCloseToExpire(domainKey, crtFile);
        } else {
            logger.info("crt file not exist for:{}, order new certificate", domainKey);
            return true;
        }
    }

    private boolean isCloseToExpire(String domainKey, String crtFile) {
        try {
            X509Certificate myCert = (X509Certificate) CertificateFactory
                    .getInstance("X509")
                    .generateCertificate(new FileInputStream(crtFile));
            Instant expireDay = myCert.getNotAfter().toInstant();
            Collection<List<?>> domains = myCert.getSubjectAlternativeNames();
            for (List<?> domain : domains) {
                logger.info("existing cert contains domain:{}", domain.get(1));
            }
            String day = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.ofHours(8)).format(expireDay);
            Duration duration = Duration.between(Instant.now(), expireDay);
            long threshold = NumberUtils.toLong(EnvUtil.getEnvValue(EnvKeys.EXPIRE_BEFORE_DAY, "30"));
            long days = duration.toDays();
            if (days < threshold) {
                logger.info("current certificate expired at: {}, only {} days, less than {} days, renew", day, days, threshold);
                return true;
            } else {
                logger.info("current certificate expired at: {}, still got {} days", day, days);
                return false;
            }
        } catch (Exception e) {
            logger.error("error to check certificate expiration for domain:{}", domainKey, e);
            return true;
        }
    }

    public void newOrder(Account account, String domainList) throws IOException, AcmeException {
        String[] domains = StringUtils.split(domainList, ",");
        Order order = account.newOrder().domains(domains).create();
        for (Authorization auth : order.getAuthorizations()) {
            if (auth.getStatus() != Status.VALID) {
                DnsProvider provider = this.getCurrentDnsProvider(auth);
                Challenge challenge = processAuth(auth, provider);
                this.loopCheckStatus(challenge);
                logger.info("validation :[{}] for domain:[{}]", auth.getStatus(), auth.getIdentifier().getDomain());
                provider.removeValidatedRecord();
            } else {
                logger.info("auth already with status:[{}] for domain:[{}]", auth.getStatus(), auth.getIdentifier().getDomain());
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
        storer.uploadFile(csrFile);
        order.execute(builder.getEncoded());
        this.loopCheckStatus(order);
        Certificate certificate = order.getCertificate();
        logger.info("Success! The certificate for domains {} has been generated!", domains);
        logger.info("Certificate URL: {}", certificate.getLocation());
        String crtFile = domainFolder + "/" + domainKey + ".crt";
        try (FileWriter fw = new FileWriter(crtFile)) {
            certificate.writeCertificate(fw);
            logger.info("write crt file to path:{}", csrFile);
        }
        storer.uploadFile(crtFile);
    }

    private KeyPair loadOrCreateKeyPair(String keyPairPath) throws IOException {
        if (storer.isFileExist(keyPairPath)) {
            storer.downloadFile(keyPairPath);
            try (FileReader fr = new FileReader(keyPairPath)) {
                return KeyPairUtils.readKeyPair(fr);
            }
        } else {
            KeyPair domainKeyPair = KeyPairUtils.createKeyPair(2048);
            try (FileWriter fw = new FileWriter(keyPairPath)) {
                KeyPairUtils.writeKeyPair(domainKeyPair, fw);
            }
            storer.uploadFile(keyPairPath);
            return domainKeyPair;
        }
    }

    private void loopCheckStatus(AcmeJsonResource resource) {
        int attempts = NumberUtils.toInt(EnvUtil.getEnvValue(EnvKeys.DNS_LOOP_RETRY, "10"));
        while (resource.getJSON().get("status").asStatus() != Status.VALID && attempts-- > 0) {
            Problem problem = resource.getJSON().get("error").map(it -> it.asProblem(resource.getLocation()))
                    .orElse(null);
            if (problem != null) {
                logger.info("failed with detail:{}, attempt:{}", problem.getDetail(), attempts);
            } else {
                logger.info("failed with status:{}, attempt:{}", resource.getJSON().get("status").asStatus(), attempts);
            }
            try {
                resource.fetch();
            } catch (AcmeException e) {
                logger.error("fail to update status", e);
            }
            if (resource.getJSON().get("status").asStatus() == Status.INVALID && attempts == 1) {
                throw new RuntimeException("validation failed... Giving up.");
            }
            HostUtil.sleepInSeconds(3);
        }
    }

    private Challenge processAuth(Authorization auth, DnsProvider provider) throws AcmeException {
        Challenge challenge = auth.findChallenge(Dns01Challenge.TYPE).get();
        provider.addTextRecord();
        challenge.trigger();
        HostUtil.sleepInSeconds(1);
        return challenge;
    }

    private DnsProvider getCurrentDnsProvider(Authorization auth) {
        String host = auth.getIdentifier().getDomain();
        Dns01Challenge challenge = (Dns01Challenge) auth.findChallenge(Dns01Challenge.TYPE).get();
        String digest = challenge.getDigest();
        String dnsProvider = EnvUtil.getEnvValue(EnvKeys.DNS_PROVIDER, "CLOUDFLARE");
        if (Strings.CI.equals(dnsProvider, "GODADDY")) {
            return new GoDaddyProvider(host, digest);
        } else if (Strings.CI.equals(dnsProvider, "ALIYUN")) {
            return new AliyunProvider(host, digest);
        } else if (Strings.CI.equals(dnsProvider, "CLOUDFLARE")) {
            return new CloudFlareProvider(host, digest);
        } else {
            throw new IllegalArgumentException("not support dns provider:" + dnsProvider);
        }
    }

    private Account getAccountInstance(Session session) throws IOException, AcmeException {
        String keyPairFolder = getKeyPairFolder();
        String userKeyPair = keyPairFolder + "/userKey.pem";
        KeyPair keyPair = this.loadOrCreateKeyPair(userKeyPair);
        Account account = new AccountBuilder()
                .agreeToTermsOfService()
                .addContact("git9527")
                .addEmail("support@etradeunion.cn")
                .useKeyPair(keyPair)
                .create(session);
        logger.info("Registered a new user, URL: {}", account.getLocation());
        URL url = account.getLocation();
        Login login = session.login(url, keyPair);
        return login.getAccount();
    }

    private String getKeyPairFolder() {
        String folder = EnvUtil.getEnvValue(EnvKeys.KEYPAIR_FOLDER, "/tmp/acme");
        new File(folder).mkdirs();
        return folder;
    }
}
