package org.swasth.hcx.v1;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.amazonaws.services.dynamodbv2.xspec.S;
import io.hcxprotocol.init.HCXIntegrator;
import io.hcxprotocol.utils.Operations;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.swasth.hcx.dto.Request;
import org.swasth.hcx.dto.Response;
import org.swasth.hcx.dto.ResponseError;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.exception.ErrorCodes;
import org.swasth.hcx.exception.ServerException;
import org.swasth.hcx.exception.ServiceUnavailbleException;
import org.swasth.hcx.fhirexamples.OnActionFhirExamples;
import org.swasth.hcx.service.HcxIntegratorService;
import org.swasth.hcx.service.PostgresService;
import org.swasth.hcx.service.ProviderService;
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.utils.JSONUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.swasth.hcx.utils.Constants.*;

public class BaseController {

    @Value("${postgres.table.provider-system}")
    private String providerServiceTable;
    @Autowired
    protected HcxIntegratorService hcxIntegratorService;
    @Autowired
    private PostgresService postgres;
    @Autowired
    protected Environment env;
    IParser parser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

    private static final Logger logger = LoggerFactory.getLogger(ProviderService.class);

    protected void processAndValidateRequest(String onApiAction, Map<String, Object> requestBody, String apiAction) throws Exception {
        String mid = UUID.randomUUID().toString();
        String serviceMode = env.getProperty(SERVICE_MODE);
        System.out.println("\n" + "Mode: " + serviceMode + " :: mid: " + mid + " :: Event: " + onApiAction);
        if (StringUtils.equalsIgnoreCase(serviceMode, GATEWAY)) {
            Map<String, String> pay = new HashMap<>();
            System.out.println("payload received " + requestBody);
            pay.put("payload", String.valueOf(requestBody.get("payload")));
            Map<String, Object> output = new HashMap<>();
            System.out.println("create the on check payload");
            Bundle bundle = new Bundle();
            Request req = new Request(requestBody, apiAction);
            HCXIntegrator hcxIntegrator = hcxIntegratorService.getHCXIntegrator(req.getRecipientCode());
            if (COVERAGE_ELIGIBILITY_ONCHECK.equalsIgnoreCase(onApiAction)) {
                processCoverageEligibility(pay, output, req, hcxIntegrator);
            } else if (CLAIM_ONSUBMIT.equalsIgnoreCase(onApiAction)) {
                processPreAuthAndClaim(pay, output, req, hcxIntegrator, Operations.CLAIM_ON_SUBMIT);
            } else if (PRE_AUTH_ONSUBMIT.equalsIgnoreCase(onApiAction)) {
                processPreAuthAndClaim(pay, output, req, hcxIntegrator, Operations.PRE_AUTH_ON_SUBMIT);
            } else if (COMMUNICATION_REQUEST.equalsIgnoreCase(apiAction)) {
                processCommunication(pay, output, req, hcxIntegrator);
            }
        }
    }

    private void processCommunication(Map<String, String> pay, Map<String, Object> output, Request req, HCXIntegrator hcxIntegrator) throws Exception {
        boolean result = hcxIntegrator.processIncoming(JSONUtils.serialize(pay), Operations.COMMUNICATION_REQUEST, output);
        if (!result) {
            logger.error("Error while processing incoming request: " + output);
            throw new ClientException("Exception while decrypting communication incoming request :" + req.getCorrelationId());
        }
        logger.info("output map after decryption communication" + output);
        String fhirPayload = (String) output.getOrDefault("fhirPayload", "");
        CommunicationRequest cr = parser.parseResource(CommunicationRequest.class, fhirPayload);
        String communicationType = cr.getPayload().get(0).getId();
        if (communicationType == null || communicationType.equalsIgnoreCase("otp_verification")) {
            updateBasedOnType("otp_status", req.getCorrelationId());
        } else if (communicationType.equalsIgnoreCase("bank_verification")) {
            updateBasedOnType("bank_status", req.getCorrelationId());
        } else if (communicationType.equalsIgnoreCase("otp_response")) {
            String status = String.valueOf(cr.getPayload().get(0).getContent());
            System.out.println("the status will be ----" + status);
            String update = String.format("UPDATE %s SET otp_status = '%s' WHERE action = 'claim' AND correlation_id ='%s'", providerServiceTable, status, req.getCorrelationId());
            postgres.execute(update);
        }
        insertRecords(req.getApiCallId(), req.getSenderCode(), req.getRecipientCode(), (String) req.getPayload().getOrDefault(Constants.PAYLOAD, ""), (String) output.get("fhirPayload"), req.getWorkflowId(), req.getCorrelationId());
        logger.info("communication request updated for correlation id {} :", req.getCorrelationId());
    }


