package com.ticketflow.notification.domain.model;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An issued ticket.
 *
 * <p>Carries a snapshot of the event instead of a reference to it. If the organiser
 * renames the show tomorrow, a ticket issued today still reads correctly - and
 * displaying it needs no call to any other service, which is the whole reason this
 * lives in a document store.
 *
 * <p>The identifier is <strong>deterministic</strong>: derived from the order, the
 * category and the seat number within that line. That is what makes issuing
 * idempotent without a transaction - a redelivered PAGAMENTO_APROVADO rewrites the
 * same documents instead of minting a second set of tickets. MongoDB standalone has
 * no multi-document transactions, so the identity has to carry that weight.
 */
public record Ticket(String id,
                     String ticketCode,
                     String orderId,
                     String eventId,
                     EventSnapshot eventSnapshot,
                     TicketCategory ticketCategory,
                     Holder holder,
                     String qrCodePayload,
                     TicketStatus status,
                     Instant issuedAt) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 10;

    public Ticket {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ticketCode, "ticketCode is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(holder, "holder is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(issuedAt, "issuedAt is required");
    }

    /**
     * @param seat 1-based position within the order line, so buying three Pista
     *             tickets yields three distinct, stable identities
     */
    public static Ticket issue(String orderId,
                               String eventId,
                               EventSnapshot eventSnapshot,
                               TicketCategory category,
                               Holder holder,
                               int seat,
                               Instant issuedAt) {
        String id = deterministicId(orderId, category.id(), seat);
        return new Ticket(
                id,
                generateCode(),
                orderId,
                eventId,
                eventSnapshot,
                category,
                holder,
                // Stands in for a real QR payload; a production one would be signed.
                "TF|" + id,
                TicketStatus.ISSUED,
                issuedAt);
    }

    /**
     * Same inputs always produce the same id, which is what an upsert needs to be
     * idempotent across redeliveries.
     */
    public static String deterministicId(String orderId, String categoryId, int seat) {
        return UUID.nameUUIDFromBytes(
                        "%s|%s|%d".formatted(orderId, categoryId, seat).getBytes())
                .toString();
    }

    /** Matches the ^TF-[A-Z0-9]{10}$ pattern the collection validator enforces. */
    private static String generateCode() {
        StringBuilder code = new StringBuilder("TF-");
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    public record EventSnapshot(String name, String venue, Instant startsAt) {
    }

    public record TicketCategory(String id, String name) {
    }

    public record Holder(String customerId, String name, String email) {
    }
}
