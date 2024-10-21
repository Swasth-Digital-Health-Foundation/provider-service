// Provider system table

CREATE TABLE provider_system (
    request_id            VARCHAR primary key,
    sender_code           VARCHAR,
    recipient_code        VARCHAR,
    raw_payload           VARCHAR,
    request_fhir          VARCHAR,
    response_fhir         VARCHAR,
    action                VARCHAR,
    status                VARCHAR,
    correlation_id        VARCHAR,
    workflow_id           VARCHAR,
    insurance_id          VARCHAR,
    patient_name          VARCHAR,
    bill_amount           VARCHAR,
    mobile                VARCHAR,
    app                   VARCHAR,
    created_on            BIGINT,
    updated_on            BIGINT,
    approved_amount       VARCHAR,
    supporting_documents  VARCHAR[],
    otp_status            VARCHAR,
    bank_status           VARCHAR,
    account_number        VARCHAR,
    ifsc_code             VARCHAR,
    remarks VARCHAR
);


// Consultation info

CREATE TABLE consultation_info(
    workflow_id VARCHAR(255) NOT NULL PRIMARY KEY,
    treatment_type VARCHAR(255),
    service_type VARCHAR(255),
    symptoms VARCHAR (255),
    supporting_documents_url CHARACTER VARYING[]
);

// Patient information

CREATE TABLE IF NOT EXISTS patient_information (
    name VARCHAR NOT NULL,
    beneficiary_id UUID PRIMARY KEY,
    mobile VARCHAR,
    address TEXT,
    payor_details JSONB,
    medical_history JSONB,
    created_on bigint,
    updated_on bigint
);


Env for provider service

hcx_provider_service:
  postgres_db_url: "jdbc:postgresql://terraform-20211111045938760100000001.culmyp72rbwi.ap-south-1/mock_service"
  postgres_user: "hcxpostgresql"
  postgres_password: ""
  url_accessKey: “// access key of s3 bucket to store the documents “
  url_accessSecret: “//secret key of s3 bucket “
  url_bucketName: “// s3 bucket name “
  provider_username: “hosp_demoho_82194121@swasth-hcx-dev”
  provider_password: “Opensaber@123”
  provider_participant_code: “hosp_demoho_82194121@swasth-hcx-dev”
  provider_private_key : “https://raw.githubusercontent.com/Swasth-Digital-Health-Foundation/hcx-platform/main/hcx-apis/src/test/resources/examples/test-keys/private-key.pem”

