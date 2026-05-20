package com.example.astrastudioopenai.service.oss;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class OssService {

    private static final Logger logger = LoggerFactory.getLogger(OssService.class);

    @Value("${oss.access-key-id:}")
    private String accessKeyId;

    @Value("${oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${oss.bucket:}")
    private String bucket;

    @Value("${oss.region:}")
    private String region;

    @Value("${oss.endpoint:}")
    private String endpoint;

    @Value("${oss.custom-domain:}")
    private String customDomain;

    @Value("${oss.max-file-size:10485760}")
    private long maxFileSize;

    public Map<String, String> generatePresignPolicy(String fileName) {
        if (accessKeyId == null || accessKeyId.isBlank()) {
            throw new IllegalStateException("OSS accessKeyId not configured");
        }
        if (accessKeySecret == null || accessKeySecret.isBlank()) {
            throw new IllegalStateException("OSS accessKeySecret not configured");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("OSS bucket not configured");
        }

        String objectName = generateObjectName(fileName);
        String uploadUrl = buildUploadUrl();
        String publicUrl = buildPublicUrl(objectName);
        String policy = buildPolicy(objectName);
        String signature = hmacSha1Sign(accessKeySecret, policy);

        log.debug("Generated presign policy: objectName={}, fileName={}", objectName, fileName);

        Map<String, String> result = new HashMap<>();
        result.put("uploadUrl", uploadUrl);
        result.put("objectName", objectName);
        result.put("policy", policy);
        result.put("OSSAccessKeyId", accessKeyId);
        result.put("signature", signature);
        result.put("publicUrl", publicUrl);
        return result;
    }

    private String generateObjectName(String fileName) {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String ext = "";
        if (fileName != null && fileName.contains(".")) {
            ext = "." + fileName.substring(fileName.lastIndexOf('.') + 1);
        }
        return "chat/" + timestamp + "_" + random + ext;
    }

    private String buildUploadUrl() {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("OSS endpoint not configured");
        }
        return endpoint.replace("https://", "https://" + bucket + ".");
    }

    private String buildPublicUrl(String objectName) {
        if (customDomain != null && !customDomain.isBlank()) {
            return customDomain + "/" + objectName;
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("OSS endpoint not configured");
        }
        return "https://" + bucket + "." + endpoint.replace("https://", "") + "/" + objectName;
    }

    private String buildPolicy(String objectName) {
        String expiration = Instant.now().plusSeconds(3600).toString();

        String policyJson = "{\"expiration\":\"" + expiration + "\","
                + "\"conditions\":["
                + "{\"bucket\":\"" + bucket + "\"},"
                + "[\"content-length-range\",0," + maxFileSize + "],"
                + "[\"starts-with\",\"$key\",\"chat/\"]"
                + "]}";

        return Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha1Sign(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC-SHA1 signature", e);
        }
    }

    public boolean isAvailable() {
        return accessKeyId != null && !accessKeyId.isBlank()
                && accessKeySecret != null && !accessKeySecret.isBlank()
                && bucket != null && !bucket.isBlank();
    }
}
