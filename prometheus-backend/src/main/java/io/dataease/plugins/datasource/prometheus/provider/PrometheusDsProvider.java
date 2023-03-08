package io.dataease.plugins.datasource.prometheus.provider;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import io.dataease.plugins.common.base.domain.Datasource;
import io.dataease.plugins.common.base.domain.DeDriver;
import io.dataease.plugins.common.base.mapper.DeDriverMapper;
import io.dataease.plugins.common.constants.DatasourceTypes;
import io.dataease.plugins.common.dto.datasource.TableDesc;
import io.dataease.plugins.common.dto.datasource.TableField;
import io.dataease.plugins.common.request.datasource.DatasourceRequest;
import io.dataease.plugins.datasource.entity.JdbcConfiguration;
import io.dataease.plugins.datasource.prometheus.engine.okhttp.HttpClientConfig;
import io.dataease.plugins.datasource.prometheus.engine.okhttp.HttpClientUtils;
import io.dataease.plugins.datasource.prometheus.engine.prometheus.datasource.MetricsInfoData;
import io.dataease.plugins.datasource.prometheus.engine.prometheus.datasource.MetricsInfoResp;
import io.dataease.plugins.datasource.prometheus.engine.prometheus.datasource.MetricsInfoResult;
import io.dataease.plugins.datasource.prometheus.engine.prometheus.datasource.MetricsResp;
import io.dataease.plugins.datasource.provider.DefaultJdbcProvider;
import io.dataease.plugins.datasource.provider.ExtendedJdbcClassLoader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;


@Component()
public class PrometheusDsProvider extends DefaultJdbcProvider {
    @Resource
    private DeDriverMapper deDriverMapper;

    @Override
    public String getType() {
        return "prometheus";
    }

    @Override
    public boolean isUseDatasourcePool() {
        return false;
    }

    /**
     * 连接数据源
     */
    @Override
    public Connection getConnection(DatasourceRequest datasourceRequest) throws Exception {
        PrometheusConfig prometheusConfig = new Gson().fromJson(datasourceRequest.getDatasource().getConfiguration(),
                PrometheusConfig.class);

        String defaultDriver = prometheusConfig.getDriver();
        String customDriver = prometheusConfig.getCustomDriver();

        String url = prometheusConfig.getJdbc();
        Properties props = new Properties();
        DeDriver deDriver = null;
        if (StringUtils.isNotEmpty(prometheusConfig.getAuthMethod()) && prometheusConfig.getAuthMethod().equalsIgnoreCase("kerberos")) {
            System.setProperty("java.security.krb5.conf", "/opt/dataease/conf/krb5.conf");
            ExtendedJdbcClassLoader classLoader;
            if (isDefaultClassLoader(customDriver)) {
                classLoader = extendedJdbcClassLoader;
            } else {
                deDriver = deDriverMapper.selectByPrimaryKey(customDriver);
                classLoader = getCustomJdbcClassLoader(deDriver);
            }
            Class<?> ConfigurationClass = classLoader.loadClass("org.apache.hadoop.conf.Configuration");
            Method set = ConfigurationClass.getMethod("set", String.class, String.class);
            Object obj = ConfigurationClass.newInstance();
            set.invoke(obj, "hadoop.security.authentication", "Kerberos");

            Class<?> UserGroupInformationClass = classLoader.loadClass("org.apache.hadoop.security" +
                    ".UserGroupInformation");
            Method setConfiguration = UserGroupInformationClass.getMethod("setConfiguration", ConfigurationClass);
            Method loginUserFromKeytab = UserGroupInformationClass.getMethod("loginUserFromKeytab", String.class,
                    String.class);
            setConfiguration.invoke(null, obj);
            loginUserFromKeytab.invoke(null, prometheusConfig.getUsername(),
                    "/opt/dataease/conf/" + prometheusConfig.getPassword());
        } else {
            if (StringUtils.isNotBlank(prometheusConfig.getUsername())) {
                props.setProperty("user", prometheusConfig.getUsername());
                if (StringUtils.isNotBlank(prometheusConfig.getPassword())) {
                    props.setProperty("password", prometheusConfig.getPassword());
                }
            }
        }

        Connection conn;
        String driverClassName;
        ExtendedJdbcClassLoader jdbcClassLoader;
        if (isDefaultClassLoader(customDriver)) {
            driverClassName = defaultDriver;
            jdbcClassLoader = extendedJdbcClassLoader;
        } else {
            if (deDriver == null) {
                deDriver = deDriverMapper.selectByPrimaryKey(customDriver);
            }
            driverClassName = deDriver.getDriverClass();
            jdbcClassLoader = getCustomJdbcClassLoader(deDriver);
        }

        Driver driverClass = (Driver) jdbcClassLoader.loadClass(driverClassName).newInstance();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(jdbcClassLoader);
            conn = driverClass.connect(url, props);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
        return conn;
    }

