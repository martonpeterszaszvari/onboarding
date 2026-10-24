package com.example.onboarding.verification.data;

/** Strategy selected by the client for the initial verification attempt. */
public enum VerificationMode {
    SYNC,   // Try once on the request thread; fall back to the durable worker if unfinished.
    ASYNC   // Return immediately; the durable worker performs all verification attempts.
}