    private void processPreAuthAndClaim(Map<String, String> pay, Map<String, Object> output, Request req, HCXIntegrator hcxIntegrator, Operations operations) throws Exception {
        boolean result = hcxIntegrator.processIncoming(JSONUtils.serialize(pay), operations, output);
        if (!result) {
            logger.error("Error while processing incoming request: {} ", output);
            throw new ClientException("Exception while decrypting claim incoming request :" + req.getCorrelationId());
        }
        String decryptedFhirPayload = (String) output.get("fhirPayload");
        logger.info("Output map after decrypting claim request : {} ", decryptedFhirPayload);
        String approvedAmount = getAmount(decryptedFhirPayload);
        String remarks = getRemarks(decryptedFhirPayload);
        logger.info("Output map after decrypting claim request : {} ", decryptedFhirPayload);
        updateTheIncomingRequest(req, approvedAmount , remarks);
    }

    private void processCoverageEligibility(Map<String, String> pay, Map<String, Object> output, Request req, HCXIntegrator hcxIntegrator) throws Exception {
        Bundle bundle;
        boolean result = hcxIntegrator.processIncoming(JSONUtils.serialize(pay), Operations.COVERAGE_ELIGIBILITY_ON_CHECK, output);
        if (!result) {
            logger.error("Error while processing incoming request: {} ", output);
            throw new ClientException("Exception while processing incoming request :" + req.getCorrelationId());
        }
        String decryptedFhirPayload = (String) output.get("fhirPayload");
        logger.info("output map after decryption coverageEligibility : {} ", decryptedFhirPayload);
        //processing the decrypted incoming bundle
        bundle = parser.parseResource(Bundle.class, decryptedFhirPayload);
        CoverageEligibilityResponse covRes = OnActionFhirExamples.coverageEligibilityResponseExample();
        covRes.setPatient(new Reference("Patient/RVH1003"));
        replaceResourceInBundleEntry(bundle, "https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-CoverageEligibilityResponseBundle.html", CoverageEligibilityRequest.class, new Bundle.BundleEntryComponent().setFullUrl(covRes.getResourceType() + "/" + covRes.getId().toString().replace("#", "")).setResource(covRes));
        System.out.println("bundle reply " + parser.encodeResourceToString(bundle));
        // check for the request exist if exist then update
        updateTheIncomingRequest(req, "", "");
    }

    private void updateBasedOnType(String type, String correlationId) throws ClientException {
        String update = String.format("UPDATE %s SET %s = '%s' WHERE  action = 'claim' AND correlation_id ='%s'", providerServiceTable, type, "initiated", correlationId);
        postgres.execute(update);
    }

    private void updateTheIncomingRequest(Request req, String approvedAmount, String remarks) throws ClientException, SQLException {
        try {
            String getByCorrelationId = String.format("SELECT * FROM %s WHERE correlation_id='%s'", providerServiceTable, req.getCorrelationId());
            ResultSet resultSet = postgres.executeQuery(getByCorrelationId);
            if (!resultSet.next()) {
                throw new ClientException("The corresponding request does not exist in the database");
            }
            String updateStatus = String.format("UPDATE %s SET status = '%s', approved_amount = '%s', remarks = '%s', updated_on=%d  WHERE correlation_id = '%s'", providerServiceTable, req.getStatus(), approvedAmount, remarks, System.currentTimeMillis(), req.getCorrelationId());
            postgres.execute(updateStatus);
        } catch (Exception e) {
            throw new ClientException("Error while updating the record  : " + e.getMessage());
        }
    }


