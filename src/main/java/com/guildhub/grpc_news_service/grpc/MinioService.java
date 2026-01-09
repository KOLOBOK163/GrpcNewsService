package com.guildhub.grpc_news_service.grpc;

import io.minio.*;
import io.minio.http.Method;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioService(MinioClient minioClient,
                              @Value("${minio.bucket}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        createBucket();
    }

    private void createBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO bucket error", e);
        }
    }

    public String uploadImage(byte[] data, String contentType, String fileName)
    {
        if(data == null || data.length == 0)
        {
            return null;
        }

        String fileExtension = "";
        if(fileName != null && fileName.contains("."))
        {
            fileExtension = fileName.substring(fileName.lastIndexOf("."));
        }

        String objectName = "news/" + UUID.randomUUID().toString() + fileExtension;

        try{
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build());
        }catch (Exception e)
        {
            throw new RuntimeException("MinIO upload file failed", e);
        }
    }

    public String updateImage(String oldImageUrl, byte[] newData, String newContentType, String newFilename) {
        if (oldImageUrl != null && !oldImageUrl.isEmpty()) {
            deleteImage(oldImageUrl);
        }

        return uploadImage(newData, newContentType, newFilename);
    }

    public void deleteImage(String imageURL)
    {
        if(imageURL == null || imageURL.isEmpty())
        {
            return;
        }
        try{
            String objectName = getObjectNameFromUrl(imageURL);
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        }catch (Exception e)
        {
            System.err.println("Ошибка удаления из MinIO: " + e.getMessage());
        }
    }

    @NotNull
    private String getObjectNameFromUrl(String imageURL) {
        int bucketStart = imageURL.indexOf(bucketName);
        if(bucketStart == -1)
        {
            throw new IllegalArgumentException("Invalid MinIO Url");
        }

        int objectStart = bucketStart + bucketName.length() + 1;
        int paramStart = imageURL.indexOf('?', objectStart);
        if(paramStart == -1)
        {
            imageURL.substring(objectStart);
        }

        String objectName = imageURL.substring(objectStart, paramStart);
        return objectName;
    }
}
