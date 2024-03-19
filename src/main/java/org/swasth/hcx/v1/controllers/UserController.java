package org.swasth.hcx.v1.controllers;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.swasth.hcx.dto.Response;
import org.swasth.hcx.dto.ResponseError;
import org.swasth.hcx.exception.ClientException;
import org.swasth.hcx.exception.ErrorCodes;
import org.swasth.hcx.exception.ServerException;
import org.swasth.hcx.exception.ServiceUnavailbleException;
import org.swasth.hcx.service.PostgresService;
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.utils.JSONUtils;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(Constants.VERSION_PREFIX)
public class UserController {

    @Autowired
    private PostgresService postgres;


    @PostMapping("/user/create")
    public ResponseEntity<Object> create(@RequestBody Map<String, Object> requestBody) {
        try {
            String mobile = (String) requestBody.getOrDefault("mobile", "");
            String userName = (String) requestBody.getOrDefault("name", "");
            String address = (String) requestBody.getOrDefault("address", "");
            Map<String, Object> payorDetails = (Map<String, Object>) requestBody.getOrDefault("payor_details", "");
            Map<String, Object> medicalHistory = (Map<String, Object>) requestBody.getOrDefault("medical_history", "");
            if (mobile.isEmpty()) {
                throw new ClientException("The mobile number is required");
            }
            String selectQuery = String.format("SELECT * FROM %s WHERE mobile='%s'", "patient_information", mobile);
            ResultSet resultSet = postgres.executeQuery(selectQuery);
            if (resultSet.next()) {
                throw new ClientException("User already registered");
            }
            String insertQuery = String.format("INSERT INTO %s (name, beneficiary_id, mobile, address, payor_details, medical_history, created_on, updated_on)" +
                            "VALUES ('%s', '%s', '%s', '%s', '%s', '%s', %d, %d)", "patient_information",
                    userName, UUID.randomUUID(), mobile, address, JSONUtils.serialize(payorDetails), JSONUtils.serialize(medicalHistory), System.currentTimeMillis(), System.currentTimeMillis());
            postgres.execute(insertQuery);
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", System.currentTimeMillis());
            response.put("mobile", mobile);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return exceptionHandler(new Response(), e);
        }
    }

    @GetMapping("/user/search/{mobile}")
    public ResponseEntity<Object> search(@PathVariable() String mobile) {
        try {
            String query = String.format("SELECT * FROM %s WHERE mobile = '%s'", "patient_information", mobile);
            ResultSet resultSet = postgres.executeQuery(query);
            Map<String, Object> responseMap = new HashMap<>();
            while (resultSet.next()) {
                responseMap.put("userName", resultSet.getString("name"));
                responseMap.put("beneficiaryId", resultSet.getString("beneficiary_id"));
                responseMap.put("address", resultSet.getString("address"));
                responseMap.put("payorDetails", JSONUtils.deserialize(resultSet.getString("payor_details"), Map.class));
                responseMap.put("medicalHistory", JSONUtils.deserialize(resultSet.getString("medical_history"), Map.class));
            }
            Response response = new Response(responseMap);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return exceptionHandler(new Response(), e);
        }
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
}
