package org.swasth.hcx.v1.controllers;


import kong.unirest.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.service.PostgresService;
import org.swasth.hcx.utils.Constants;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.swasth.hcx.utils.Constants.CONSULTATION_ADD;

@RestController()
@RequestMapping(Constants.VERSION_PREFIX)
public class DocumentController {

    @Value("${postgres.table.consultation-info}")
    private String consultationInfoTable;

    @Autowired
    private PostgresService postgres;

    @PostMapping(CONSULTATION_ADD)
    public ResponseEntity<Object> addConsultationInfo(@RequestBody Map<String, Object> requestBody) throws ClientException {
        String workflowId = (String) requestBody.getOrDefault("workflow_id", "");
        if (!requestBody.containsKey("workflow_id") && workflowId.isEmpty()) {
            throw new ClientException("Work flow id cannot be empty");
        }
        List<String> supportingDocumentsUrls = (List<String>) requestBody.getOrDefault("supporting_documents_url", new ArrayList<>());
        String supportingDocuments = supportingDocumentsUrls.stream()
                .map(document -> "'" + document + "'")
                .collect(Collectors.joining(","));
        String insertQuery = String.format("INSERT INTO %s (workflow_id, treatment_type, " +
                        "service_type, symptoms, supporting_documents_url) VALUES ('%s', '%s', '%s', '%s', ARRAY[%s])",
                consultationInfoTable, workflowId,
                requestBody.getOrDefault("treatment_type", ""),
                requestBody.getOrDefault("service_type", ""),
                requestBody.getOrDefault("symptoms", ""),
                supportingDocuments);
        try {
            postgres.execute(insertQuery);
            Map<String, Object> responseMap = getResponse(workflowId, Constants.SUCCESSFUL);
            return ResponseEntity.ok(responseMap); // Return a 200 OK response
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> responseMap = getResponse(workflowId, Constants.FAILED);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseMap); // Return a 500 Internal Server Error response
        }
    }

    @GetMapping("/consultation/{workflow_id}")
    public ResponseEntity<Object> getConsultationInfo(@PathVariable("workflow_id") String workflowId) {
        try {
            Map<String, Object> consultationInfo = getConsultationInfoByWorkflowId(workflowId);
            if (consultationInfo != null) {
                return ResponseEntity.ok(consultationInfo);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.singletonMap("error", "Consultation info not found"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("error", "Unable to fetch the details" + e.getMessage()));
        }
    }

    public Map<String, Object> getConsultationInfoByWorkflowId(String workflowId) throws ClientException, SQLException {
        String searchQuery = String.format("SELECT * FROM %s WHERE workflow_id = '%s'", consultationInfoTable, workflowId);
        Map<String, Object> consultationInfo = new HashMap<>();
        try (ResultSet resultSet = postgres.executeQuery(searchQuery)) {
            if (resultSet.next()) {
                consultationInfo.put("treatment_type", resultSet.getString("treatment_type"));
                consultationInfo.put("service_type", resultSet.getString("service_type"));
                consultationInfo.put("symptoms", resultSet.getString("symptoms"));
                consultationInfo.put("supporting_documents_url", resultSet.getString("supporting_documents_url"));
                consultationInfo.put("workflow_id", workflowId);
            } else {
                throw new ClientException("The Record does not exist for workflow id: " + workflowId);
            }
        }
        return consultationInfo;
    }

    public Map<String, Object> getResponse(String workflowId, String status) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("status", status);
        responseMap.put("workflowId", workflowId);
        return responseMap;
    }

}
