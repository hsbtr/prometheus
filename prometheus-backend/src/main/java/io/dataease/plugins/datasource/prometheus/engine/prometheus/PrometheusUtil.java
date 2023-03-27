package io.dataease.plugins.datasource.prometheus.engine.prometheus;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import io.dataease.plugins.datasource.prometheus.dto.DatasourceDTO;
import io.dataease.plugins.datasource.prometheus.engine.okhttp.HttpClientConfig;
import io.dataease.plugins.datasource.prometheus.engine.okhttp.HttpClientUtils;
import io.dataease.plugins.datasource.prometheus.engine.prometheus.datasource.MetricsInfoResp;
import io.dataease.plugins.datasource.prometheus.engine.prometheus.datasource.MetricsResp;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PrometheusUtil {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusUtil.class);

    private static final String prefix = "http://";
    //校验是否可以连接成功
    private static final String metrics = "/metrics";
    //查询所有表情
    private static final String tableLabel = "/api/v1/label/__name__/values";
    //瞬时查询
    private static final String instant = "/api/v1/query?query=";

    /**
     * 检验数据源状态
     */
    public static String checkStatus(DatasourceDTO dto) {
        String queryResult = null;

        String url = prefix + dto.getHost() + ":" + dto.getPort() + metrics;
        String baseStr = dto.getUsername()+":"+dto.getPassword();
        String token = "Basic " + Base64.getEncoder().encodeToString(baseStr.getBytes(StandardCharsets.UTF_8));

        HttpClientConfig config = new HttpClientConfig();
        config.setMasterUrl(url);
        config.setRequestTimeout(300000);
        config.setConnectionTimeout(300000);
        OkHttpClient okHttpClient = HttpClientUtils.getInstance(config);

        Request request;
        if (StrUtil.isBlank(token)){
            request = new Request.Builder().url(url).build();
        }else {
            request = new Request.Builder().url(url).addHeader("Authorization", token).build();
        }

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.code() != HttpStatus.OK.value()) {
                logger.error("prometheus 校验连接 状态码异常：{},url:{}", response.code(), url);
            } else {
                ResponseBody body = response.body();
                if (ObjectUtil.isNotNull(body)) {
                    queryResult = body.string();
                }
            }
        } catch (Exception e) {
            logger.error("prometheus 校验连接 出现错误：{},url:{}", e, url);
        }
        return queryResult;
    }

    /**
     * 获取表名称
     */
    public static MetricsResp getTables(DatasourceDTO dto) {
        MetricsResp metricsResp = null;

        String url = prefix + dto.getHost() + ":" + dto.getPort() + tableLabel;
        String baseStr = dto.getUsername()+":"+dto.getPassword();
        String token = "Basic " + Base64.getEncoder().encodeToString(baseStr.getBytes(StandardCharsets.UTF_8));

        HttpClientConfig config = new HttpClientConfig();
        config.setMasterUrl(url);
        config.setRequestTimeout(300000);
        config.setConnectionTimeout(300000);
        OkHttpClient okHttpClient = HttpClientUtils.getInstance(config);

        Request request;
        if (StrUtil.isBlank(token)){
            request = new Request.Builder().url(url).build();
        }else {
            request = new Request.Builder().url(url).addHeader("Authorization", token).build();
        }

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.code() != HttpStatus.OK.value()) {
                logger.error("prometheus 获取表名称 状态码异常：{},url:{}", response.code(), url);
            } else {
                ResponseBody body = response.body();
                if (ObjectUtil.isNotNull(body)) {
                    String result = body.string();
                    if (StringUtils.isNotBlank(result)) {
                        metricsResp = JSONUtil.toBean(result, MetricsResp.class);
                        if (metricsResp != null) {
                            String status = metricsResp.getStatus();
                            if ("success".equals(status)) {
                                return metricsResp;
                            } else {
                                logger.error("prometheus 获取表名称 状态不正确：{},url:{}", metricsResp, url);
                            }
                        }

                    }
                }
            }
        } catch (Exception e) {
            logger.error("prometheus 获取表名称 出现错误：{},url:{}", e, url);
        }
        return metricsResp;
    }

    /**
     * 获取表字段信息
     */
    public static MetricsInfoResp getTableFields(DatasourceDTO dto) {
        MetricsInfoResp metricsInfoResp = null;

        long time = System.currentTimeMillis() / 1000;
        String url = prefix + dto.getHost() + ":" + dto.getPort() + instant + dto.getTable() + "&time=" + time;
        String baseStr = dto.getUsername()+":"+dto.getPassword();
        String token = "Basic " + Base64.getEncoder().encodeToString(baseStr.getBytes(StandardCharsets.UTF_8));

        HttpClientConfig config = new HttpClientConfig();
        config.setMasterUrl(url);
        config.setRequestTimeout(300000);
        config.setConnectionTimeout(300000);
        OkHttpClient okHttpClient = HttpClientUtils.getInstance(config);

        Request request;
        if (StrUtil.isBlank(token)){
            request = new Request.Builder().url(url).build();
        }else {
            request = new Request.Builder().url(url).addHeader("Authorization", token).build();
        }

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.code() != HttpStatus.OK.value()) {
                logger.error("prometheus 获取表字段信息 状态码异常：{},url:{}", response.code(), url);
            } else {
                ResponseBody body = response.body();
                if (ObjectUtil.isNotNull(body)) {
                    String result = body.string();
                    if (StringUtils.isNotBlank(result)) {
                        metricsInfoResp = JSONUtil.toBean(result, MetricsInfoResp.class);
                        if (metricsInfoResp != null) {
                            String status = metricsInfoResp.getStatus();
                            if ("success".equals(status)) {
                                return metricsInfoResp;
                            } else {
                                logger.error("prometheus 获取表字段信息 状态不正确：{},url:{}", metricsInfoResp, url);
                            }
                        }

                    }
                }
            }
        } catch (Exception e) {
            logger.error("prometheus 获取表字段信息 出现错误：{},url:{}", e, url);
        }
        return metricsInfoResp;
    }
}
