package io.dataease.plugins.datasource.prometheus.engine.okhttp.base;

/**
 * Created by lkh on 2018/1/8.
 */



import io.dataease.plugins.datasource.prometheus.engine.okhttp.HttpClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;

public class SSLUtils {
    private static final Logger LOG = LoggerFactory.getLogger(SSLUtils.class);

    private SSLUtils() {
        //Utility
    }

    /*
        public static boolean isHttpsAvailable(Config config) {
            //config.setMasterUrl(Config.HTTPS_PROTOCOL_PREFIX + config.getMasterUrl());

            Config sslConfig = new ConfigBuilder(config)
                    .withMasterUrl(Config.HTTPS_PROTOCOL_PREFIX + config.getMasterUrl())
                    .withRequestTimeout(1000)
                    .withConnectionTimeout(1000)
                    .build();

            OkHttpClient client = HttpClientUtils.createHttpClient(config);
            try {
                Request request = new Request.Builder().get().url(sslConfig.getMasterUrl())
                        .build();
                Response response = client.newCall(request).execute();
                try (ResponseBody body = response.body()) {
                    return response.isSuccessful();
                }
            } catch (Throwable t) {
                LOG.warn("SSL handshake failed. Falling back to insecure connection.");
            } finally {
                if (client != null && client.connectionPool() != null) {
                    client.connectionPool().evictAll();
                }
            }
            return false;
        }
    */
    public static SSLContext sslContext(HttpClientConfig config) throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, IOException, InvalidKeySpecException, KeyManagementException {
        return sslContext(keyManagers(config), trustManagers(config), config.isTrustCerts());
    }

    public static SSLContext sslContext(KeyManager[] keyManagers, TrustManager[] trustManagers, boolean trustCerts) throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, IOException, InvalidKeySpecException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(keyManagers, trustManagers, new SecureRandom());
        return sslContext;
    }

    public static TrustManager[] trustManagers(HttpClientConfig config) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        return trustManagers(config.getCaCertData(), config.getCaCertFile(), config.isTrustCerts(), config.getTrustStoreFile(), config.getTrustStorePassphrase());
    }

    public static TrustManager[] trustManagers(String certData, String certFile, boolean isTrustCerts, String trustStoreFile, String trustStorePassphrase) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore trustStore = null;
        if (isTrustCerts) {
            return new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] chain, String s) {
                        }

                        public void checkServerTrusted(X509Certificate[] chain, String s) {
                        }

                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
        } else if (Utils.isNotNullOrEmpty(certData) || Utils.isNotNullOrEmpty(certFile)) {
            trustStore = CertUtils.createTrustStore(certData, certFile, trustStoreFile, trustStorePassphrase);
        }
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }

    public static KeyManager[] keyManagers(HttpClientConfig config) throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, InvalidKeySpecException, IOException {
        return keyManagers(config.getClientCertData(), config.getClientCertFile(), config.getClientKeyData(), config.getClientKeyFile(), config.getClientKeyAlgo(), config.getClientKeyPassphrase(), config.getKeyStoreFile(), config.getKeyStorePassphrase());
    }

    public static KeyManager[] keyManagers(String certData, String certFile, String keyData, String keyFile, String algo, String passphrase, String keyStoreFile, String keyStorePassphrase) throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, InvalidKeySpecException, IOException {
        KeyManager[] keyManagers = null;
        if ((Utils.isNotNullOrEmpty(certData) || Utils.isNotNullOrEmpty(certFile)) && (Utils.isNotNullOrEmpty(keyData) || Utils.isNotNullOrEmpty(keyFile))) {
            KeyStore keyStore = CertUtils.createKeyStore(certData, certFile, keyData, keyFile, algo, passphrase, keyStoreFile, keyStorePassphrase);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, passphrase.toCharArray());
            keyManagers = kmf.getKeyManagers();
        }
        return keyManagers;
    }

    public static KeyManager[] keyManagers(InputStream certInputStream, InputStream keyInputStream, String algo, String passphrase, String keyStoreFile, String keyStorePassphrase) throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, InvalidKeySpecException, IOException {
        KeyStore keyStore = CertUtils.createKeyStore(certInputStream, keyInputStream, algo, passphrase.toCharArray(), keyStoreFile, keyStorePassphrase.toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, passphrase.toCharArray());
        return kmf.getKeyManagers();
    }
}
