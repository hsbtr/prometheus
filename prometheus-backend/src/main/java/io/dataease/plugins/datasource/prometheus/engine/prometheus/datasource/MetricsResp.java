package io.dataease.plugins.datasource.prometheus.engine.prometheus.datasource;

import java.util.List;

public class MetricsResp {
    private String status;
    private List<String> data;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getData() {
        return data;
    }

    public void setData(List<String> data) {
        this.data = data;
    }
}
