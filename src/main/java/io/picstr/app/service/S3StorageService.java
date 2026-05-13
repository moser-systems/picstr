package io.picstr.app.service;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import io.picstr.app.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;

    public S3StorageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public void upload(String key, InputStream content, long contentLength, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(properties.getS3().getBucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromInputStream(content, contentLength));
    }

    @Override
    public Optional<StorageObject> get(String key) {
        try {
            var response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(key)
                    .build());

            var bytes = response.readAllBytes();
            var contentType = response.response().contentType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return Optional.of(new StorageObject(new ByteArrayInputStream(bytes), bytes.length, contentType));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get file from S3: " + key, e);
        }
    }

    @Override
    public List<String> listKeys() {
        try {
            var request = ListObjectsV2Request.builder()
                    .bucket(properties.getS3().getBucket())
                    .build();

            return s3Client.listObjectsV2Paginator(request).stream()
                    .flatMap(response -> response.contents().stream())
                    .map(item -> item.key())
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to list files from S3", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(key)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from S3: " + key, e);
        }
    }
}
