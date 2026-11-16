package com.vector.airport.data.format.impl;

import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** A stream that keeps only the SHA-256 of what is written to it, e.g. a file for its fingerprint. */
final class Sha256 extends DigestOutputStream {

    Sha256() {
        super(OutputStream.nullOutputStream(), messageDigest());
    }

    /** The SHA-256 of everything written, as hex. */
    String hex() {
        return HexFormat.of().formatHex(getMessageDigest().digest());
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM ships SHA-256", e);
        }
    }
}
