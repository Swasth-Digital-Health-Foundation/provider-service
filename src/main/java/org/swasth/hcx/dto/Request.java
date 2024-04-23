package org.swasth.hcx.dto;

import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.exception.ErrorCodes;
import org.swasth.hcx.utils.JSONUtils;

import java.util.HashMap;
import java.util.Map;

import static org.swasth.hcx.utils.Constants.*;

public class Request {

    private  Map<String, Object> payload;
    protected Map<String, Object> hcxHeaders = null;
    private Map<String,Object> requestBody;
    private String notificationRequest;
    private String action;

    public Request(Map<String, Object> body, String action) throws Exception {
        this.payload = body;
        this.action = action;
        if (NOTIFICATION_NOTIFY.equals(action)) {
            notificationRequest = (String) ((Map<String, Object>) body.get(PAYLOAD)).get(PAYLOAD);
            System.out.println("Encoded payload ----------" + JSONUtils.serialize(notificationRequest));
            hcxHeaders = JSONUtils.decodeBase64String(notificationRequest.split("\\.")[0], Map.class);
            payload = JSONUtils.decodeBase64String(notificationRequest.split("\\.")[1], Map.class);
        } else {
            try {
                if (body.containsKey(PAYLOAD)) {
                    hcxHeaders = JSONUtils.decodeBase64String(((String) body.get(PAYLOAD)).split("\\.")[0], Map.class);
                } else if (body.containsKey(STATUS))
                    hcxHeaders = body;
            } catch (Exception e) {
                throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD, "Invalid Payload");
            }
        }
    }

    public String getNotificationRequest() {
        return notificationRequest;
    }


    public Request(Map<String, Object> requestBody) throws Exception {
        this.requestBody = requestBody;
        if (requestBody.containsKey(PAYLOAD)) {
            hcxHeaders = JSONUtils.decodeBase64String(((String) requestBody.get(PAYLOAD)).split("\\.")[0], Map.class);
            payload = JSONUtils.decodeBase64String(((String) requestBody.get(PAYLOAD)).split("\\.")[1], Map.class);
        }
    }

    public Map<String, Object> getNotificationHeaders() {
        return (Map<String, Object>) hcxHeaders.getOrDefault("x-hcx-notification_headers",new HashMap<>());
    }
    public String getTopicCode(){
        return (String) payload.get("topic_code");
    }

    // TODO remove this method. We should restrict accessing it to have a clean code.
    public Map<String, Object> getPayload() {
        return payload;
    }

    public String getWorkflowId() {
        return getHeader(WORKFLOW_ID);
    }

    public String getApiCallId() {
        return getHeader(API_CALL_ID);
    }

    public String getCorrelationId() {
        return getHeader(CORRELATION_ID);
    }

    public String getSenderCode() {
        return getHeader(SENDER_CODE);
    }

    public String getRecipientCode() {
        return getHeader(RECIPIENT_CODE);
    }


    public String getDebugFlag() {
        return getHeader(DEBUG_FLAG);
    }

    public String getStatus() {
        return getHeader(STATUS);
    }

    public Map<String, Object> getHcxHeaders() {
        return hcxHeaders;
    }

    protected String getHeader(String key) {
        return (String) hcxHeaders.getOrDefault(key, null);
    }

    protected Map<String, Object> getHeaderMap(String key) {
        return (Map<String, Object>) hcxHeaders.getOrDefault(key, null);
    }

    private Map<String, Object> getErrorDetails() {
        return getHeaderMap(ERROR_DETAILS);
    }

    private Map<String, Object> getDebugDetails() {
        return getHeaderMap(DEBUG_DETAILS);
    }

    public String getSubscriptionId() { return (String) payload.get(SUBSCRIPTION_ID); }

    public String getAction() { return this.action; }
}

