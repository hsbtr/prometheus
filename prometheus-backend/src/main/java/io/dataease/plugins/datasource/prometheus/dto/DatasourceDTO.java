package io.dataease.plugins.datasource.prometheus.dto;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import io.dataease.plugins.common.request.datasource.DatasourceRequest;
import io.dataease.plugins.datasource.prometheus.JsonUtil;
import lombok.Data;
import java.util.Map;


@Data
public class DatasourceDTO {

    private String host;
    private Integer port;
    private String username;
    private String password;
    private String table;

    public static DatasourceDTO convert(DatasourceRequest datasourceRequest){
        DatasourceDTO datasource = new DatasourceDTO();

        String configuration = datasourceRequest.getDatasource().getConfiguration();
        String table = datasourceRequest.getTable();
        if (StrUtil.isNotBlank(table)){
         datasource.setTable(table);
        }

        Map<String, Object> configMap = JsonUtil.jsonToMap3(configuration);
        if (CollUtil.isEmpty(configMap)){
            return datasource;
        }

        if (configMap.containsKey(ParamConst.HOST)){
            datasource.setHost(Convert.toStr(configMap.get("host")).trim());
        }
        if (configMap.containsKey(ParamConst.PORT)){
            datasource.setPort(Integer.valueOf(Convert.toStr(configMap.get("port")).trim()));
        }
        if (configMap.containsKey(ParamConst.USERNAME)){
            datasource.setUsername(Convert.toStr(configMap.get("username")).trim());
        }
        if (configMap.containsKey(ParamConst.PASSWORD)){
            datasource.setPassword(Convert.toStr(configMap.get("password")).trim());
        }
        return datasource;
    }
}
