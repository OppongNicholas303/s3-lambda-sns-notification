package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class S3EventHandlerTest {

    @Mock
    private Context context;

    @Mock
    private SnsClient snsClient;

    private S3EventHandler handler;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        // Configure mock SNS client
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("test-message-id").build());

        // Create handler with mocked dependencies
        handler = new S3EventHandler("arn:aws:sns:us-east-1:123456789012:test-topic", "test", snsClient);
    }

    @Test
    public void testHandleRequest_Success() {
        // Create a sample S3 event
        S3EventNotification.S3BucketEntity bucket = new S3EventNotification.S3BucketEntity("test-bucket", null, null);
        S3EventNotification.S3ObjectEntity object = new S3EventNotification.S3ObjectEntity("test-key", 1L, null, null, null);
        S3EventNotification.S3Entity s3Entity = new S3EventNotification.S3Entity(null, bucket, object, null);

        S3EventNotification.S3EventNotificationRecord record = new S3EventNotification.S3EventNotificationRecord(
                "us-east-1", "ObjectCreated:Put", "aws:s3",
                "2023-01-01T00:00:00.000Z", "1", null, null, s3Entity, null);

        List<S3EventNotification.S3EventNotificationRecord> records = new ArrayList<>();
        records.add(record);

        S3Event event = new S3Event(records);

        // Execute the handler
        String result = handler.handleRequest(event, context);

        // Verify result
        assertNotNull(result);
        assertEquals("Successfully processed S3 event", result);
    }
}