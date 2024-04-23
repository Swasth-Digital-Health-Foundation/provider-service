package org.swasth.hcx.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response {

    private long timestamp = System.currentTimeMillis();
    @JsonProperty("correlation_id")
    private String correlationId;
    @JsonProperty("api_call_id")
    private String apiCallId;
    @JsonProperty("subscription_id")
    private String subscriptionId;

    private String recipientCode;
    private String senderCode ;
    private ResponseError error;
    private Map<String, Object> result;

    public List<Map<String, Object>> getResultList() {
        return resultList;
    }

    public void setResultList(List<Map<String, Object>> resultList) {
        this.resultList = resultList;
    }

    private List<Map<String,Object>> resultList;

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    private String workflowId;
    public Response() {}

    public Response(String workflowId, String senderCode, String recipientCode) {
        this.workflowId = workflowId;
        this.senderCode = senderCode;
        this.recipientCode = recipientCode;
    }

    public Response(String key, Object val) {
        this.result = new HashMap<>();
        this.put(key, val);
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getApiCallId() {
        return apiCallId;
    }

    public void setApiCallId(String apiCallId) {
        this.apiCallId = apiCallId;
    }

    public ResponseError getError() {
        return error;
    }

    public void setError(ResponseError error) {
        this.error = error;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getSubscriptionId() {return  subscriptionId;}

    public Object get(String key) {
        return result.get(key);
    }

    public Response(long timestamp){
        this.timestamp = timestamp;
    }

    public Response put(String key, Object vo) {
        result.put(key, vo);
        return this;
    }

    public Response(Map<String, Object> result) {
        this.timestamp = System.currentTimeMillis();
        this.result = result;
    }

    public Response(List<Map<String, Object>> result) {
        this.timestamp = System.currentTimeMillis();
        this.resultList = result;
    }

}

