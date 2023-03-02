package io.dataease.plugins.datasource.prometheus.engine.okhttp;



import cn.hutool.core.util.ObjectUtil;
import io.dataease.plugins.datasource.prometheus.engine.okhttp.base.MD5Util;
import io.dataease.plugins.datasource.prometheus.engine.okhttp.base.SSLUtils;
import io.dataease.plugins.datasource.prometheus.engine.okhttp.base.Utils;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static okhttp3.ConnectionSpec.CLEARTEXT;

public class HttpClientUtils {
    private static final Logger logger = LoggerFactory.getLogger(HttpClientUtils.class);
    private static final Map<String, OkHttpClient> map_clients = new ConcurrentHashMap<>();

    private static OkHttpClient okHttpClient2;

    // 饿汉式单例
    public static OkHttpClient getInstance(final HttpClientConfig config) {
        if (map_clients.containsKey(md5Key(config))) {
            return map_clients.get(md5Key(config));
        }
        OkHttpClient okHttpClient = createHttpClient(config);
        map_clients.put(md5Key(config), okHttpClient);
        return okHttpClient;
    }

    public static String md5Key(HttpClientConfig config) {
        return MD5Util.getMD5InHex(config.getClusterId() + "/" + config.getMasterUrl() + "/" + config.getUsername() + "/" + config.getPassword() + "/" + config.getOauthToken());
    }

    /**
     * 懒汉式单例
     */
    public static OkHttpClient getOkHttpClient(HttpClientConfig config) {
        if (okHttpClient2 == null) {
            okHttpClient2 = createHttpClient(config);
        }
        return okHttpClient2;
    }

    private HttpClientUtils() {
    }

