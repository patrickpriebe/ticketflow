package com.ticketflow.notification.application.port.out;

import com.ticketflow.notification.domain.model.Ticket;

/**
 * Driven port: keeps a durable copy of the issued ticket outside the database.
 *
 * <p>MongoDB holds the ticket's data; this holds the artefact the customer actually
 * receives. Object storage is the right home for it - it is written once, read
 * rarely, and must survive the database being restored from a backup taken before
 * the ticket existed.
 *
 * <p>Optional by design: the service runs perfectly with archiving switched off, and
 * a storage outage must never stop a paid customer from getting their ticket.
 */
public interface TicketArchive {

    /**
     * @return where the copy was stored, or null when archiving is disabled or the
     *         attempt failed. Never throws - failing to archive is not a reason to
     *         fail the issuing of a ticket that was already paid for.
     */
    String archive(Ticket ticket);
}
