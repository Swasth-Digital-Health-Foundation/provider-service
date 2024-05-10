package org.swasth.hcx.v1.controllers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.hcxprotocol.utils.Operations;
import kong.unirest.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.swasth.hcx.dto.Response;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.service.ProviderService;
import org.swasth.hcx.service.RequestListService;
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.utils.JSONUtils;
import org.swasth.hcx.v1.BaseController;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.swasth.hcx.utils.Constants.*;

@RestController
@RequestMapping(Constants.VERSION_PREFIX)
public class ProviderController extends BaseController {

    @Autowired
    private ProviderService providerService;

    @Autowired
    private RequestListService requestListService;

    @Value("${procedures.file-path}")
    private String proceduresFilePath;
    @Value("${phone.beneficiary-register}")
    private String beneficiaryRegisterContent;

    @PostMapping(COVERAGE_ELIGIBILITY_CHECK)
    public ResponseEntity<Object> createCoverageEligibility(@RequestHeader HttpHeaders headers, @RequestBody Map<String, Object> requestBody) throws Exception {
        return providerService.createCoverageEligibilityRequest(requestBody, Operations.COVERAGE_ELIGIBILITY_CHECK);
    }

    @PostMapping(CLAIM_SUBMIT)
    public ResponseEntity<Object> createClaimSubmit(@RequestBody Map<String, Object> requestBody) {
        return providerService.createClaimRequest(requestBody, Operations.CLAIM_SUBMIT, CLAIM);
    }

    @PostMapping(PRE_AUTH_SUBMIT)
    public ResponseEntity<Object> createPreAuthSubmit(@RequestBody Map<String, Object> requestBody) {
        return providerService.createClaimRequest(requestBody, Operations.PRE_AUTH_SUBMIT, PRE_AUTH);
    }

    @PostMapping(COVERAGE_ELIGIBILITY_ONCHECK)
    public ResponseEntity<Object> coverageEligibilityOnCheck(@RequestBody Map<String, Object> requestBody) {
        return processAndValidateRequest(requestBody, Constants.COVERAGE_ELIGIBILITY_CHECK, Constants.COVERAGE_ELIGIBILITY_ONCHECK);
    }

    @PostMapping(CLAIM_ONSUBMIT)
    public ResponseEntity<Object> claimOnSubmit(@RequestBody Map<String, Object> requestBody) {
        return processAndValidateRequest(requestBody, CLAIM_SUBMIT, CLAIM_ONSUBMIT);
    }

    @PostMapping(PRE_AUTH_ONSUBMIT)
    public ResponseEntity<Object> preAuthOnSubmit(@RequestBody Map<String, Object> requestBody) {
        return processAndValidateRequest(requestBody, PRE_AUTH_SUBMIT, PRE_AUTH_ONSUBMIT);
    }

    @PostMapping(COMMUNICATION_REQUEST)
    public ResponseEntity<Object> processCommunicationRequest(@RequestBody Map<String, Object> requestBody) {
        return processAndValidateRequest(requestBody, COMMUNICATION_REQUEST, COMMUNICATION);
    }

    @PostMapping(CREATE_COMMUNICATION_ON_REQUEST)
    public ResponseEntity<Object> createOnCommunication(@RequestBody Map<String, Object> requestBody) throws Exception {
        return providerService.createCommunicationOnRequest(requestBody);
    }

    @PostMapping(CHECK_COMMUNICATION_REQUEST)
    public ResponseEntity<Object> checkCommunicationRequest(@RequestBody Map<String, Object> requestBody) {
        Response response = new Response();
        try {
            if (requestBody.isEmpty() || !requestBody.containsKey("request_id")) {
                throw new ClientException("Request body is empty or request_id is missing");
            }
            Map<String, Object> communicationStatus = providerService.getCommunicationStatus(requestBody);
            response.setResult(communicationStatus);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return exceptionHandler(response, e);
        }
    }

    @PostMapping(REQUEST_LIST)
    public ResponseEntity<Object> requestList(@RequestBody Map<String, Object> requestBody) {
        try {
            if (requestBody.containsKey("mobile")) {
                return requestListService.getRequestByMobile(requestBody);
            } else if (requestBody.containsKey("workflow_id")) {
                return requestListService.getRequestByWorkflowId(requestBody);
            } else if (requestBody.containsKey("sender_code")) {
                return requestListService.getRequestBySenderCode(requestBody);
            } else {
                throw new ClientException("Please provide valid request body");
            }
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error: " + ex.getMessage());
        }
    }

    @PostMapping(UPLOAD_DOCUMENTS)
    public ResponseEntity<Object> uploadDocuments(@RequestParam("file") List<MultipartFile> files, @RequestParam("mobile") String mobile) {
        try {
            List<Map<String, Object>> responses = providerService.getDocumentUrls(files, mobile);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping(SEND_OTP)
    public ResponseEntity<Object> sendOTP(@RequestBody Map<String, Object> requestBody) {
        try {
            String mobile = (String) requestBody.get(MOBILE);
            providerService.sendOTP(mobile, beneficiaryRegisterContent);
            return ResponseEntity.ok(Map.of("message", "OTP sent successfully", "mobile", mobile));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping(VERIFY_OTP)
    public ResponseEntity<Object> verifyOTP(@RequestBody Map<String, Object> requestBody) {
        try {
            return providerService.verifyOTP(requestBody);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/procedures/list/{procedure_name}")
    public ResponseEntity<Object> getProcedures(@PathVariable("procedure_name") String procedureName) throws IOException {
        try (FileReader reader = new FileReader(proceduresFilePath)) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            JsonArray conceptArray = jsonObject.getAsJsonArray("concept");
            List<Map<String, Object>> matchedConcepts = new ArrayList<>();
            for (JsonElement conceptElement : conceptArray) {
                JsonObject conceptObject = conceptElement.getAsJsonObject();
                if (conceptObject.get("display").getAsString().toLowerCase().startsWith(procedureName.toLowerCase()) || conceptObject.get("display").getAsString().equalsIgnoreCase(procedureName)) {
                    matchedConcepts.add(JSONUtils.deserialize(conceptObject.toString(), Map.class));
                }
            }
            return ResponseEntity.ok(new Response(matchedConcepts));
        } catch (Exception e) {
            return exceptionHandler(new Response(), e);
        }
    }
}

