package org.swasth.hcx.v1.controllers;

import io.hcxprotocol.utils.Operations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.swasth.hcx.service.ProviderService;
import org.swasth.hcx.utils.Constants;

import java.util.Map;

import static org.swasth.hcx.utils.Constants.*;

@RestController
@RequestMapping(Constants.VERSION_PREFIX)
public class ProviderController {

    @Autowired
    private ProviderService providerService;

    @PostMapping(COVERAGE_ELIGIBILITY_CHECK)
    public ResponseEntity<Object> createCoverageEligibility(@RequestHeader HttpHeaders headers, @RequestBody Map<String, Object> requestBody) throws Exception {
        return providerService.createCoverageEligibilityRequest(requestBody, Operations.COVERAGE_ELIGIBILITY_CHECK);
    }

    @PostMapping(CLAIM_SUBMIT)
    public ResponseEntity<Object> createClaimSubmit(@RequestBody Map<String, Object> requestBody) {
        return providerService.createClaimRequest(requestBody, Operations.CLAIM_SUBMIT);
    }

    @PostMapping(PRE_AUTH_SUBMIT)
    public ResponseEntity<Object> createPreAuthSubmit(@RequestBody Map<String, Object> requestBody) {
        return providerService.createClaimRequest(requestBody, Operations.PRE_AUTH_SUBMIT);
    }


}
