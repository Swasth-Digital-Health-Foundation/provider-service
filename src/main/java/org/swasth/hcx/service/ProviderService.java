package org.swasth.hcx.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.amazonaws.services.dynamodbv2.xspec.S;
import io.hcxprotocol.init.HCXIntegrator;
import io.hcxprotocol.utils.Operations;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.utilities.xhtml.XhtmlDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
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
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.swasth.hcx.utils.Constants.*;

@Service
public class ProviderService {

    @Value("${hcx_application.protocol-base-path}")
    private String protocolBasePath;
    @Value("${postgres.table.provider-system}")
    private String providerServiceTable;
    @Value("${aws-url.bucketName}")
    private String bucketName;
    @Autowired
    private PostgresService postgres;
    @Autowired
    private CloudStorageClient cloudStorageClient;
    @Autowired
    private SMSService smsService;
    @Value("${postgres.table.beneficiary}")
    private String beneficiaryTable;

    @Value("${otp.expiry}")
    private int otpExpiry;
    IParser parser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

    private static final Logger logger = LoggerFactory.getLogger(ProviderService.class);

    public ResponseEntity<Object> createCoverageEligibilityRequest(Map<String, Object> requestBody, Operations operations) {
        Response response = new Response();
        try {
            logger.info("Coverage Eligibility request started " + requestBody);
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
                throw new ClientException("Exception while generating the coverage eligibility request" + output);
            }
            insertRecords(participantCode, recipientCode, "", app, mobile, insuranceId, workflowId, apiCallId, correlationId, reqFhir, patientName, COVERAGE_ELIGIBILITY, "ARRAY[]::character varying[]");
            Map<String, Object> response1 = ResponseMap(workflowId, participantCode, recipientCode);
            return new ResponseEntity<>(response1, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error   " + e);
            return exceptionHandler(response, e);
        }
    }

