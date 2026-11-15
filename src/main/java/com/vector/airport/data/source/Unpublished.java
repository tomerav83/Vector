package com.vector.airport.data.source;

/**
 * The source has not published that file (yet): a 404, a form that does not offer the period, or a web page or a table
 * without rows where the data should be.
 */
public final class Unpublished extends RuntimeException {

    public Unpublished(String message) {
        super(message);
    }
}
