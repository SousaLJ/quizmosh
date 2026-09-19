package io.quizmosh.application.account;
/** Adapter must recheck consent at write time and isolate analytics failures. */
public interface AnalyticsPort {
    void record(String subject, String event, String roomCode, String matchId, String referral);
}
