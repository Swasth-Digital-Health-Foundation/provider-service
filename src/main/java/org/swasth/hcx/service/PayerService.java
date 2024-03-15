package org.swasth.hcx.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.swasth.hcx.dto.Request;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.utils.JSONUtils;

import java.util.*;

import static org.swasth.hcx.utils.Constants.PENDING;

@Service
public class PayerService {

    @Value("${postgres.table.payerData}")
    private String table;

    @Autowired
    private PostgresService postgres;
    private final IParser parser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

    public void process(Request request, String reqFhirObj, String respFhirObj) throws ClientException, JsonProcessingException {
        Map<String, Object> info = new HashMap<>();
        String app = getApp(request.getAction(), reqFhirObj);
        if (!StringUtils.isEmpty(app)) {
            processWithApp(request, info, reqFhirObj, respFhirObj, app);
        } else if (request.getAction().contains(Constants.COMMUNICATION)) {
            String query = String.format("INSERT INTO %s (request_id,sender_code,recipient_code,action,raw_payload,request_fhir,response_fhir,status,additional_info,created_on,updated_on,correlation_id,mobile,otp_verification,workflow_id,account_number,ifsc_code,bank_details,app,supporting_documents,bill_amount,insurance_id,patient_name) VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s',%d,%d,'%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s');",
                    table, request.getApiCallId(), request.getSenderCode(), request.getRecipientCode(), getEntity(request.getAction()), request.getPayload().getOrDefault(Constants.PAYLOAD, ""), reqFhirObj, respFhirObj, PENDING, JSONUtils.serialize(info), System.currentTimeMillis(), System.currentTimeMillis(), request.getCorrelationId(), "", PENDING, request.getWorkflowId(), "1234", "1234", PENDING, "", "{}", "", "", "");
            postgres.execute(query);
        } else {
            processWithOutApp(request, info, reqFhirObj, respFhirObj);
        }
    }

    public void processWithOutApp(Request request, Map<String, Object> info, String reqFhirObj, String respFhirObj) throws ClientException, JsonProcessingException {
        if (!request.getAction().contains(Constants.COVERAGE_ELIGIBILITY)) {
            info.put("medical", Collections.singletonMap("status", PENDING));
            info.put("financial", Collections.singletonMap("status", PENDING));
        }
        String query = String.format("INSERT INTO %s (request_id,sender_code,recipient_code,action,raw_payload,request_fhir,response_fhir,status,additional_info,created_on,updated_on,correlation_id,mobile,otp_verification,workflow_id,account_number,ifsc_code,bank_details,app,supporting_documents,bill_amount,insurance_id,patient_name) VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s',%d,%d,'%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s');",
                table, request.getApiCallId(), request.getSenderCode(), request.getRecipientCode(), getEntity(request.getAction()), request.getPayload().getOrDefault("payload", ""), reqFhirObj, respFhirObj, PENDING, JSONUtils.serialize(info), System.currentTimeMillis(), System.currentTimeMillis(), request.getCorrelationId(), "", PENDING, request.getWorkflowId(), "1234", "1234", PENDING, "", "{}", "", "", "");
        postgres.execute(query);
    }

    public void processWithApp(Request request, Map<String, Object> info, String reqFhirObj, String respFhirObj, String app) throws ClientException, JsonProcessingException {
        String action = request.getAction();
        String query;
        if (action.contains(Constants.COVERAGE_ELIGIBILITY)) {
            query = String.format("INSERT INTO %s (request_id,sender_code,recipient_code,action,raw_payload,request_fhir,response_fhir,status,additional_info,created_on,updated_on,correlation_id,mobile,otp_verification,workflow_id,account_number,ifsc_code,bank_details,app,supporting_documents,bill_amount,insurance_id,patient_name) VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s',%d,%d,'%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s');",
                    table, request.getApiCallId(), request.getSenderCode(), request.getRecipientCode(), getEntity(request.getAction()), request.getPayload().getOrDefault(Constants.PAYLOAD, ""), reqFhirObj, respFhirObj, PENDING, JSONUtils.serialize(info), System.currentTimeMillis(), System.currentTimeMillis(), request.getCorrelationId(), getPatientMobile(reqFhirObj), PENDING, request.getWorkflowId(), "1234", "1234", PENDING, app, "{}", "", getInsuranceId(reqFhirObj), getPatientName(reqFhirObj));
        } else {
            info.put("medical", Collections.singletonMap("status", PENDING));
            info.put("financial", Collections.singletonMap("status", PENDING));
            Map<String, List<String>> documents = getSupportingDocuments(reqFhirObj);
            String amount = getAmount(reqFhirObj);
            String serializedDocuments = JSONUtils.serialize(documents);
            query = String.format("INSERT INTO %s (request_id,sender_code,recipient_code,action,raw_payload,request_fhir,response_fhir,status,additional_info,created_on,updated_on,correlation_id,mobile,otp_verification,workflow_id,account_number,ifsc_code,bank_details,app,supporting_documents,bill_amount,insurance_id,patient_name) VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s',%d,%d,'%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s');",
                    table, request.getApiCallId(), request.getSenderCode(), request.getRecipientCode(), getEntity(request.getAction()), request.getPayload().getOrDefault(Constants.PAYLOAD, ""), reqFhirObj, respFhirObj, PENDING, JSONUtils.serialize(info), System.currentTimeMillis(), System.currentTimeMillis(), request.getCorrelationId(), getPatientMobile(reqFhirObj), PENDING, request.getWorkflowId(), "1234", "1234", PENDING, app, serializedDocuments, amount, getInsuranceId(reqFhirObj), getPatientName(reqFhirObj));
        }
        postgres.execute(query);
    }

