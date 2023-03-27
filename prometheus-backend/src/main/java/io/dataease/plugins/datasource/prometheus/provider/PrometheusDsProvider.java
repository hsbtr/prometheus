package io.dataease.plugins.datasource.prometheus.provider;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import io.dataease.plugins.common.base.domain.Datasource;
import io.dataease.plugins.common.base.domain.DeDriver;
import io.dataease.plugins.common.base.mapper.DeDriverMapper;
import io.dataease.plugins.common.dto.datasource.TableDesc;
import io.dataease.plugins.common.dto.datasource.TableField;
import io.dataease.plugins.common.request.datasource.DatasourceRequest;
import io.dataease.plugins.datasource.prometheus.dto.DatasourceDTO;
import io.dataease.plugins.datasource.prometheus.engine.prometheus.PrometheusUtil;
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

        String error = checkDatasourceParam(datasourceRequest);
        if (StrUtil.isNotBlank(error)){
            return tables;
        }

        DatasourceDTO dto = DatasourceDTO.convert(datasourceRequest);
        if (ObjectUtil.isNull(dto)){
            return tables;
        }

        MetricsResp metricsResp = PrometheusUtil.getTables(dto);
        if (ObjectUtil.isNull(metricsResp)){
            return tables;
        }

        List<String> data = metricsResp.getData();
        if (CollUtil.isEmpty(data)){
            return tables;
        }

        tables = metricsResp.getData().stream().filter(Objects::nonNull).map(s -> {
            TableDesc desc = new TableDesc();
            desc.setName(s);
            return desc;
        }).collect(Collectors.toList());

        return tables;
    }

    /**
     * 获取表字段信息
     */
    @Override
    public List<TableField> getTableFields(DatasourceRequest datasourceRequest){
        List<TableField> list = new LinkedList<>();

        String error = checkDatasourceParam(datasourceRequest);
        if (StrUtil.isNotBlank(error)){
            return list;
        }

        DatasourceDTO dto = DatasourceDTO.convert(datasourceRequest);
        if (ObjectUtil.isNull(dto)){
            return list;
        }

        MetricsInfoResp metricsInfoResp = PrometheusUtil.getTableFields(dto);
        if (ObjectUtil.isNull(metricsInfoResp)){
            return list;
        }

        MetricsInfoData data = metricsInfoResp.getData();
        if (ObjectUtil.isNull(data)){
            return list;
        }
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

        return list;
    }

    /**
     * 检验数据源状态
     */
    @Override
    public String checkStatus(DatasourceRequest datasourceRequest){

        String error = checkDatasourceParam(datasourceRequest);
        if (StrUtil.isNotBlank(error)){
            return error;
        }

        DatasourceDTO dto = DatasourceDTO.convert(datasourceRequest);
        if (ObjectUtil.isNull(dto)){
            return "Error";
        }

        String queryResult = PrometheusUtil.checkStatus(dto);

        if (StrUtil.isBlank(queryResult)){
            return "Error";
        }

        return "Success";
    }

    private String checkDatasourceParam(DatasourceRequest datasourceRequest) {
        String error = null;
        if (ObjectUtil.isNull(datasourceRequest)){
            error =  "Error";
        }

        Datasource datasource = datasourceRequest.getDatasource();
        if (ObjectUtil.isNull(datasource)){
            error =  "Error";
        }

        String configuration = datasource.getConfiguration();
        if (StrUtil.isBlank(configuration)){
            error =  "Error";
        }

        return error;
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
