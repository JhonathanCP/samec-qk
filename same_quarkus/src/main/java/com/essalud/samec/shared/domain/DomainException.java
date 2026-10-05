package com.essalud.samec.shared.domain;

/**
 * A business rule of the domain was broken. The message is meant for the end user (in Spanish)
 * and is returned as is with a 400.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
