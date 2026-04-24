package app.revanced.extension.youtube.patches;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.settings.preference.SharedPrefCategory;
import app.revanced.extension.youtube.settings.Settings;

/**
 * Persists the last watched position for each non-Shorts video and seeks to it
 * when the video is re-opened. This fixes YouTube's behaviour of restarting
 * videos from 00:00 when they are played from a playlist or autoplay queue,
 * even though the client already tracks the watch progress for direct opens.
 */
@SuppressWarnings("unused")
public final class RememberPlaybackPositionPatch {

    private static final SharedPrefCategory PREFS = new SharedPrefCategory("revanced_playback_positions");

    private static final String KEY_PREFIX = "pos_";

    /** Minimum playback time before a position is worth saving. */
    private static final long MIN_PROGRESS_MS = 10_000L;

    /** Do not save/restore near the end of a video (treat the video as finished). */
    private static final long END_PADDING_MS = 10_000L;

    /** Throttle writes to SharedPreferences. */
    private static final long SAVE_INTERVAL_MS = 5_000L;

    /** Give up trying to restore once the user has been watching for this long. */
    private static final long RESTORE_WINDOW_MS = 5_000L;

    private static String trackedVideoId = "";
    private static boolean restoreAttemptFinished;
    private static long lastSavedTime = -1L;
    private static long lastSaveAtMs;

    /**
     * Injection point. Called on the main thread approximately once per second
     * by the same hook used by {@link VideoInformation#setVideoTime(long)}.
     */
    public static void setVideoTime(long currentTimeMs) {
        try {
            if (!Settings.REMEMBER_PLAYBACK_POSITION.get()) {
                return;
            }
            if (VideoInformation.lastVideoIdIsShort()) {
                return;
            }

            final String videoId = VideoInformation.getVideoId();
            if (videoId.isEmpty()) {
                return;
            }

            final long videoLength = VideoInformation.getVideoLength();

            if (!videoId.equals(trackedVideoId)) {
                trackedVideoId = videoId;
                restoreAttemptFinished = false;
                lastSavedTime = -1L;
                lastSaveAtMs = 0L;
            }

            if (!restoreAttemptFinished) {
                if (currentTimeMs >= RESTORE_WINDOW_MS) {
                    // Too late to restore; user is already past the typical resume point.
                    restoreAttemptFinished = true;
                } else if (videoLength > 0) {
                    final long saved = loadPosition(videoId);
                    if (saved <= MIN_PROGRESS_MS || saved >= videoLength - END_PADDING_MS) {
                        restoreAttemptFinished = true;
                    } else {
                        // Avoid fighting YouTube if it already seeked close to the saved spot
                        // (e.g. when the video was opened directly and YouTube restored it).
                        if (Math.abs(currentTimeMs - saved) < 2_000L) {
                            restoreAttemptFinished = true;
                        } else if (VideoInformation.seekTo(saved)) {
                            Logger.printDebug(() -> "Restored playback position for " + videoId + " to " + saved + "ms");
                            restoreAttemptFinished = true;
                        }
                        // If seekTo failed (player controller not yet ready), retry on the next tick.
                    }
                }
            }

            if (videoLength <= 0 || currentTimeMs < MIN_PROGRESS_MS) {
                return;
            }

            if (currentTimeMs >= videoLength - END_PADDING_MS) {
                // Video is finished; remove any stored position so a later rewatch starts fresh.
                clearPosition(videoId);
                lastSavedTime = -1L;
                return;
            }

            final long now = System.currentTimeMillis();
            if (now - lastSaveAtMs < SAVE_INTERVAL_MS) {
                return;
            }
            if (Math.abs(currentTimeMs - lastSavedTime) < 1_000L) {
                return;
            }

            savePosition(videoId, currentTimeMs);
            lastSavedTime = currentTimeMs;
            lastSaveAtMs = now;
        } catch (Exception ex) {
            Logger.printException(() -> "RememberPlaybackPositionPatch.setVideoTime failure", ex);
        }
    }

    private static long loadPosition(String videoId) {
        try {
            return PREFS.preferences.getLong(KEY_PREFIX + videoId, 0L);
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to load playback position for " + videoId, ex);
            return 0L;
        }
    }

    private static void savePosition(String videoId, long timeMs) {
        try {
            SharedPreferences.Editor editor = PREFS.preferences.edit();
            editor.putLong(KEY_PREFIX + videoId, timeMs);
            editor.apply();
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to save playback position for " + videoId, ex);
        }
    }

    private static void clearPosition(String videoId) {
        try {
            PREFS.preferences.edit().remove(KEY_PREFIX + videoId).apply();
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to clear playback position for " + videoId, ex);
        }
    }

    /**
     * Injection point. Called from the YouTube player-parameter builder. The last parameter is a
     * desugared {@code j$.time.Duration} for where watch history says playback should start (e.g. after
     * watching on another device). That value is read-only; we merge it into the same local store as
     * {@link #setVideoTime(long)} so auto-advance can resume consistently.
     */
    public static void onServerResumeOffset(
            @NonNull String videoId, @Nullable String playerParams, @Nullable Object duration) {
        try {
            if (!Settings.REMEMBER_PLAYBACK_POSITION.get()) {
                return;
            }
            if (videoId.isEmpty() || playerParams == null) {
                return;
            }
            if (VideoInformation.playerParametersAreShort(playerParams)) {
                return;
            }
            if (duration == null) {
                return;
            }
            final long serverMs;
            try {
                serverMs = (long) duration.getClass().getMethod("toMillis").invoke(duration);
            } catch (Exception e) {
                return;
            }
            if (serverMs < MIN_PROGRESS_MS) {
                return;
            }
            final long stored = loadPosition(videoId);
            final long merged = Math.max(stored, serverMs);
            if (merged == stored) {
                return;
            }
            savePosition(videoId, merged);
            Logger.printDebug(
                    () -> "Merged server resume hint for " + videoId + " to " + merged + "ms");
        } catch (Exception ex) {
            Logger.printException(
                    () -> "RememberPlaybackPositionPatch.onServerResumeOffset failure", ex);
        }
    }
}
