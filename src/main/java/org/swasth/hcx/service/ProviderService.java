package org.swasth.hcx.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import io.hcxprotocol.init.HCXIntegrator;
import io.hcxprotocol.utils.Operations;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.utilities.xhtml.XhtmlDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.swasth.hcx.dto.Response;
import org.swasth.hcx.dto.ResponseError;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.exception.ErrorCodes;
import org.swasth.hcx.exception.ServerException;
import org.swasth.hcx.exception.ServiceUnavailbleException;
import org.swasth.hcx.fhirexamples.OnActionFhirExamples;
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.utils.HCXFHIRUtils;
import org.swasth.hcx.utils.JSONUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

import static org.swasth.hcx.utils.Constants.*;

@Service
public class ProviderService {

    @Value("${beneficiary.protocol-base-path}")
    private String protocolBasePath;

    @Value("${postgres.table.provider-system}")
    private String providerService;

    @Autowired
    private PostgresService postgres;

    @Value("${postgres.table.consultation-info}")
    private String consultationInfoTable;
    IParser parser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

    public ResponseEntity<Object> createCoverageEligibilityRequest(Map<String, Object> requestBody, Operations operations) {
        Response response = new Response();
        try {
            System.out.println("----- requestBody ------- " + requestBody);
            String participantCode = (String) requestBody.getOrDefault("participantCode", "");
            validateKeys("participantCode", participantCode);
            String password = (String) requestBody.getOrDefault("password", "");
            validateKeys("password", password);
            String recipientCode = (String) requestBody.getOrDefault("recipientCode", "");
            validateKeys("recipientCode", recipientCode);
            HCXIntegrator hcxIntegrator = HCXIntegrator.getInstance(initializingConfigMap(participantCode, password));
            CoverageEligibilityRequest ce = OnActionFhirExamples.coverageEligibilityRequestExample();
            String app = (String) requestBody.get("app");
            ce.setText(new Narrative().setDiv(new XhtmlDocument().setValue(app)).setStatus(Narrative.NarrativeStatus.GENERATED));
            Practitioner practitioner = OnActionFhirExamples.practitionerExample();
            Organization hospital = OnActionFhirExamples.providerOrganizationExample();
            hospital.setName((String) requestBody.getOrDefault("providerName", ""));
            Patient patient = OnActionFhirExamples.patientExample();
            String mobile = (String) requestBody.getOrDefault("mobile", "");
            patient.getTelecom().add(new ContactPoint().setValue(mobile).setSystem(ContactPoint.ContactPointSystem.PHONE));
            String patientName = (String) requestBody.getOrDefault("patientName", "");
            patient.getName().add(new HumanName().setText(patientName));
            Organization insurerOrganization = OnActionFhirExamples.insurerOrganizationExample();
            insurerOrganization.setName((String) requestBody.getOrDefault("payor", ""));
            Coverage coverage = OnActionFhirExamples.coverageExample();
            String insuranceId = (String) requestBody.getOrDefault("insuranceId", "");
            coverage.setSubscriberId(insuranceId);
            List<DomainResource> domList = List.of(hospital, insurerOrganization, patient, coverage, practitioner);
            Bundle bundleTest = new Bundle();
            try {
                bundleTest = HCXFHIRUtils.resourceToBundle(ce, domList, Bundle.BundleType.COLLECTION, "https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-CoverageEligibilityRequestBundle.html", hcxIntegrator);
                System.out.println("Resource To Bundle Coverage Eligibility Request \n" + parser.encodeResourceToString(bundleTest));
            } catch (Exception e) {
                System.out.println("Error message " + e.getMessage());
                throw new ClientException(e.getMessage());
            }
            Map<String, Object> output = new HashMap<>();
            String workflowId = UUID.randomUUID().toString();
            String apiCallId = UUID.randomUUID().toString();
            String correlationId = UUID.randomUUID().toString();
            String reqFhir = parser.encodeResourceToString(bundleTest);
            boolean outgoingRequest = hcxIntegrator.processOutgoingRequest(reqFhir, operations, recipientCode, apiCallId, correlationId, workflowId, new HashMap<>(), output);
            if (!outgoingRequest) {
                throw new ClientException("Exception while generating the coverage eligibility request");
            }
            insertRecords(participantCode, recipientCode, "", app, mobile, insuranceId, workflowId, apiCallId, correlationId, reqFhir, patientName);
            Map<String, Object> response1 = ResponseMap(workflowId, participantCode, recipientCode);
            return new ResponseEntity<>(response1, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error   " + e);
            return exceptionHandler(response, e);
        }
    }

    public ResponseEntity<Object> createClaimRequest(Map<String, Object> requestBody, Operations operations) {
        Response response = new Response();
        try {
            String participantCode = (String) requestBody.getOrDefault("participantCode", "");
            validateKeys("participantCode", participantCode);
            String password = (String) requestBody.getOrDefault("password", "");
            validateKeys("password", password);
            String recipientCode = (String) requestBody.getOrDefault("recipientCode", "");
            validateKeys("recipientCode", recipientCode);
            HCXIntegrator hcxIntegrator = HCXIntegrator.getInstance(initializingConfigMap(participantCode, password));
            Claim claim = OnActionFhirExamples.claimExample();
            String billAmount = (String) requestBody.getOrDefault("billAmount", 0);
            claim.setTotal(new Money().setCurrency("INR").setValue(Long.parseLong(billAmount)));
            // To check type is OPD
            String type = (String) requestBody.getOrDefault("type", "");
            claim.setSubType(new CodeableConcept(new Coding().setSystem("https://staging-hcx.swasth.app/hapi-fhir/fhir/CodeSystem/hcx-claim-sub-types").setCode(type)));
            // adding supporting documents (Bill/invoice or prescription)
            String app = (String) requestBody.getOrDefault("app", "");
            claim.setText(new Narrative().setDiv(new XhtmlDocument().setValue(app)).setStatus(Narrative.NarrativeStatus.GENERATED));
            // Add items
            if (StringUtils.equalsIgnoreCase(app, org.swasth.hcx.utils.Constants.ABSP)) {
                addInputsBasedOnApp(requestBody, claim);
            }
            // Adding supporting Documents
            addSupportingDocuments(requestBody, claim);
            Practitioner practitioner = OnActionFhirExamples.practitionerExample();
            Organization hospital = OnActionFhirExamples.providerOrganizationExample();
            hospital.setName((String) requestBody.getOrDefault("providerName", ""));
            Patient patient = OnActionFhirExamples.patientExample();
            String mobile = (String) requestBody.getOrDefault(Constants.MOBILE, "");
            patient.getTelecom().add(new ContactPoint().setValue(mobile).setSystem(ContactPoint.ContactPointSystem.PHONE));
            patient.getName().add(new HumanName().setText((String) requestBody.getOrDefault(PATIENT_NAME, "")));
            Organization insurerOrganization = OnActionFhirExamples.insurerOrganizationExample();
            insurerOrganization.setName((String) requestBody.getOrDefault("payor", ""));
            Coverage coverage = OnActionFhirExamples.coverageExample();
            String insuranceId = (String) requestBody.getOrDefault("insuranceId", "");
            coverage.setSubscriberId(insuranceId);
            List<DomainResource> domList = List.of(hospital, insurerOrganization, patient, coverage, practitioner);
            Bundle bundleTest = new Bundle();
            try {
                bundleTest = HCXFHIRUtils.resourceToBundle(claim, domList, Bundle.BundleType.COLLECTION, "https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-ClaimRequestBundle.html", hcxIntegrator);
                System.out.println("resource To Bundle claim Request\n" + parser.encodeResourceToString(bundleTest));
            } catch (Exception e) {
                System.out.println("Error message " + e.getMessage());
                throw new ClientException(e.getMessage());
            }
            Map<String, Object> output = new HashMap<>();
            String workflowId = "";
            if (!requestBody.containsKey("workflowId")) {
                workflowId = UUID.randomUUID().toString();
            } else {
                workflowId = (String) requestBody.getOrDefault("workflowId", "");
            }
            String apiCallId = UUID.randomUUID().toString();
            String correlationId = UUID.randomUUID().toString();
            String reqFhir = parser.encodeResourceToString(bundleTest);
//            if (requestBody.containsKey("supportingDocuments")) {
//                ArrayList<Map<String, Object>> supportingDocuments = JSONUtils.convert(requestBody.get("supportingDocuments"), ArrayList.class);
//            }
            boolean outgoingRequest = hcxIntegrator.processOutgoingRequest(reqFhir, operations, recipientCode, apiCallId, correlationId, workflowId, new HashMap<>(), output);
            if (!outgoingRequest) {
                throw new ClientException("Exception while generating the claim request :");
            }
            insertRecords(participantCode, recipientCode, billAmount, app, mobile, insuranceId, workflowId, apiCallId, correlationId, reqFhir, "");
            System.out.println("The outgoing request has been successfully generated.");
            Map<String, Object> response1 = ResponseMap(workflowId, participantCode, recipientCode);
            return new ResponseEntity<>(response1, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error   " + e);
            return exceptionHandler(response, e);
        }
    }

    private void insertRecords(String participantCode, String recipientCode, String billAmount, String app, String mobile, String insuranceId, String workflowId, String apiCallId, String correlationId, String reqFhir, String patientName) throws ClientException {
        String query = String.format("INSERT INTO %s (request_id,sender_code,recipient_code,raw_payload,request_fhir,response_fhir,action,status,correlation_id,workflow_id,supporting_documents, insurance_id, patient_name, bill_amount, mobile, app, created_on, updated_on, approved_amount) VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s',%d,%d,'%s');",
                providerService, apiCallId, participantCode, recipientCode, "", reqFhir, "", Constants.CLAIM, PENDING, correlationId, workflowId, "{}", insuranceId, patientName, billAmount, mobile, app, System.currentTimeMillis(), System.currentTimeMillis(), "");
        postgres.execute(query);
        System.out.println("Inserted the request details into the Database : " + apiCallId);
    }

    protected void validateKeys(String field, String value) throws ClientException {
        if (StringUtils.isEmpty(value))
            throw new ClientException("Missing required field " + field);
    }

    public Map<String, Object> ResponseMap(String workflowId, String senderCode, String recipientCode) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("workflowId", workflowId);
        responseMap.put("senderCode", senderCode);
        responseMap.put("recipientCode", recipientCode);
        return responseMap;
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


    public Map<String, Object> initializingConfigMap(String participantCode, String password) throws IOException {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("protocolBasePath", protocolBasePath);
        configMap.put("participantCode", participantCode);
        configMap.put("username", participantCode);
        configMap.put("password", password);
        String keyUrl = "https://raw.githubusercontent.com/Swasth-Digital-Health-Foundation/hcx-platform/main/hcx-apis/src/test/resources/examples/test-keys/private-key.pem";
        String certificate = IOUtils.toString(new URL(keyUrl), StandardCharsets.UTF_8);
        configMap.put("encryptionPrivateKey", certificate);
        configMap.put("signingPrivateKey", certificate);
        return configMap;
    }

    private void addInputsBasedOnApp(Map<String, Object> requestBody, Claim claim) {
        if (requestBody.containsKey(Constants.ITEMS)) {
            ArrayList<Map<String, Object>> itemsList = JSONUtils.convert(requestBody.get(Constants.ITEMS), ArrayList.class);
            for (Map<String, Object> itemMap : itemsList) {
                BigDecimal itemQuantity = new BigDecimal(itemMap.getOrDefault("quantity", 0).toString());
                BigDecimal itemPrice = new BigDecimal(itemMap.getOrDefault("pricing", 0).toString());
                claim.getItem().add(new org.hl7.fhir.r4.model.Claim.ItemComponent().setSequence(1).setQuantity(new Quantity().setValue(itemQuantity)).setProductOrService(new CodeableConcept(new Coding().setCode("E101021").setSystem("https://irdai.gov.in/package-code").setDisplay((String) itemMap.getOrDefault(ITEM_NAME, "")))).setUnitPrice(new Money().setValue(itemPrice).setCurrency("INR")));
            }
        }
        claim.addIdentifier(new Identifier().setSystem("http://identifiersystem.com/treatmentCategory").setValue((String) requestBody.getOrDefault(TREATMENT_CATEGORY, "")));
        claim.addIdentifier(new Identifier().setSystem("http://identifiersystem.com/treatmentSubcategory").setValue((String) requestBody.getOrDefault(TREATMENT_SUB_CATEGORY, "")));
        claim.addIdentifier(new Identifier().setSystem("http://identifiersystem.com/serviceLocation").setValue((String) requestBody.getOrDefault(SERVICE_LOCATION, "")));
        if (requestBody.containsKey(SPECIALITY_TYPE)) {
            claim.addIdentifier(new Identifier().setSystem("http://identifiersystem.com/specialityType").setValue((String) requestBody.getOrDefault(SPECIALITY_TYPE, "")));
        }
    }

    private void addSupportingDocuments(Map<String, Object> requestBody, Claim claim) {
        if (requestBody.containsKey("supportingDocuments")) {
            ArrayList<Map<String, Object>> supportingDocuments = JSONUtils.convert(requestBody.get("supportingDocuments"), ArrayList.class);
            for (Map<String, Object> document : supportingDocuments) {
                String documentType = (String) document.getOrDefault("documentType", "");
                List<String> urls = (List<String>) document.get("urls");
                if (urls != null && !urls.isEmpty()) {
                    for (String url : urls) {
                        claim.addSupportingInfo(new Claim.SupportingInformationComponent().setSequence(1).setCategory(new CodeableConcept(new Coding().setCode(documentType).setSystem("http://hcxprotocol.io/codes/claim-supporting-info-categories").setDisplay(documentType))).setValue(new Attachment().setUrl(url)));
                    }
                }
            }
        }
    }
}
