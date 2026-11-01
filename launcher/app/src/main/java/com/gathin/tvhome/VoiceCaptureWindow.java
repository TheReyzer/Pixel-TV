package com.gathin.tvhome;

/** Some TV providers end a silent attempt early; keep the user's start window bounded. */
final class VoiceCaptureWindow {
    static final long START_WINDOW_MS=12_000;
    private final long deadline;
    private boolean heardSpeech;
    VoiceCaptureWindow(long now) { deadline=now+START_WINDOW_MS; }
    void speechStarted() { heardSpeech=true; }
    boolean retrySilence(long now) { return !heardSpeech&&now<deadline; }
}
