package app.revanced.patches.youtube.misc.playbackposition

import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.shared.misc.settings.preference.SwitchPreference
import app.revanced.patches.youtube.misc.extension.sharedExtensionPatch
import app.revanced.patches.youtube.misc.settings.PreferenceScreen
import app.revanced.patches.youtube.video.information.videoInformationPatch
import app.revanced.patches.youtube.video.information.videoTimeHook

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/youtube/patches/RememberPlaybackPositionPatch;"

val rememberPlaybackPositionPatch = bytecodePatch(
    name = "Remember playback position",
    description = "Adds an option to resume videos where you left off, " +
            "including when videos auto-advance inside a playlist or autoplay queue.",
) {
    dependsOn(
        sharedExtensionPatch,
        addResourcesPatch,
        videoInformationPatch,
    )

    compatibleWith(
        "com.google.android.youtube"(
            "20.14.43",
            "20.21.37",
            "20.26.46",
            "20.31.42",
            "20.37.48",
            "20.40.45",
        ),
    )

    apply {
        addResources("youtube", "misc.playbackposition.rememberPlaybackPositionPatch")

        PreferenceScreen.PLAYER.addPreferences(
            SwitchPreference("revanced_remember_playback_position"),
        )

        // Reuse VideoInformation's per-second time hook. The extension reads the
        // current video id/length directly from VideoInformation.
        videoTimeHook(EXTENSION_CLASS_DESCRIPTOR, "setVideoTime")
    }
}
