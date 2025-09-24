package me.git9527.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;


public class X509Utils {

    public static Instant getExpireDayFromCert(String certPath) throws CertificateException, FileNotFoundException {
        X509Certificate myCert = (X509Certificate) CertificateFactory
                .getInstance("X509")
                .generateCertificate(new FileInputStream(certPath));
        return myCert.getNotAfter().toInstant();
    }
}
