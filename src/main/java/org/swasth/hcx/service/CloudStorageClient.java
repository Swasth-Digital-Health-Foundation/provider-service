package org.swasth.hcx.service;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;

@Service
public class CloudStorageClient {

    @Value("${aws-url.accessKey}")
    private String accessKey;
    @Value("${aws-url.accessSecret}")
    private String secretKey;

    public AmazonS3 getClient() {
        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .withRegion(Regions.AP_SOUTH_1)
                .build();
    }

    public void putObject(String bucketName, String folderName, MultipartFile content) throws IOException {
        ObjectMetadata md = new ObjectMetadata();
        md.setContentType(content.getContentType());
        getClient().putObject(bucketName, folderName, content.getInputStream(), md);
    }

    public URL getUrl(String bucketName, String path) {
        return getClient().getUrl(bucketName, path);
    }
}
