package com.ticketflow.notification.infrastructure.archive;

import com.ticketflow.notification.application.port.out.TicketArchive;
import com.ticketflow.notification.domain.model.Ticket;

/**
 * What runs when archiving is switched off.
 *
 * <p>A null object rather than a null reference or an {@code if (archive != null)}
 * scattered through the use case: the caller has exactly one code path either way.
 */
public class DisabledTicketArchive implements TicketArchive {

    @Override
    public String archive(Ticket ticket) {
        return null;
    }
}
