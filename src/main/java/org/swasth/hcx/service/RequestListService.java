package org.swasth.hcx.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.swasth.hcx.utils.Constants;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RequestListService {

    @Autowired
    PostgresService postgresService;

    private static final Logger logger = LoggerFactory.getLogger(ProviderService.class);

    @Value("${postgres.table.provider-system}")
    private String providerSystem;

//    public ResponseEntity<Object> getRequestByMobile(Map<String, Object> requestBody) throws SQLException {
//        String mobile = (String) requestBody.getOrDefault("mobile", "");
//        logger.info("The request list for mobile {}", mobile);
//        String app = (String) requestBody.getOrDefault("app", "");
//        Map<String, List<Map<String, Object>>> groupedEntries = new HashMap<>();
//        String searchQuery = String.format("SELECT * FROM %s WHERE mobile = '%s' AND app = '%s' ORDER BY created_on DESC LIMIT 20", providerSystem, mobile, app);
//        try (ResultSet searchResultSet = postgresService.executeQuery(searchQuery)) {
//            while (!searchResultSet.isClosed() && searchResultSet.next()) {
//                String workflowId = searchResultSet.getString("workflow_id");
//                if (!groupedEntries.containsKey(workflowId)) {
//                    groupedEntries.put(workflowId, new ArrayList<>());
//                }
//                Map<String, Object> responseMap = getResponseMap(searchResultSet, workflowId);
//                groupedEntries.get(workflowId).add(responseMap);
//            }
//            Map<String, Object> resp = getEntries(groupedEntries);
//            return new ResponseEntity<>(resp, HttpStatus.OK);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
//        }
//    }

    public ResponseEntity<Object> getRequestBySenderCode(Map<String, Object> requestBody, String type) {
        String filterBy = (String) requestBody.getOrDefault(type, "");
        logger.info("The request list for {} : {}", type, filterBy);
        String app = (String) requestBody.getOrDefault("app", "");
        Map<String, List<Map<String, Object>>> groupedEntries = new HashMap<>();
        String searchQuery = String.format("SELECT * FROM %s WHERE %s = '%s' AND app = '%s' ORDER BY created_on DESC LIMIT 20", providerSystem, type, filterBy, app);
        try (ResultSet searchResultSet = postgresService.executeQuery(searchQuery)) {
            while (searchResultSet.next()) {
                String workflowId = searchResultSet.getString("workflow_id");
                if (!groupedEntries.containsKey(workflowId)) {
                    groupedEntries.put(workflowId, new ArrayList<>());
                }
                Map<String, Object> responseMap = getResponseMap(searchResultSet, workflowId);
                groupedEntries.get(workflowId).add(responseMap);
            }
            Map<String, Object> resp = getEntries(groupedEntries);
            return new ResponseEntity<>(resp, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Object> getRequestByWorkflowId(Map<String, Object> requestBody) throws SQLException {
        String workflowId = (String) requestBody.getOrDefault("workflow_id", "");
        String app = (String) requestBody.getOrDefault("app", "");
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Object> resp = new HashMap<>();
        String searchQuery = String.format("SELECT * FROM %s WHERE workflow_id = '%s' AND (action = 'claim' OR action = 'preauth') AND app = '%s' ORDER BY created_on ASC", providerSystem, workflowId, app);
        try (ResultSet searchResultSet = postgresService.executeQuery(searchQuery)) {
            while (!searchResultSet.isClosed() && searchResultSet.next()) {
                getDataByWorkflowId(entries, searchResultSet);
            }
            resp.put("entries", entries);
            return new ResponseEntity<>(resp, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace(); // Log the exception for debugging
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> getEntries(Map<String, List<Map<String, Object>>> groupedEntries) {
        Map<String, Object> resp = new HashMap<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String key : groupedEntries.keySet()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put(key, groupedEntries.get(key));
            entries.add(entry);
        }
        resp.put("entries", entries);
        resp.put("count", entries.size());
        return resp;
    }

    private Map<String, Object> getResponseMap(ResultSet searchResultSet, String workflowId) throws SQLException {
        Map<String, Object> responseMap = new HashMap<>();
        String actionType = searchResultSet.getString("action");
        if (actionType.equalsIgnoreCase("claim") || actionType.equalsIgnoreCase("preauth")) {
            responseMap.put("supportingDocuments", searchResultSet.getString("supporting_documents"));
            responseMap.put("billAmount", searchResultSet.getString("bill_amount"));
            responseMap.put("approvedAmount", searchResultSet.getString("approved_amount"));
            responseMap.put("otpStatus", searchResultSet.getString("otp_status"));
            responseMap.put("bankStatus", searchResultSet.getString("bank_status"));
            responseMap.put("accountNumber", searchResultSet.getString("account_number"));
            responseMap.put("ifscCode", searchResultSet.getString("ifsc_code"));
        }
        responseMap.put("type", actionType);
        responseMap.put("status", searchResultSet.getString("status"));
        responseMap.put("apiCallId", searchResultSet.getString("request_id"));
        responseMap.put("claimType", "OPD");
        responseMap.put("date", searchResultSet.getString("created_on"));
        responseMap.put("insurance_id", searchResultSet.getString("insurance_id"));
        responseMap.put("correlationId", searchResultSet.getString("correlation_id"));
        responseMap.put("sender_code", searchResultSet.getString("sender_code"));
        responseMap.put("recipient_code", searchResultSet.getString("recipient_code"));
        responseMap.put("workflow_id", workflowId);
        responseMap.put("mobile", searchResultSet.getString("mobile"));
        responseMap.put("patientName", searchResultSet.getString("patient_name"));
        return responseMap;
    }

    private void getDataByWorkflowId(List<Map<String, Object>> entries, ResultSet searchResultSet) throws SQLException {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("type", getType(searchResultSet.getString("action")));
        responseMap.put("status", searchResultSet.getString("status"));
        responseMap.put("apiCallId", searchResultSet.getString("request_id"));
        responseMap.put("claimType", "OPD");
        responseMap.put("date", searchResultSet.getString("created_on"));
        responseMap.put("correlationId", searchResultSet.getString("correlation_id"));
        responseMap.put("sender_code", searchResultSet.getString("sender_code"));
        responseMap.put("recipient_code", searchResultSet.getString("recipient_code"));
        responseMap.put("billAmount", searchResultSet.getString("bill_amount"));
        responseMap.put("supportingDocuments", searchResultSet.getString("supporting_documents"));
        responseMap.put("mobile", searchResultSet.getString("mobile"));
        responseMap.put("patientName", searchResultSet.getString("patient_name"));
        responseMap.put("approvedAmount", searchResultSet.getString("approved_amount"));
        responseMap.put("otpStatus", searchResultSet.getString("otp_status"));
        responseMap.put("bankStatus", searchResultSet.getString("bank_status"));
        responseMap.put("accountNumber", searchResultSet.getString("account_number"));
        responseMap.put("ifscCode", searchResultSet.getString("ifsc_code"));
        entries.add(responseMap);
    }

    private String getType(String action) {
        if (Constants.CLAIM.equalsIgnoreCase(action)) {
            return Constants.CLAIM;
        } else {
            return Constants.PRE_AUTH;
        }
    }
}
