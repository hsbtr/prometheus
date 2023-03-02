package io.dataease.plugins.datasource.prometheus.engine.prometheus.datasource;


import java.util.List;

public class MetricsInfoData {

    List<MetricsInfoResult> result;
    private String resultType;

    public List<MetricsInfoResult> getResult() {
        return result;
    }

    public void setResult(List<MetricsInfoResult> result) {
        this.result = result;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }
}