    /**
     * 获取表名称
     */
    @Override
    public List<TableDesc> getTables(DatasourceRequest datasourceRequest){
        List<TableDesc> tables = new ArrayList<>();

        if (ObjectUtil.isNull(datasourceRequest)){
            return tables;
        }

        Datasource datasource = datasourceRequest.getDatasource();
        if (ObjectUtil.isNull(datasource)){
            return tables;
        }

        String configuration = datasource.getConfiguration();
        if (StrUtil.isBlank(configuration)){
            return tables;
        }

        HttpClientConfig config = new HttpClientConfig();

        Map<String,Object> maps = (Map<String,Object>) JSON.parse(configuration);
        String headData = getConnectionParam(config, maps,"/api/v1/label/__name__/values");

        String result;
        if (StrUtil.isNotBlank(headData)){
            result = HttpClientUtils.doGet(config, headData);
        }else {
           result = HttpClientUtils.doGet(config);
        }

        if (StrUtil.isBlank(result)){
            return tables;
        }

        MetricsResp metricsResp = JSONUtil.toBean(result, MetricsResp.class);

        if (metricsResp.getStatus().equals("success") && CollUtil.isNotEmpty(metricsResp.getData())){
            tables = metricsResp.getData().stream().filter(Objects::nonNull).map(s -> {
                TableDesc desc = new TableDesc();
                desc.setName(s);
                return desc;
            }).collect(Collectors.toList());
        }

        return tables;
    }

    /**
     * 获取表字段信息
     */
    @Override
    public List<TableField> getTableFields(DatasourceRequest datasourceRequest){
        List<TableField> list = new LinkedList<>();

        if (ObjectUtil.isNull(datasourceRequest)){
            return list;
        }

        Datasource datasource = datasourceRequest.getDatasource();
        if (ObjectUtil.isNull(datasource)){
            return list;
        }

        String table = datasourceRequest.getTable();
        if (StrUtil.isBlank(table)){
            return list;
        }

        String configuration = datasource.getConfiguration();
        if (StrUtil.isBlank(configuration)){
            return list;
        }

        HttpClientConfig config = new HttpClientConfig();
        Map<String,Object> maps = (Map<String,Object>) JSON.parse(configuration);
        long time = System.currentTimeMillis() / 1000;
        String path = "/api/v1/query?query="+ table + "&time=" + time;
        String headData = getConnectionParam(config, maps,path);


        String result;
        if (StrUtil.isNotBlank(headData)){
            result = HttpClientUtils.doGet(config, headData);
        }else {
            result = HttpClientUtils.doGet(config);
        }

        if (StrUtil.isBlank(result)){
            return list;
        }

        MetricsInfoResp metricsInfoResp = JSONUtil.toBean(result, MetricsInfoResp.class);

        if (ObjectUtil.isNotNull(metricsInfoResp) && metricsInfoResp.getStatus().equals("success")){
            MetricsInfoData data = metricsInfoResp.getData();
            if (ObjectUtil.isNotNull(data)){
                if (CollUtil.isNotEmpty(data.getResult()) && data.getResult().size() >= 1){
                    MetricsInfoResult metricsInfoResult = data.getResult().get(0);
                    Map<String, Object> metric = metricsInfoResult.getMetric();
                    if (CollUtil.isNotEmpty(metric)){
                        metric.forEach((key,value) ->{
                            TableField tableField = new TableField();
                            tableField.setFieldName(key);
                            tableField.setRemarks(key);
                            if (value.getClass().equals(String.class)){
                                tableField.setFieldType("0");
                            }
                            if (value.getClass().equals(Integer.class)){
                                tableField.setFieldType("2");
                            }
                            if (value.getClass().equals(Float.class)){
                                tableField.setFieldType("3");
                            }
                            if (value.getClass().equals(Boolean.class)){
                                tableField.setFieldType("4");
                            }
                            list.add(tableField);
                        });
                    }
                }
            }
        }

        return list;
    }

