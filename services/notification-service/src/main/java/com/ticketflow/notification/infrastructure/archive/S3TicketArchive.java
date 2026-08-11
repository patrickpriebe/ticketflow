package com.ticketflow.notification.infrastructure.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.notification.application.port.out.TicketArchive;
import com.ticketflow.notification.domain.model.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores a copy of the ticket in S3 - LocalStack locally, the real thing elsewhere.
 *
 * <p><strong>Never throws.</strong> The customer has already paid and the ticket
 * already exists in MongoDB; an object storage hiccup must not turn that into a
 * failed message that gets redelivered and reprocessed. It logs and moves on, and
 * the missing {@code archiveLocation} is what a backfill job would look for.
 */
public class S3TicketArchive implements TicketArchive {

    private static final Logger log = LoggerFactory.getLogger(S3TicketArchive.class);

    private final S3Client s3;
    private final ObjectMapper objectMapper;
    private final String bucket;

    public S3TicketArchive(S3Client s3, ObjectMapper objectMapper, String bucket) {
        this.s3 = s3;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
    }

    @Override
    public String archive(Ticket ticket) {
        // Keyed by order then ticket: listing an order's tickets is a prefix scan,
        // and re-archiving the same ticket overwrites instead of duplicating.
        String key = "tickets/%s/%s.json".formatted(ticket.orderId(), ticket.id());

        try {
            byte[] body = objectMapper.writeValueAsBytes(ticket);

            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/json")
                            .build(),
                    RequestBody.fromBytes(body));

            return "s3://%s/%s".formatted(bucket, key);

        } catch (Exception e) {
            log.warn("Could not archive ticket {} - it is issued and valid regardless",
                    ticket.ticketCode(), e);
            return null;
        }
    }
}
