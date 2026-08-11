package com.ticketflow.order.domain.exception;

/**
 * Base for every business rule violation.
 *
 * <p>The {@code code} is stable, machine-readable and is what the web layer turns
 * into the {@code type} URI of the RFC 7807 problem response. Clients branch on the
 * code; they must never have to parse the message.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