    public ResponseEntity<Object> createClaimRequest(Map<String, Object> requestBody, Operations operations, String action) {
        logger.info("Claim or preAuth request started " + requestBody);
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
            if (requestBody.containsKey("diagnosis")){
                List<Map<String,Object>> diagnosis = (List<Map<String, Object>>) requestBody.getOrDefault("diagnosis", new ArrayList<>());
                for(Map<String,Object> diagnosisMap : diagnosis){
                    claim.getDiagnosis().add(new Claim.DiagnosisComponent().addType(new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/ex-diagnosistype").setCode("admitting").setDisplay("Admitting Diagnosis"))).setSequence(1).setDiagnosis(new CodeableConcept(new Coding().setSystem((String) diagnosisMap.getOrDefault("system", "")).setCode((String) diagnosisMap.getOrDefault("value","")).setDisplay((String) diagnosisMap.getOrDefault("label",""))).setText((String) diagnosisMap.getOrDefault("label",""))));
                }
            }
            if (requestBody.containsKey("items")){

                List<Map<String,Object>> items = (List<Map<String, Object>>) requestBody.getOrDefault("items", new ArrayList<>());
                String amountItems = (String) requestBody.getOrDefault("amountItems", 0);
                ArrayList<String> amountList = new ArrayList<>(Arrays.asList(amountItems.split(",")));
                for(int i=0; i < items.size(); i++){
                    Map<String, Object> itemsMap = items.get(i);
                    // Get the amount for this item, handle any parsing errors gracefully
                    long amount = 0; // Default value if parsing fails
                    if (i < amountList.size()) {
                        try {
                            amount = Long.parseLong(amountList.get(i).trim());
                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing amount for index " + i + ": " + e.getMessage());
                        }
                    }
                    claim.getItem().add(new Claim.ItemComponent().setSequence(1).setProductOrService(new CodeableConcept(new Coding().setSystem((String) itemsMap.getOrDefault("system", "")).setCode((String) itemsMap.getOrDefault("value", "")).setDisplay((String) itemsMap.getOrDefault("label", "")))).setUnitPrice(new Money().setValue(amount).setCurrency("INR")));
                }
            }

            if (requestBody.containsKey("procedures")){
                List<Map<String,Object>> items = (List<Map<String, Object>>) requestBody.getOrDefault("procedures", new ArrayList<>());
                for(Map<String,Object> itemsMap : items){
                    claim.getProcedure().add(new Claim.ProcedureComponent().setSequence(1).setProcedure(new CodeableConcept(new Coding().setCode((String) itemsMap.getOrDefault("value", "")).setDisplay((String) itemsMap.getOrDefault("label", "")).setSystem((String) itemsMap.getOrDefault("system", "")))));
                }
            }
            // Adding supporting Documents
            addSupportingDocuments(requestBody, claim);
            Practitioner practitioner = OnActionFhirExamples.practitionerExample();
            Organization hospital = OnActionFhirExamples.providerOrganizationExample();
            hospital.setName((String) requestBody.getOrDefault("providerName", ""));
            Patient patient = OnActionFhirExamples.patientExample();
            String patientName = (String) requestBody.getOrDefault(PATIENT_NAME, "");
            String mobile = (String) requestBody.getOrDefault(Constants.MOBILE, "");
            Address address = new Address();
            address.setCity((String) requestBody.getOrDefault("address", ""));
            patient.addAddress(address);

            patient.getTelecom().add(new ContactPoint().setValue(mobile).setSystem(ContactPoint.ContactPointSystem.PHONE));
            patient.getName().add(new HumanName().setText(patientName));
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
            List<Map<String, Object>> supportingDocuments = (ArrayList<Map<String, Object>>) requestBody.getOrDefault("supportingDocuments", new ArrayList<>());
            List<Object> urlList = new ArrayList<>();
            if (requestBody.containsKey("supportingDocuments")) {
                for (Map<String, Object> urls : supportingDocuments) {
                    List<String> url = (List<String>) urls.getOrDefault("urls", "");
                    urlList.addAll(url);
                }
            }
            String supportingDocumentsUrl = urlList.stream()
                    .map(document -> "'" + document + "'")
                    .collect(Collectors.joining(","));
            boolean outgoingRequest = hcxIntegrator.processOutgoingRequest(reqFhir, operations, recipientCode, apiCallId, correlationId, workflowId, new HashMap<>(), output);
            if (!outgoingRequest) {
                throw new ClientException("Exception while generating the claim request :");
            }
            insertRecords(participantCode, recipientCode, billAmount, app, mobile, insuranceId, workflowId, apiCallId, correlationId, reqFhir, patientName, action, supportingDocumentsUrl.isEmpty() ? "ARRAY[]::character varying[]" : supportingDocumentsUrl);
            System.out.println("The outgoing request has been successfully generated.");
            Map<String, Object> response1 = ResponseMap(workflowId, participantCode, recipientCode);
            return new ResponseEntity<>(response1, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error   " + e);
            return exceptionHandler(response, e);
        }
    }

    public ResponseEntity<Object> createCommunicationOnRequest(Map<String, Object> requestBody) {
        try {
            String requestId = (String) requestBody.getOrDefault("request_id", "");
            String participantCode = (String) requestBody.getOrDefault("participantCode", "");
            validateKeys("participantCode", participantCode);
            String password = (String) requestBody.getOrDefault("password", "");
            validateKeys("password", password);
            String recipientCode = (String) requestBody.getOrDefault("recipientCode", "");
            String mobile = (String) requestBody.getOrDefault("mobile", "");
            validateKeys("recipientCode", recipientCode);
            String type = (String) requestBody.getOrDefault("type", "");
            if (StringUtils.equalsIgnoreCase(type, "otp")) {
                boolean isValid = processOutgoingCallbackCommunication("otp", requestId, (String) requestBody.get("otp_code"), "", "", participantCode, password, mobile);
                return validateRequest(isValid);
            } else if (StringUtils.equalsIgnoreCase((String) requestBody.get(TYPE), BANK_DETAILS)) {
                String accountNumber = (String) requestBody.getOrDefault(ACCOUNT_NUMBER, "");
                String ifscCode = (String) requestBody.getOrDefault(IFSC_CODE, "");
                String query = String.format("UPDATE %s SET account_number ='%s',ifsc_code = '%s', bank_status = '%s' WHERE request_id = '%s'", providerServiceTable, accountNumber, ifscCode, "successful", requestId);
                postgres.execute(query);
                logger.info("The bank details updated successfully to the request id : {} ", requestId);
                boolean isValid = processOutgoingCallbackCommunication("bank_details", requestId, "", accountNumber, ifscCode, participantCode, password, "");
                return validateRequest(isValid);
            } else {
                throw new ClientException("Invalid request type");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred: " + e.getMessage());
        }
    }


    private ResponseEntity<Object> validateRequest(boolean isValid) throws ClientException {
        if (isValid) {
            return new ResponseEntity<>(HttpStatus.ACCEPTED);
        } else {
            throw new ClientException("Unable to send communication on request");
        }
    }

    public boolean processOutgoingCallbackCommunication(String type, String requestId, String otpCode, String accountNumber, String ifscCode, String participantCode, String password, String mobile) throws Exception {
        Communication communication = OnActionFhirExamples.communication();
        List<DomainResource> domList = new ArrayList<>();
        HCXIntegrator hcxIntegrator = HCXIntegrator.getInstance(initializingConfigMap(participantCode, password));
        if (type.equalsIgnoreCase(OTP)) {
            communication.getIdentifier().add(new Identifier().setSystem("http://www.providerco.com/communication").setValue("otp_verification"));
            communication.getPayload().add(new Communication.CommunicationPayloadComponent().setContent(new StringType().setValue(otpCode)));
            communication.getPayload().add(new Communication.CommunicationPayloadComponent().setContent(new StringType().setValue(mobile)));
        } else {
            communication.getIdentifier().add(new Identifier().setSystem("http://www.providerco.com/communication").setValue("bank_verification"));
            communication.getPayload().add(new Communication.CommunicationPayloadComponent().setContent(new StringType().setValue(accountNumber)));
            communication.getPayload().add(new Communication.CommunicationPayloadComponent().setContent(new StringType().setValue(ifscCode)));
        }
        Bundle bundleTest = new Bundle();
        try {
            bundleTest = HCXFHIRUtils.resourceToBundle(communication, domList, Bundle.BundleType.COLLECTION, "https://ig.hcxprotocol.io/v0.7.1/StructureDefinition-CommunicationBundle.html", hcxIntegrator);
            System.out.println("resource To Bundle communication Request\n" + parser.encodeResourceToString(bundleTest));
        } catch (Exception e) {
            System.out.println("Error message " + e.getMessage());
            throw new ClientException(e.getMessage());
        }
        String searchCorrelationIdQuery = String.format("SELECT correlation_id FROM %s WHERE request_id = '%s'", providerServiceTable, requestId);
        ResultSet resultSet = postgres.executeQuery(searchCorrelationIdQuery);
        String correlationId = "";
        while (resultSet.next()) {
            correlationId = resultSet.getString("correlation_id");
        }
        String searchActionJweQuery = String.format("SELECT raw_payload from %s where correlation_id = '%s' AND action = 'communication'", providerServiceTable, correlationId);
        ResultSet searchResultSet = postgres.executeQuery(searchActionJweQuery);
        String rawPayload = "";
        while (searchResultSet.next()) {
            rawPayload = searchResultSet.getString("raw_payload");
        }
        Map<String, Object> outputMap = new HashMap<>();
        return hcxIntegrator.processOutgoingCallback(parser.encodeResourceToString(bundleTest), Operations.COMMUNICATION_ON_REQUEST, "", rawPayload, "response.complete", new HashMap<>(), outputMap);
    }



    public void insertRecords(String participantCode, String recipientCode, String billAmount, String app, String mobile, String insuranceId, String workflowId, String apiCallId, String correlationId, String reqFhir, String patientName, String action, String documents) throws ClientException {
        String query = String.format("INSERT INTO %s (request_id,sender_code,recipient_code,raw_payload,request_fhir,response_fhir,action,status,correlation_id,workflow_id, insurance_id, patient_name, bill_amount, mobile, app, created_on, updated_on, approved_amount,supporting_documents,otp_status,bank_status, remarks) VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s',%d,%d,'%s',ARRAY[%s],'%s','%s','%s');",
                providerServiceTable, apiCallId, participantCode, recipientCode, "", reqFhir, "", action, PENDING, correlationId, workflowId, insuranceId, patientName, billAmount, mobile, app, System.currentTimeMillis(), System.currentTimeMillis(), "", documents, PENDING, PENDING, "");
        postgres.execute(query);
        System.out.println("Inserted the request details into the Database : " + apiCallId);
    }


    public void updateOtpAndBankStatus(String type, String correlationId) throws ClientException {
        String updateStatus = String.format("UPDATE %s SET %s = 'successful' WHERE correlation_id = '%s'", providerServiceTable, type, correlationId);
        postgres.execute(updateStatus);
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

    public List<Map<String, Object>> getDocumentUrls(List<MultipartFile> files, String mobile) throws ClientException, SQLException, IOException {
        String query = String.format("SELECT beneficiary_id  FROM %s WHERE mobile = '%s'", "patient_information", mobile);
        String beneficiaryReferenceId = "";
        try (ResultSet resultSet = postgres.executeQuery(query)) {
            while (resultSet.next()) {
                beneficiaryReferenceId = resultSet.getString("beneficiary_id");
            }
        }
        List<Map<String, Object>> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            String pathToFile = String.format("provider-app/%s/%s", beneficiaryReferenceId, fileName);
            cloudStorageClient.putObject(bucketName, pathToFile, file);
            Map<String, Object> response = new HashMap<>();
            response.put("url", cloudStorageClient.getUrl(bucketName, pathToFile).toString());
            response.put("reference_id", beneficiaryReferenceId);
            responses.add(response);
        }
        return responses;
    }

    public void sendOTP(String mobile, String phoneContent) throws ClientException, SQLException {
        try {
            int otpCode = 100_000 + new Random().nextInt(900_000);
            String query = String.format("SELECT * FROM %s WHERE mobile = '%s'", "beneficiary_verification", mobile);
            ResultSet resultSet = postgres.executeQuery(query);
            if (!resultSet.next()) {
                String beneficiaryReferenceId = String.valueOf(UUID.randomUUID());
                String insertQuery = String.format("INSERT INTO %s (mobile, otp_code, mobile_verified, createdon, otp_expiry, bsp_reference_id) VALUES ('%s', %d, false, %d, %d, '%s')", beneficiaryTable, mobile, otpCode, System.currentTimeMillis(), System.currentTimeMillis() + otpExpiry, beneficiaryReferenceId);
                postgres.execute(insertQuery);
                smsService.sendSMS(mobile, phoneContent + "\r\n" + otpCode);
                System.out.println("OTP sent successfully for " + mobile);
            } else {
                String updateQuery = String.format("UPDATE %s SET otp_code = %d, otp_expiry = %d , mobile_verified = %b WHERE mobile = '%s'", beneficiaryTable, otpCode, System.currentTimeMillis() + otpExpiry, false, mobile);
                postgres.execute(updateQuery);
                smsService.sendSMS(mobile, phoneContent + "\r\n" + otpCode);
                System.out.println("OTP sent successfully for " + mobile);
            }
        } catch (ClientException e) {
            throw new ClientException(e.getMessage());
        }
    }


    public ResponseEntity<Object> verifyOTP(Map<String, Object> requestBody) {
        try {
            String mobile = (String) requestBody.get(Constants.MOBILE);
            int userEnteredOTP = Integer.parseInt((String) requestBody.get("otp_code"));
            String query = String.format("SELECT * FROM %s WHERE mobile = '%s'", beneficiaryTable, mobile);
            ResultSet resultSet = postgres.executeQuery(query);
            if (resultSet.next()) {
                boolean isMobileVerified = resultSet.getBoolean("mobile_verified");
                int storedOTP = resultSet.getInt("otp_code");
                long otpExpire = resultSet.getLong("otp_expiry");
                if (isMobileVerified) {
                    return ResponseEntity.badRequest().body(response("Mobile Number already verified", mobile, "failed"));
                }
                if (userEnteredOTP != storedOTP || System.currentTimeMillis() > otpExpire) {
                    throw new ClientException("Invalid OTP or OTP has expired");
                }
                // Update mobile_verified status
                String updateQuery = String.format("UPDATE %s SET mobile_verified = true WHERE mobile = '%s'", beneficiaryTable, mobile);
                postgres.execute(updateQuery);
                return ResponseEntity.ok().body(response("verification is successful", mobile, "successful"));
            } else {
                return ResponseEntity.badRequest().body(response("Record does not exist in the database", mobile, "failed"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public Map<String, Object> response(String message, String mobile, String verification) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("mobile", mobile);
        response.put("verification", verification);
        return response;
    }

    public Map<String, Object> getCommunicationStatus(Map<String, Object> requestBody) throws ClientException, SQLException {
        String requestId = (String) requestBody.get("request_id");
        String query = String.format("SELECT otp_status,bank_status FROM %s WHERE request_id = '%s'", providerServiceTable, requestId);
        ResultSet resultSet = postgres.executeQuery(query);
        Map<String, Object> status = new HashMap<>();
        if (!resultSet.next()) {
            throw new ClientException("Claim Request Id Does not exist in the database");
        }
        status.put("otpStatus", resultSet.getString("otp_status"));
        status.put("bankStatus", resultSet.getString("bank_status"));
        return status;
    }
}
