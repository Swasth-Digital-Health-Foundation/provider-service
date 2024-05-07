package org.swasth.hcx.v1.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.swasth.hcx.models.User;
import org.swasth.hcx.service.PostgresService;
import org.swasth.hcx.service.ProviderService;
import org.swasth.hcx.utils.Constants;
import org.swasth.hcx.utils.JSONUtils;

import java.sql.ResultSet;
import java.util.*;

@RestController
@RequestMapping(Constants.VERSION_PREFIX)
public class UserController {

    @Autowired
    private PostgresService postgres;
    private static final Logger logger = LoggerFactory.getLogger(ProviderService.class);

    @PostMapping(Constants.USER_CREATE)
    public ResponseEntity<Object> create(@RequestBody Map<String, Object> requestBody) {
        try {
            logger.info("Creating user with request body {}", requestBody);
            User user = new User(requestBody);
            if (user.getMobile().isEmpty()) {
                throw new ClientException("The mobile number is required");
            }
            insertQuery(user);
            Map<String, Object> response = new HashMap<>();
            response.put(Constants.TIMESTAMP, System.currentTimeMillis());
            response.put(Constants.MOBILE, user.getMobile());
            logger.info("User created successfully with mobile : {}", user.getMobile());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return exceptionHandler(new Response(), e);
        }
    }

    @GetMapping(Constants.USER_SEARCH)
    public ResponseEntity<Object> search(@PathVariable() String mobile) {
        try (ResultSet resultSet = postgres.executeQuery(String.format("SELECT * FROM %s WHERE mobile = '%s'", "patient_information", mobile));) {
            logger.info("Searching user with mobile number {}", mobile);
            Map<String, Object> responseMap = extractResultSetData(resultSet);
            Response response = new Response(responseMap);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return exceptionHandler(new Response(), e);
        }
    }

    @PostMapping(Constants.USER_UPDATE)
    public ResponseEntity<Object> update(@RequestBody Map<String, Object> requestBody) {
        try {
            String mobile = (String) requestBody.getOrDefault("mobile", "");
            if (!requestBody.containsKey("mobile") && mobile.isEmpty()) {
                throw new ClientException("Mobile number is mandatory to update the details");
            }
            requestBody.remove("mobile");
            StringBuilder updateQuery = buildUpdateQuery(requestBody);
            updateQuery.append(" WHERE mobile = '").append(mobile).append("'");
            postgres.execute(updateQuery.toString());
            logger.info("User updated successfully for mobile number: {}", mobile);
            return ResponseEntity.ok(Map.of(Constants.MOBILE, mobile));
        } catch (Exception e) {
            return exceptionHandler(new Response(), e);
        }
    }

    private void insertQuery(User user) throws JsonProcessingException, ClientException {
        String insertQuery = String.format("INSERT INTO %s (name, beneficiary_id, mobile, address, payor_details, medical_history, created_on, updated_on)" +
                        "VALUES ('%s', '%s', '%s', '%s', '%s', '%s', %d, %d)", "patient_information",
                user.getName(), UUID.randomUUID(), user.getMobile(), user.getAddress(), JSONUtils.serialize(user.getInsuranceDetails()), JSONUtils.serialize(user.getMedicalHistory()), System.currentTimeMillis(), System.currentTimeMillis());
        postgres.execute(insertQuery);
    }

    private StringBuilder buildUpdateQuery(Map<String, Object> requestBody) throws ClientException {
        try {
            StringBuilder updateQuery = new StringBuilder("UPDATE patient_information SET ");
            boolean firstEntry = true;
            for (Map.Entry<String, Object> entry : requestBody.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (!firstEntry) {
                    updateQuery.append(", ");
                }
                if (value instanceof Map<?, ?> || value instanceof ArrayList<?>) {
                    String serializedData = JSONUtils.serialize(entry.getValue());
                    updateQuery.append(key).append(" = '").append(serializedData).append("'");
                } else {
                    updateQuery.append(key).append(" = '").append(value).append("'");
                }
                firstEntry = false;
            }
            if (updateQuery.toString().equals("UPDATE patient_information SET ")) {
                throw new ClientException("No valid fields provided for update");
            }
            return updateQuery;
        } catch (Exception e) {
            throw new ClientException(e.getMessage());
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

    private Map<String, Object> extractResultSetData(ResultSet resultSet) throws Exception {
        Map<String, Object> responseMap = new HashMap<>();
        while (resultSet.next()) {
            responseMap.put("userName", resultSet.getString("name"));
            responseMap.put("beneficiaryId", resultSet.getString("beneficiary_id"));
            responseMap.put("address", resultSet.getString("address"));
            responseMap.put("payorDetails", JSONUtils.deserialize(resultSet.getString("payor_details"), List.class));
            responseMap.put("medicalHistory", JSONUtils.deserialize(resultSet.getString("medical_history"), List.class));
        }
        return responseMap;
    }
}
