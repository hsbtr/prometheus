package io.dataease.plugins.datasource.prometheus.engine.okhttp;

import lombok.Data;
import okhttp3.TlsVersion;

import static okhttp3.TlsVersion.TLS_1_2;


@Data
public class HttpClientConfig {

    private String masterUrl;
    private boolean trustCerts;
    private String username;
    private String password;
    private int connectionTimeout;
    private int requestTimeout;
    private int websocketPingInterval;
    private int maxConcurrentRequestsPerHost;

    private String httpProxy;
    private String httpsProxy;
    private String proxyUsername;
    private String proxyPassword;
    private String userAgent;
    private TlsVersion[] tlsVersions = new TlsVersion[]{TLS_1_2};
    private String[] noProxy;

    private String caCertData;
    private String caCertFile;
    private String trustStoreFile;
    private String trustStorePassphrase;
    private String clientCertFile;
    private String clientCertData;
    private String clientKeyFile;
    private String clientKeyData;
    private String clientKeyAlgo = "RSA";
    private String clientKeyPassphrase = "changeit";
    private String keyStoreFile;
    private String keyStorePassphrase;
    private int maxConnection;
    private int clusterId; // 集群ID
    private String oauthToken;

    public static final String HTTP_PROTOCOL_PREFIX = "http://";
    public static final String HTTPS_PROTOCOL_PREFIX = "https://";

}