    private String getEntity(String action) {
        Map<String, String> actionMap = new HashMap<>();
        actionMap.put(Constants.COVERAGE_ELIGIBILITY_CHECK, Constants.COVERAGE_ELIGIBILITY);
        actionMap.put(Constants.PRE_AUTH_SUBMIT, Constants.PRE_AUTH);
        actionMap.put(Constants.CLAIM_SUBMIT, Constants.CLAIM);
        actionMap.put(Constants.COMMUNICATION_REQUEST, Constants.COMMUNICATION);
        return actionMap.get(action);
    }

    public Map<String, List<String>> getSupportingDocuments(String fhirPayload) {
        Map<String, List<String>> documentMap = new HashMap<>();
        Claim claim = getResourceByType(Constants.CLAIM, Claim.class, fhirPayload);
        if (claim != null) {
            for (Claim.SupportingInformationComponent supportingInfo : claim.getSupportingInfo()) {
                if (supportingInfo.hasValueAttachment() && supportingInfo.getValueAttachment().hasUrl()) {
                    String url = supportingInfo.getValueAttachment().getUrl();
                    String documentType = supportingInfo.getCategory().getCoding().get(0).getDisplay();
                    if (!documentMap.containsKey(documentType)) {
                        documentMap.put(documentType, new ArrayList<>());
                    }
                    documentMap.get(documentType).add(url);
                }
            }
        }
        return documentMap;
    }


    public String getAmount(String fhirPayload) {
        String amount = "0";
        Claim claim = getResourceByType(Constants.CLAIM, Claim.class, fhirPayload);
        if (claim != null && claim.getTotal() != null && claim.getTotal().getValue() != null) {
            amount = String.valueOf(claim.getTotal().getValue());
        }
        return amount;
    }

    public String getInsuranceId(String fhirPayload) {
        String insuranceId = "";
        Coverage coverage = getResourceByType("Coverage", Coverage.class, fhirPayload);
        if (coverage != null && coverage.getSubscriberId() != null) {
            insuranceId = coverage.getSubscriberId();
        }
        return insuranceId;
    }

    public String getPatientName(String fhirPayload) {
        String patientName = "";
        Patient patient = getResourceByType("Patient", Patient.class, fhirPayload);
        if (patient!= null && patient.getName() != null && !CollectionUtils.isEmpty(patient.getName()) && patient.getName().get(0).getTextElement() != null && patient.getName().get(0).getTextElement().getValue() != null) {
            patientName = patient.getName().get(0).getTextElement().getValue();
        }
        return patientName;
    }

    private String getApp(String apiAction, String fhirPayload) {
        if (apiAction.equalsIgnoreCase(Constants.COVERAGE_ELIGIBILITY_CHECK)) {
            Bundle parsed = parser.parseResource(Bundle.class, fhirPayload);
            CoverageEligibilityRequest ce = parser.parseResource(CoverageEligibilityRequest.class, parser.encodeResourceToString(parsed.getEntry().get(0).getResource()));
            if (ce.getText() != null && ce.getText().getDiv().allText() != null)
                return ce.getText().getDiv().allText();
        } else if (apiAction.equalsIgnoreCase(Constants.CLAIM_SUBMIT) || apiAction.equalsIgnoreCase(Constants.PRE_AUTH_SUBMIT)) {
            Bundle parsed = parser.parseResource(Bundle.class, fhirPayload);
            Claim claim = parser.parseResource(Claim.class, parser.encodeResourceToString(parsed.getEntry().get(0).getResource()));
            if (claim.getText() != null && claim.getText().getDiv().allText() != null)
                return claim.getText().getDiv().allText();
        }
        return "";
    }

    public String getPatientMobile(String fhirPayload) {
        String patientMobile = "";
        Patient patient = getResourceByType("Patient", Patient.class, fhirPayload);
        if (patient != null && patient.getTelecom() != null && !CollectionUtils.isEmpty(patient.getTelecom())) {
            patientMobile = patient.getTelecom().get(0).getValue();
        }
        return patientMobile;
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