    public static OkHttpClient createHttpClient(final HttpClientConfig config) {
        try {
            OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();

            // Follow any redirects
            httpClientBuilder.followRedirects(true);
            httpClientBuilder.followSslRedirects(true);

            if (config.isTrustCerts()) {
                httpClientBuilder.hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String s, SSLSession sslSession) {
                        return true;
                    }
                });
            }

            TrustManager[] trustManagers = SSLUtils.trustManagers(config);
            KeyManager[] keyManagers = SSLUtils.keyManagers(config);

            if (keyManagers != null || trustManagers != null || config.isTrustCerts()) {
                X509TrustManager trustManager = null;
                if (trustManagers != null && trustManagers.length == 1) {
                    trustManager = (X509TrustManager) trustManagers[0];
                }

                try {
                    SSLContext sslContext = SSLUtils.sslContext(keyManagers, trustManagers, config.isTrustCerts());
                    httpClientBuilder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
                } catch (GeneralSecurityException e) {
                    throw new AssertionError(); // The system has no TLS. Just give up.
                }
            } else {
                SSLContext context = SSLContext.getInstance("TLSv1.2");
                context.init(keyManagers, trustManagers, null);
                httpClientBuilder.sslSocketFactory(context.getSocketFactory(), (X509TrustManager) trustManagers[0]);
            }

            httpClientBuilder.addInterceptor(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request request = chain.request();
                    if (Utils.isNotNullOrEmpty(config.getUsername()) && Utils.isNotNullOrEmpty(config.getPassword())) {
                        Request authReq = chain.request().newBuilder().addHeader("Authorization", Credentials.basic(config.getUsername(), config.getPassword())).build();
                        return chain.proceed(authReq);
                        //TODO 暂时先关闭
                    } else if (Utils.isNotNullOrEmpty(config.getOauthToken())) {
                        Request authReq = chain.request().newBuilder().addHeader("Authorization", "Bearer " + config.getOauthToken()).build();
                        return chain.proceed(authReq);

                    }
                    return chain.proceed(request);
                }
            });

            if (config.getConnectionTimeout() > 0) {
                httpClientBuilder.connectTimeout(config.getConnectionTimeout(), TimeUnit.MILLISECONDS);
            }

            if (config.getRequestTimeout() > 0) {
                httpClientBuilder.readTimeout(config.getRequestTimeout(), TimeUnit.MILLISECONDS);
            }

            if (config.getWebsocketPingInterval() > 0) {
                httpClientBuilder.pingInterval(config.getWebsocketPingInterval(), TimeUnit.MILLISECONDS);
            }

            if (config.getMaxConcurrentRequestsPerHost() > 0) {
                Dispatcher dispatcher = new Dispatcher();
                dispatcher.setMaxRequestsPerHost(config.getMaxConcurrentRequestsPerHost());
                httpClientBuilder.dispatcher(dispatcher);
            }

            if (config.getMaxConnection() > 0) {
                ConnectionPool connectionPool = new ConnectionPool(config.getMaxConnection(), 60, TimeUnit.SECONDS);
                httpClientBuilder.connectionPool(connectionPool);
            }

            // Only check proxy if it's a full URL with protocol
            if (config.getMasterUrl().toLowerCase().startsWith(HttpClientConfig.HTTP_PROTOCOL_PREFIX) || config.getMasterUrl().startsWith(HttpClientConfig.HTTPS_PROTOCOL_PREFIX)) {
                try {
                    URL proxyUrl = getProxyUrl(config);
                    if (proxyUrl != null) {
                        httpClientBuilder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUrl.getHost(), proxyUrl.getPort())));

                        if (config.getProxyUsername() != null) {
                            httpClientBuilder.proxyAuthenticator(new Authenticator() {
                                @Override
                                public Request authenticate(Route route, Response response) throws IOException {

                                    String credential = Credentials.basic(config.getProxyUsername(), config.getProxyPassword());
                                    return response.request().newBuilder().header("Proxy-Authorization", credential).build();
                                }
                            });
                        }
                    }


                } catch (MalformedURLException e) {
                    throw new RuntimeException("Invalid proxy server configuration", e);
                }
            }

            if (config.getUserAgent() != null && !config.getUserAgent().isEmpty()) {
                httpClientBuilder.addNetworkInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request agent = chain.request().newBuilder().header("User-Agent", config.getUserAgent()).build();
                        return chain.proceed(agent);
                    }
                });
            }

            if (config.getTlsVersions() != null && config.getTlsVersions().length > 0) {
                ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(config.getTlsVersions())
                        .build();
                httpClientBuilder.connectionSpecs(Arrays.asList(spec, CLEARTEXT));
            }
            return httpClientBuilder.build();
        } catch (Exception e) {
            throw new RuntimeException("创建OKHTTPClient错误", e);
        }
    }

    private static URL getProxyUrl(HttpClientConfig config) throws MalformedURLException {
        URL master = new URL(config.getMasterUrl());
        String host = master.getHost();
        if (config.getNoProxy() != null) {
            for (String noProxy : config.getNoProxy()) {
                if (host.endsWith(noProxy)) {
                    return null;
                }
            }
        }
        String proxy = config.getHttpsProxy();
        if (master.getProtocol().equals("http")) {
            proxy = config.getHttpProxy();
        }
        if (proxy != null) {
            return new URL(proxy);
        }
        return null;
    }






    public static Headers doLoginRequest(HttpClientConfig config, Map<String, String> params) {
        Response response = null;
        try {
            OkHttpClient client = getInstance(config);
            FormBody.Builder builder = new FormBody.Builder();
            for (Map.Entry<String, String> param : params.entrySet()) {
                builder.add(param.getKey(), param.getValue());
            }
            FormBody formBody = builder.build();
            Request request = new Request.Builder().url(config.getMasterUrl()).post(formBody).build();
            response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                return response.headers();
            } else if (response.body() != null){
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (response != null){
                response.close();
            }
        }
        return null;
    }


    public static String doFormRequest(HttpClientConfig config, Map<String, String> params, String cookie) {
        Response response = null;
        try {
            OkHttpClient client = getInstance(config);
            FormBody.Builder builder = new FormBody.Builder();
            for (Map.Entry<String, String> param : params.entrySet()) {
                builder.add(param.getKey(), param.getValue());
            }
            FormBody formBody = builder.build();
            Request request = new Request.Builder().url(config.getMasterUrl()).header("Cookie", cookie).post(formBody).build();
            response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else if (response.body() != null){
                return response.body().string();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (response != null){
                response.close();
            }
        }
        return null;
    }

    public static String doFormRequest(HttpClientConfig config, Map<String, String> params) {
        Response response = null;
        try {
            OkHttpClient client = getInstance(config);
            FormBody.Builder builder = new FormBody.Builder();
            for (Map.Entry<String, String> param : params.entrySet()) {
                builder.add(param.getKey(), param.getValue());
            }
            FormBody formBody = builder.build();
            Request request = new Request.Builder().url(config.getMasterUrl()).post(formBody).build();
            response = client.newCall(request).execute();
            if (response.isSuccessful()){
                if (response.body() != null){
                    return response.body().string();
                }
            }else {
                if (response.body() != null){
                    logger.error(response.body().string());
                }
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (response != null){
                response.close();
            }
        }
        return null;
    }




    public static String doFormRequest(HttpClientConfig clientConfig) {

        try {
            OkHttpClient client = getInstance(clientConfig);
            //创建请求

            Request.Builder builder = new Request.Builder();

            Request request = builder.get().url(clientConfig.getMasterUrl()).build();

            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else if (response.body() != null) {
                return response.body().string();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;

    }

    public static String doPost(HttpClientConfig httpClientConfig, String param, String jesessionId){
        Response response = null;
        try {
            OkHttpClient client = getInstance(httpClientConfig);
            RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), param);
            Request.Builder builder = new Request.Builder();
            Request request = builder.url(httpClientConfig.getMasterUrl()).header("Authorization", jesessionId).post(requestBody).build();
            response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                if (ObjectUtil.isNotNull(response.body())){
                    return  response.body().string();
                }
            } else {
                if (ObjectUtil.isNotNull(response.body())){
                    logger.error(response.body().string());
                }
                return null;
            }
        } catch (IOException e){
            e.printStackTrace();
        } finally {
            if (response != null){
                response.close();
            }
        }
        return null;
    }

    public static String doGet(HttpClientConfig config,  String headData) {
        Response response = null;
        try {
            OkHttpClient client = getInstance(config);
            Request request = new Request.Builder().url(config.getMasterUrl()).header("Authorization", headData).build();
            response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            if (!response.isSuccessful()){
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (response != null){
                response.close();
            }
        }
        return null;
    }

    public static String doGet(HttpClientConfig config) {
        Response response = null;
        try {
            OkHttpClient client = getInstance(config);
            Request request = new Request.Builder().url(config.getMasterUrl()).build();
            response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            if (!response.isSuccessful()){
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (response != null){
                response.close();
            }
        }
        return null;
    }

    public static String doPost(HttpClientConfig httpClientConfig, String param){
        Response response = null;
        try {
            OkHttpClient client = getInstance(httpClientConfig);
            RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), param);
            Request.Builder builder = new Request.Builder();
            Request request = builder.url(httpClientConfig.getMasterUrl()).post(requestBody).build();
            response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                if (ObjectUtil.isNotNull(response.body())){
                    return  response.body().string();
                }
            } else {
                if (ObjectUtil.isNotNull(response.body())){
                    logger.error(response.body().string());
                }
                return null;
            }
        } catch (IOException e){
            e.printStackTrace();
        } finally {
            if (response != null){
                response.close();
            }
        }
        return null;
    }



}