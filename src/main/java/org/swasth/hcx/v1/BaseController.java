package org.swasth.hcx.v1;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import io.hcxprotocol.init.HCXIntegrator;
import io.hcxprotocol.utils.Operations;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
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
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.utils.JSONUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.swasth.hcx.utils.Constants.*;

public class BaseController {

    @Value("${postgres.table.provider-system}")
    private String providerService;
    @Autowired
    protected HcxIntegratorService hcxIntegratorService;
    @Autowired
    private PostgresService postgresService;
    @Autowired
    protected Environment env;
    IParser parser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

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
                boolean result = hcxIntegrator.processIncoming(JSONUtils.serialize(pay), Operations.COVERAGE_ELIGIBILITY_ON_CHECK, output);
                if (!result) {
                    System.out.println("Error while processing incoming request: " + output);
                    throw new ClientException("Exception while processing incoming request :" + req.getCorrelationId());
                }
                String decryptedFhirPayload = (String) output.get("fhirPayload");
                System.out.println("output map after decryption coverageEligibility" + decryptedFhirPayload);
                System.out.println("decryption successful");
                //processing the decrypted incoming bundle
                bundle = parser.parseResource(Bundle.class, decryptedFhirPayload);
                CoverageEligibilityResponse covRes = OnActionFhirExamples.coverageEligibilityResponseExample();
                covRes.setPatient(new Reference("Patient/RVH1003"));
                replaceResourceInBundleEntry(bundle, "https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-CoverageEligibilityResponseBundle.html", CoverageEligibilityRequest.class, new Bundle.BundleEntryComponent().setFullUrl(covRes.getResourceType() + "/" + covRes.getId().toString().replace("#", "")).setResource(covRes));
                System.out.println("bundle reply " + parser.encodeResourceToString(bundle));
                // check for the request exist if exist then update
                updateTheIncomingRequest(req, "");
            } else if (CLAIM_ONSUBMIT.equalsIgnoreCase(onApiAction)) {
                boolean result = hcxIntegrator.processIncoming(JSONUtils.serialize(pay), Operations.CLAIM_ON_SUBMIT, output);
                if (!result) {
                    System.out.println("Error while processing incoming request: " + output);
                    throw new ClientException("Exception while decrypting claim incoming request :" + req.getCorrelationId());
                }
                String decryptedFhirPayload = (String) output.get("fhirPayload");
                String approvedAmount = getAmount(decryptedFhirPayload);
                System.out.println("Output map after decrypting claim request :" + decryptedFhirPayload);
                updateTheIncomingRequest(req, approvedAmount);
            } else if (PRE_AUTH_ONSUBMIT.equalsIgnoreCase(onApiAction)) {
                boolean result = hcxIntegrator.processIncoming(JSONUtils.serialize(pay), Operations.PRE_AUTH_ON_SUBMIT, output);
                if (!result) {
                    System.out.println("Error while processing incoming request: " + output);
                    throw new ClientException("Exception while decrypting pre auth incoming request :" + req.getCorrelationId());
                }
                String decryptedFhirPayload = (String) output.get("fhirPayload");
                String approvedAmount = getAmount(decryptedFhirPayload);
                System.out.println("output map after decryption preauth " + output);
                updateTheIncomingRequest(req, approvedAmount);
            }
        }
    }

    private void updateTheIncomingRequest(Request req, String approvedAmount) throws ClientException, SQLException {
        String query = String.format("SELECT * FROM %s WHERE correlation_id='%s'", providerService, req.getCorrelationId());
        ResultSet resultSet = postgresService.executeQuery(query);
        if (!resultSet.next()) {
            throw new ClientException("The corresponding request does not exist in the database");
        }
        String query1 = String.format("UPDATE %s SET status = '%s', approved_amount = '%s', updated_on=%d  WHERE correlation_id = '%s'", providerService, req.getStatus(), approvedAmount, System.currentTimeMillis(), req.getCorrelationId());
        postgresService.execute(query1);
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
        ClaimResponse claimResponse = getResourceByType("ClaimResponse", ClaimResponse.class, fhirPayload);
        if (claimResponse != null && claimResponse.getTotal() != null && claimResponse.getTotal().get(0) != null) {
            amount = String.valueOf(claimResponse.getTotal().get(0).getAmount().getValue());
        }
        return amount;
    }

    public <T extends Resource> T getResourceByType(String type, Class<T> resourceClass, String fhirPayload) {
        Bundle parsed = parser.parseResource(Bundle.class, fhirPayload);
        return parsed.getEntry().stream()
                .filter(entry -> StringUtils.equalsIgnoreCase(String.valueOf(entry.getResource().getResourceType()), type))
                .findFirst()
                .map(entry -> parser.parseResource(resourceClass, parser.encodeResourceToString(entry.getResource())))
                .orElse(null);
    }
}
