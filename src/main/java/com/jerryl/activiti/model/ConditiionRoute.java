package com.jerryl.activiti.model;

/**
 *
 * @author yuz
 * @date 2018/7/24
 */
public class ConditiionRoute {
    String value;
    Long flowId;

    public ConditiionRoute() {}

    public ConditiionRoute(String value, Long flowId) {
        this.value = value;
        this.flowId = flowId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }
}
