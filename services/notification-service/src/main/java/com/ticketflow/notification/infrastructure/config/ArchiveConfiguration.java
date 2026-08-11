package com.ticketflow.notification.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.notification.application.port.out.TicketArchive;
import com.ticketflow.notification.infrastructure.archive.DisabledTicketArchive;
import com.ticketflow.notification.infrastructure.archive.S3TicketArchive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Chooses how tickets are archived.
 *
 * <p>Off by default: the service must run without any AWS at all, and a developer
 * should not need LocalStack up just to see an order go through.
 */
@Configuration
public class ArchiveConfiguration {

    @Bean
    @ConditionalOnProperty(name = "ticketflow.archive.enabled", havingValue = "true")
    public S3Client s3Client(@Value("${ticketflow.archive.endpoint:}") String endpoint,
                             @Value("${ticketflow.archive.region:us-east-1}") String region,
                             @Value("${ticketflow.archive.access-key:test}") String accessKey,
                             @Value("${ticketflow.archive.secret-key:test}") String secretKey) {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        if (!endpoint.isBlank()) {
            // LocalStack. Path style because bucket-as-subdomain needs DNS that a
            // local container does not have.
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "ticketflow.archive.enabled", havingValue = "true")
    public TicketArchive s3TicketArchive(S3Client s3Client,
                                         ObjectMapper objectMapper,
                                         @Value("${ticketflow.archive.bucket}") String bucket) {
        return new S3TicketArchive(s3Client, objectMapper, bucket);
    }

    @Bean
    @ConditionalOnProperty(name = "ticketflow.archive.enabled", havingValue = "false", matchIfMissing = true)
    public TicketArchive disabledTicketArchive() {
        return new DisabledTicketArchive();
    }
}
