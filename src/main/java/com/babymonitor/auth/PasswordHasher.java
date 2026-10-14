package com.babymonitor.auth;

import org.mindrot.jbcrypt.BCrypt;

/**
 * bcrypt password checking.
 *
 * <p>The sample accounts seeded by {@code db/init.sql} store
 * {@code bcrypt(name)} as their hash, i.e. the password for "Cindy" is
 * literally "Cindy". That is convenient for a demo and terrible for
 * production -- real accounts must use a password the user chose.
 */
public final class PasswordHasher {

    private PasswordHasher() {
    }

    /**
     * Verifies {@code rawPassword} against a stored bcrypt {@code hash}.
     *
     * @return {@code true} only when the password matches. A malformed hash is
     *         treated as "no match" rather than throwing, so a corrupt row
     *         cannot turn a login attempt into a 500.
     */
    public static boolean matches(String rawPassword, String hash) {
        if (rawPassword == null || hash == null || hash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Hashes a password with a fresh salt. Used by tests and tooling. */
    public static String hash(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }
}
