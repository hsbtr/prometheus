package io.dataease.plugins.datasource.prometheus.engine.prometheus.datasource;


import io.dataease.plugins.datasource.prometheus.engine.prometheus.datasource.MetricsInfoData;

public class MetricsInfoResp {

    private String status;

    private MetricsInfoData data;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public MetricsInfoData getData() {
        return data;
    }

    public void setData(MetricsInfoData data) {
        this.data = data;
    }
}
