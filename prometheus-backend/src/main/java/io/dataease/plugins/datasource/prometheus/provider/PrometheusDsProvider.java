package io.dataease.plugins.datasource.prometheus.provider;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.NumberUtil;
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
import io.dataease.plugins.common.exception.DataEaseException;
import io.dataease.plugins.common.request.datasource.DatasourceRequest;
import io.dataease.plugins.datasource.entity.JdbcConfiguration;
import io.dataease.plugins.datasource.prometheus.engine.okhttp.HttpClientConfig;
import io.dataease.plugins.datasource.prometheus.engine.okhttp.HttpClientUtils;
import io.dataease.plugins.datasource.provider.DefaultJdbcProvider;
import io.dataease.plugins.datasource.provider.ExtendedJdbcClassLoader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;


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
    public List<TableDesc> getTables(DatasourceRequest datasourceRequest) throws Exception {
        List<TableDesc> tables = new ArrayList<>();
        String queryStr = getTablesSql(datasourceRequest);
        JdbcConfiguration jdbcConfiguration =
                new Gson().fromJson(datasourceRequest.getDatasource().getConfiguration(), JdbcConfiguration.class);
        int queryTimeout = Math.max(jdbcConfiguration.getQueryTimeout(), 0);
        try (Connection con = getConnectionFromPool(datasourceRequest); Statement statement = getStatement(con,
                queryTimeout); ResultSet resultSet = statement.executeQuery(queryStr)) {
            while (resultSet.next()) {
                tables.add(getTableDesc(datasourceRequest, resultSet));
            }
        } catch (Exception e) {
            DataEaseException.throwException(e);
        }

        return tables;
    }

    /**
     * 获取表名称
     */
    private TableDesc getTableDesc(DatasourceRequest datasourceRequest, ResultSet resultSet) throws SQLException {
        TableDesc tableDesc = new TableDesc();
        tableDesc.setName(resultSet.getString(1));
        return tableDesc;
    }

    /**
     * 获取表字段信息
     */
    @Override
    public List<TableField> getTableFields(DatasourceRequest datasourceRequest) throws Exception {
        List<TableField> list = new LinkedList<>();
        try (Connection connection = getConnectionFromPool(datasourceRequest)) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            ResultSet resultSet = databaseMetaData.getColumns(null, null, datasourceRequest.getTable(), "%");
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME").toUpperCase();
                String database;
                database = resultSet.getString("TABLE_CAT");
                if (database != null) {
                    if (tableName.equals(datasourceRequest.getTable()) && database.equalsIgnoreCase(getDatabase(datasourceRequest))) {
                        TableField tableField = getTableFiled(resultSet, datasourceRequest);
                        list.add(tableField);
                    }
                } else {
                    if (tableName.equals(datasourceRequest.getTable())) {
                        TableField tableField = getTableFiled(resultSet, datasourceRequest);
                        list.add(tableField);
                    }
                }
            }
            resultSet.close();
        } catch (SQLException e) {
            DataEaseException.throwException(e);
        } catch (Exception e) {
            DataEaseException.throwException("Data source connection exception: " + e.getMessage());
        }
        return list;
    }

    /**
     * 获取数据源
     */
    private String getDatabase(DatasourceRequest datasourceRequest) {
        JdbcConfiguration jdbcConfiguration =
                new Gson().fromJson(datasourceRequest.getDatasource().getConfiguration(), JdbcConfiguration.class);
        return jdbcConfiguration.getDataBase();
    }

    /**
     * 获取表字段
     */
    private TableField getTableFiled(ResultSet resultSet, DatasourceRequest datasourceRequest) throws SQLException {
        TableField tableField = new TableField();
        String colName = resultSet.getString("COLUMN_NAME");
        tableField.setFieldName(colName);
        String remarks = resultSet.getString("REMARKS");
        if (remarks == null || remarks.equals("")) {
            remarks = colName;
        }
        tableField.setRemarks(remarks);
        String dbType = resultSet.getString("TYPE_NAME").toUpperCase();
        tableField.setFieldType(dbType);
        if (dbType.equalsIgnoreCase("LONG")) {
            tableField.setFieldSize(65533);
        }
        if (StringUtils.isNotEmpty(dbType) && dbType.toLowerCase().contains("date") && tableField.getFieldSize() < 50) {
            tableField.setFieldSize(50);
        }

        if (datasourceRequest.getDatasource().getType().equalsIgnoreCase(DatasourceTypes.hive.name()) && tableField.getFieldType().equalsIgnoreCase("BOOLEAN")) {
            tableField.setFieldSize(1);
        } else {
            String size = resultSet.getString("COLUMN_SIZE");
            if (size == null) {
                tableField.setFieldSize(1);
            } else {
                tableField.setFieldSize(Integer.valueOf(size));
            }
        }
        return tableField;
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
        String headData = getConnectionParam(config, maps);


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

    private String getConnectionParam(HttpClientConfig config, Map<String, Object> maps) {
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
                    String url = host + ":" + port + "/metrics";
                    config.setMasterUrl(url);
                }else {
                    String url = "http://" + host + ":" + port + "/metrics";
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
