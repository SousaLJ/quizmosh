package io.quizmosh.domain.account;

/** Stable provider/subject pair. Never associate accounts using an unverified email. */
public record UserIdentity(String provider, String subject) {
    public UserIdentity {
        if (provider == null || !provider.matches("[a-z][a-z0-9-]{1,39}") || subject == null || subject.isBlank() || subject.length() > 255)
            throw new IllegalArgumentException("Invalid identity");
    }
}
