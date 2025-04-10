package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class S3EventHandler implements RequestHandler<S3Event, String> {
    private static final Logger logger = Logger.getLogger(S3EventHandler.class.getName());
    private final SnsClient snsClient;
    private final String snsTopicArn;
    private final String environment;


    // Default constructor used by AWS Lambda
    public S3EventHandler() {
        this(
                System.getenv("SNS_TOPIC_ARN"),
                System.getenv("ENVIRONMENT"),
                System.getenv("AWS_REGION") != null ? System.getenv("AWS_REGION") : "eu-central-1"
        );
        logger.info("Initialized with SNS_TOPIC_ARN: " + this.snsTopicArn);
    }



    // Constructor for testing and dependency injection
    public S3EventHandler(String snsTopicArn, String environment, String awsRegion) {
        this.snsTopicArn = snsTopicArn;
        this.environment = environment != null ? environment : "dev";

        // Initialize the SNS client with a default region if none provided
        Region region = awsRegion != null ? Region.of(awsRegion) : Region.US_EAST_1;
        this.snsClient = SnsClient.builder()
                .region(region)
                .build();
    }

    // Constructor for testing with mocked dependencies
    public S3EventHandler(String snsTopicArn, String environment, SnsClient snsClient) {
        this.snsTopicArn = snsTopicArn;
        this.environment = environment != null ? environment : "dev";
        this.snsClient = snsClient;
    }

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        try {
            logger.info("Received S3 event: " + s3Event);

            // Process each record in the S3 event
            for (S3EventNotificationRecord record : s3Event.getRecords()) {
                String bucketName = record.getS3().getBucket().getName();
                String objectKey = record.getS3().getObject().getKey();

                // URL decode the object key (S3 keys can be URL encoded)
                objectKey = URLDecoder.decode(objectKey, StandardCharsets.UTF_8);

                logger.info("Processing new file upload: " + objectKey + " in bucket: " + bucketName);

                // Create notification message
                String message = createNotificationMessage(bucketName, objectKey);
                String subject = createNotificationSubject(environment);

                // Send notification via SNS
                PublishResponse response = publishToSns(message, subject);
                logger.info("Notification sent successfully. Message ID: " + response.messageId());
            }

            return "Successfully processed S3 event";
        } catch (Exception e) {
            logger.severe("Error processing S3 event: " + e.getMessage());
            throw new RuntimeException("Error processing S3 event", e);
        }
    }

    private String createNotificationMessage(String bucketName, String objectKey) {
        return String.format("""
            New File Upload Notification
            ---------------------------
            Environment: %s
            Bucket: %s
            File: %s
            Timestamp: %s
            
            This notification was automatically generated by the AWS Lambda function.
            """,
                environment,
                bucketName,
                objectKey,
                java.time.Instant.now());
    }

    private String createNotificationSubject(String environment) {
        return String.format("[%s] New File Upload Notification", environment.toUpperCase());
    }


    private PublishResponse publishToSns(String message, String subject) {
        logger.info("Attempting to publish to SNS with Topic ARN: " + snsTopicArn);
        if (snsTopicArn == null || snsTopicArn.isEmpty()) {
            throw new IllegalStateException("SNS Topic ARN is not configured");
        }
        try {
            PublishRequest request = PublishRequest.builder()
                    .message(message)
                    .subject(subject)
                    .topicArn(snsTopicArn)
                    .build();
            return snsClient.publish(request);
        } catch (SnsException e) {
            logger.severe("SNS publish error: " + e.getMessage());
            throw e;
        }
    }

}