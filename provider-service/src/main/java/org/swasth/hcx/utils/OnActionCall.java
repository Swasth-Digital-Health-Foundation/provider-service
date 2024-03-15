package org.swasth.hcx.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hcxprotocol.init.HCXIntegrator;
import io.hcxprotocol.utils.Operations;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.swasth.hcx.dto.ResponseError;
import org.swasth.hcx.service.HcxIntegratorService;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.*;


@Component
public class OnActionCall {

    @Autowired
    Environment env;

    private String onCheckPayloadType;

    @Autowired
    protected HcxIntegratorService hcxIntegratorService;

    public static String getRandomChestItem(List<String> items) {
        return items.get(new Random().nextInt(items.size()));
    }

    public InputStream getFileAsIOStream(final String fileName)
    {
        InputStream ioStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream(fileName);

        if (ioStream == null) {
            throw new IllegalArgumentException(fileName + " is not found");
        }
        return ioStream;
    }

    public Map<String, String> jwsEncryptPayload(String filePath, Map<String, Object> payload) throws Exception{
        RSAPrivateKey rsaPrivateKey;
        Map<String, String> encryptedObject = new HashMap<>();
        InputStream io = getFileAsIOStream(filePath);
        Reader fileReader = new InputStreamReader(io);
        PemReader pemReader = new PemReader(fileReader);
        PemObject pemObject = pemReader.readPemObject();
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(pemObject.getContent());
        KeyFactory factory = KeyFactory.getInstance("RSA");
        rsaPrivateKey = (RSAPrivateKey) factory.generatePrivate(privateKeySpec);
        String jwsEncrypted = Jwts.builder().setClaims(payload).signWith(SignatureAlgorithm.RS256, rsaPrivateKey).compact();
        return encryptedObject;
    }
    @Async("asyncExecutor")
    public void sendOnAction(String recipientCode, String fhirPayload, Operations operation, String actionJwe, String onActionStatus, Map<String,Object> output) throws Exception{
        HCXIntegrator hcxIntegrator = hcxIntegratorService.getHCXIntegrator(recipientCode);
        hcxIntegrator.processOutgoingCallback(fhirPayload, operation,"", actionJwe,onActionStatus, new HashMap<>(), output);
        System.out.println("output of onaction" + output);
    }

    public String searchRegistry(String email) throws Exception {
        System.out.println("Timestamp before registry call: "+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()));
        HttpResponse<String> response = Unirest.post(env.getProperty("hcx_application.token_url"))
                .header("content-type", "application/x-www-form-urlencoded")
                .field("client_id", "registry-frontend")
                .field("username", env.getProperty("mock_payer.username"))
                .field("password", env.getProperty("mock_payer.password"))
                .field("grant_type", "password")
                .asString();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> responseBody = mapper.readValue(response.getBody(), Map.class);
        //creating filter for search query on email
        HashMap<String, HashMap<String, Object>> filter = new HashMap<>();
        filter.put("filters",new HashMap<String, Object>(Map.of("primary_email", new HashMap<>(Map.of("eq", email)))));
        HttpResponse<String> onActionResponse = Unirest.post(env.getProperty("hcx_application.registry_url"))
                    .header("Authorization", "Bearer " + responseBody.get("access_token").toString())
                .header("Content-Type", "application/json")
                .body(filter)
                .asString();
        Map<String, ArrayList> resArray = mapper.readValue(onActionResponse.getBody(), Map.class);
        ArrayList participant =resArray.get("participants");
        Map<String, Object> res = (Map<String, Object>) participant.get(0);
        System.out.println("res for filter " + res.get("participant_code"));
        return (String) res.get("osid");
    }

    public void sendRequest(Map<String,Object> responseObj, String url) throws Exception {
        System.out.println("Timestamp before registry call: "+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()));
        System.out.println("responseObj " + responseObj);
        //Getting username and password for token generation
        Map<String,Object> config = hcxIntegratorService.getParticipantConfig((String) responseObj.get(Constants.SENDER_CODE));
        HttpResponse<String> response = Unirest.post(env.getProperty("hcx_application.token_url"))
                .header("content-type", "application/x-www-form-urlencoded")
                .field("client_id", "registry-frontend")
                .field("username", (String) config.get("participantCode"))
                .field("password", (String) config.get("password"))
                .field("grant_type", "password")
                .asString();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> responseBody = mapper.readValue(response.getBody(), Map.class);
        HttpResponse<String> onActionResponse = Unirest.post((String) config.get("protocolBasePath") + url)
                .header("Authorization", "Bearer " + responseBody.get("access_token").toString())
                .header("Content-Type", "application/json")
                .body(responseObj)
                .asString();
        Map<String, ArrayList> resArray = mapper.readValue(onActionResponse.getBody(), Map.class);
        System.out.println("protocol http response: " + resArray);
    }

    public void sendOnActionErrorProtocolResponse(Map<String, Object> actionJwe, ResponseError error, String url) throws Exception{
        System.out.println("We have come here: " +  error.getCode());
        Map<String, Object> responseObj = new HashMap<>();
        Map<String, Object> headers = (Map<String, Object>) actionJwe.get(Constants.HEADERS);
        responseObj.put(Constants.API_CALL_ID, UUID.randomUUID().toString());
        responseObj.put(Constants.CORRELATION_ID, headers.get(Constants.CORRELATION_ID));
        responseObj.put(Constants.TIMESTAMP, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
        responseObj.put(Constants.ERROR_DETAILS, error);
        responseObj.put(Constants.STATUS, "response.error");
        responseObj.put(Constants.RECIPIENT_CODE, headers.get(Constants.SENDER_CODE));
        responseObj.put(Constants.SENDER_CODE, headers.get(Constants.RECIPIENT_CODE));
        responseObj.put(Constants.ENC, headers.get(Constants.ENC));
        responseObj.put(Constants.ALG, headers.get(Constants.ALG));
        sendRequest(responseObj, url);
    }
}
