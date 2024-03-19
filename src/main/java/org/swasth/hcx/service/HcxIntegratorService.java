package org.swasth.hcx.service;

import io.hcxprotocol.init.HCXIntegrator;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


@Service
public class HcxIntegratorService {

    @Autowired
    Environment env;

    private Map<String, Object> configCache = new HashMap<>();

    public HCXIntegrator getHCXIntegrator(String participantCode) throws Exception {
        HCXIntegrator hcxIntegrator;
        if (!configCache.containsKey(participantCode)) {
            hcxIntegrator = HCXIntegrator.getInstance(getConfig(participantCode));
            configCache.put(hcxIntegrator.getParticipantCode(), hcxIntegrator);
        } else {
            hcxIntegrator = (HCXIntegrator) configCache.get(participantCode);
        }
        System.out.println("We are initializing the integrator SDK: " + hcxIntegrator.getParticipantCode() + " :: config map: " + hcxIntegrator.getConfig().toString());
        return hcxIntegrator;
    }

    public Map<String, Object> getConfig(String code) throws IOException {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("protocolBasePath", env.getProperty("hcx_application.url") + "/api/" + env.getProperty("hcx_application.api_version"));
        configMap.put("participantCode", code);
        configMap.put("authBasePath", env.getProperty("hcx_application.token_url"));
        configMap.put("username", env.getProperty("provider.username"));
        configMap.put("password", env.getProperty("provider.password"));
        String certificate = IOUtils.toString(new URL(env.getProperty("provider.private_key")), StandardCharsets.UTF_8.toString());
        configMap.put("encryptionPrivateKey", certificate);
        configMap.put("fhirValidationEnabled", true);
        configMap.put("signingPrivateKey", certificate);
        return configMap;
    }
}