    protected void replaceResourceInBundleEntry(Bundle bundle, String bundleURL, Class matchClass, Bundle.BundleEntryComponent bundleEntry) {
        //updating the meta
        Meta meta = new Meta();
        meta.getProfile().add(new CanonicalType(bundleURL));
        meta.setLastUpdated(new Date());
        bundle.setMeta(meta);
        for (int i = 0; i < bundle.getEntry().size(); i++) {
            System.out.println("in the loop " + i);
            Bundle.BundleEntryComponent par = bundle.getEntry().get(i);
            DomainResource dm = (DomainResource) par.getResource();
            if (dm.getClass() == matchClass) {
                bundle.getEntry().set(i, bundleEntry);
            }
        }
    }


    protected void setResponseParams(Request request, Response response) {
        response.setCorrelationId(request.getCorrelationId());
        response.setApiCallId(request.getApiCallId());
    }

    protected ResponseEntity<Object> exceptionHandler(Response response, Exception e) {
        e.printStackTrace();
        if (e instanceof ClientException) {
            return new ResponseEntity<>(errorResponse(response, ((ClientException) e).getErrCode(), e), HttpStatus.BAD_REQUEST);
        } else if (e instanceof ServiceUnavailbleException) {
            return new ResponseEntity<>(errorResponse(response, ((ServiceUnavailbleException) e).getErrCode(), e), HttpStatus.SERVICE_UNAVAILABLE);
        } else if (e instanceof ServerException) {
            return new ResponseEntity<>(errorResponse(response, ((ServerException) e).getErrCode(), e), HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            return new ResponseEntity<>(errorResponse(response, ErrorCodes.INTERNAL_SERVER_ERROR, e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    protected Response errorResponse(Response response, ErrorCodes code, java.lang.Exception e) {
        ResponseError error = new ResponseError(code, e.getMessage(), e.getCause());
        response.setError(error);
        return response;
    }

    public ResponseEntity<Object> processAndValidateRequest(Map<String, Object> requestBody, String apiAction, String onApiAction) {
        Response response = new Response();
        try {
            Request request = new Request(requestBody, apiAction);
            setResponseParams(request, response);
            processAndValidateRequest(onApiAction, requestBody, apiAction);
            System.out.println("http respond sent");
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error   " + e);
            return exceptionHandler(response, e);
        }
    }

    public String getAmount(String fhirPayload) {
        String amount = "0";
        ClaimResponse cr = getResourceByType("ClaimResponse", ClaimResponse.class, fhirPayload);
        if (cr != null && cr.getTotal() != null && cr.getTotal().get(0) != null) {
            amount = String.valueOf(cr.getTotal().get(0).getAmount().getValue());
        }
        return amount;
    }

    public String getRemarks(String fhirPayload) {
        String remarks = "";
        ClaimResponse cr = getResourceByType("ClaimResponse", ClaimResponse.class, fhirPayload);
        if (cr != null && cr.getProcessNote() != null) {
            for (ClaimResponse.NoteComponent message : cr.getProcessNote()) {
                remarks = message.getText();
            }
        }
        return remarks;
    }

    public <T extends Resource> T getResourceByType(String type, Class<T> resourceClass, String fhirPayload) {
        Bundle parsed = parser.parseResource(Bundle.class, fhirPayload);
        return parsed.getEntry().stream()
                .filter(entry -> StringUtils.equalsIgnoreCase(String.valueOf(entry.getResource().getResourceType()), type))
                .findFirst()
                .map(entry -> parser.parseResource(resourceClass, parser.encodeResourceToString(entry.getResource())))
                .orElse(null);
    }

    public void insertRecords(String apiCallId, String participantCode, String recipientCode, String rawPayload, String reqFhir, String workflowId, String correlationId) throws ClientException {
        String query = String.format("INSERT INTO %s (request_id,sender_code,recipient_code,raw_payload,request_fhir,response_fhir,action,status,correlation_id,workflow_id, insurance_id, patient_name, bill_amount, mobile, app, created_on, updated_on, approved_amount,supporting_documents,otp_status,bank_status,remarks) VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s',%d,%d,'%s',ARRAY[%s],'%s','%s','%s');",
                providerServiceTable, apiCallId, participantCode, recipientCode, rawPayload, reqFhir, "", "communication", PENDING, correlationId, workflowId, "", "", "", "", "", System.currentTimeMillis(), System.currentTimeMillis(), "", "ARRAY[]::character varying[]", PENDING, PENDING, "");
        postgres.execute(query);
        logger.info("Inserted the request details into the database : {} ", apiCallId);
    }
}