    /**
     * 检验数据源状态
     */
    @Override
    public String checkStatus(DatasourceRequest datasourceRequest){

        if (ObjectUtil.isNull(datasourceRequest)){
            return "Error";
        }

        Datasource datasource = datasourceRequest.getDatasource();
        if (ObjectUtil.isNull(datasource)){
            return "Error";
        }

        String configuration = datasource.getConfiguration();
        if (StrUtil.isBlank(configuration)){
            return "Error";
        }

        HttpClientConfig config = new HttpClientConfig();

        Map<String,Object> maps = (Map<String,Object>) JSON.parse(configuration);
        String headData = getConnectionParam(config, maps,"/metrics");


        String resultData;
        if (StrUtil.isNotBlank(headData)){
            resultData = HttpClientUtils.doGet(config, headData);
        }else {
            resultData = HttpClientUtils.doGet(config);
        }

        if (StrUtil.isNotBlank(resultData)){
            return "Success";
        }else {
            return "Error";
        }
    }

    private String getConnectionParam(HttpClientConfig config, Map<String, Object> maps,String path) {
        String headData = null;
        if (CollUtil.isNotEmpty(maps) && maps.containsKey("host") && maps.containsKey("port")){
            String host = null;
            Integer port = null;
            String username = null;
            String password = null;
            if (maps.containsKey("host")){
                host = Convert.toStr(maps.get("host"));
            }

            if (maps.containsKey("port")){
                port = Convert.toInt(maps.get("port"));
            }

            if (maps.containsKey("username")){
                username = Convert.toStr(maps.get("username"));
            }

            if (maps.containsKey("password")){
                password = Convert.toStr(maps.get("password"));
            }

            if (StrUtil.isNotBlank(host) && ObjectUtil.isNotNull(port)){
                if (host.startsWith("http://")){
                    String url = host + ":" + port + path;
                    config.setMasterUrl(url);
                }else {
                    String url = "http://" + host + ":" + port + path;
                    config.setMasterUrl(url);
                }
            }

            if (StrUtil.isNotBlank(username) && StrUtil.isNotBlank(password)){
                String data = username+":"+password;
                String base64encodedString = Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
                headData = "Basic " + base64encodedString;
            }
        }
        return headData;
    }


    /**
     * 显示对应的表的 SQL 语句
     */
    @Override
    public String getTablesSql(DatasourceRequest datasourceRequest) throws Exception {
        PrometheusConfig kingbaseConfig = new Gson().fromJson(datasourceRequest.getDatasource().getConfiguration(),
                PrometheusConfig.class);
        if (StringUtils.isEmpty(kingbaseConfig.getSchema())) {
            throw new Exception("Database schema is empty.");
        }
        /*return "select a.table_name, b.comments from all_tables a, user_tab_comments b where a.table_name = b
        .table_name and owner=upper('OWNER') ".replaceAll("OWNER",
                kingbaseConfig.getSchema());*/
        return ("select table_name from all_tables where owner=upper('OWNER') ").replaceAll("OWNER",
                kingbaseConfig.getSchema());
    }

    /**
     * 获取所有的用户
     */
    @Override
    public String getSchemaSql(DatasourceRequest datasourceRequest) {
        return "select * from all_users";
    }

}
