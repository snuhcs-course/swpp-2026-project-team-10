// AI-generated with Claude Code, 2026-11-05, reviewed by Jaewan Park
package com.lastpenguin.pix.generation

import android.graphics.BitmapFactory
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone's person check on the labeled photos of the AI module tests (Testing Documentation 3.1, FR-4.13): the
 * app's own [RuleBasedPersonDetector] with ML Kit on a real device, so it measures and does not pass or fail on
 * accuracy. It only fails when there are no photos. From the repository's root, with one device connected
 * (server/README.md, *Evaluate the AI modules*):
 *
 *     (cd android && ./gradlew :app:installDebug :app:installDebugAndroidTest)
 *     adb push server/eval/person/. /data/local/tmp/pix-eval-person/
 *     adb shell am instrument -w -e class com.lastpenguin.pix.generation.PersonCheckEval \
 *         com.lastpenguin.pix.test/androidx.test.runner.AndroidJUnitRunner
 *     adb pull /sdcard/Android/data/com.lastpenguin.pix/files/phone.json
 *
 * Gradle's `connectedDebugAndroidTest` would uninstall the app at the end, and `phone.json` with it. The photos are
 * read through the instrumentation's shell, which can read what `adb push` left in `/data/local/tmp`; the app cannot.
 * Each photo in `with/` and `without/` is decoded as it is stored and checked as the app checks the shutter's photo;
 * `phone.json` holds each one's answer, its landmarks in the frame, and its faces, with the device.
 */
@RunWith(AndroidJUnit4::class)
class PersonCheckEval {

    @Test
    fun checksEveryPhoto() = runBlocking<Unit> {
        val photos = listOf("with", "without").flatMap { label ->
            shell("ls $ROOT/$label").decodeToString().lines().map { it.trim() }
                .filter { it.substringAfterLast('.', "").lowercase() in PHOTO_EXTENSIONS }
                .sorted()
                .map { label to it }
        }
        assertTrue("No photos in $ROOT: push server/eval/person there first", photos.isNotEmpty())

        val signals = MlKitPersonSignals()
        val detector = RuleBasedPersonDetector(signals)
        val results = JSONArray()
        var refusedPeople = 0
        var passedEmpty = 0
        for ((label, name) in photos) {
            val bytes = shell("cat $ROOT/$label/$name")
            val scene = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "$name is not an image" }
            val found = detector.findsPerson(scene)
            val landmarks = signals.poseLikelihoods(scene).count { it >= RuleBasedPersonDetector.MIN_LIKELIHOOD }
            val faces = signals.faceCount(scene)
            val person = label == "with"
            if (person && !found) refusedPeople++
            if (!person && found) passedEmpty++
            results.put(
                JSONObject()
                    .put("scene", name)
                    .put("person", person)
                    .put("found", found)
                    .put("landmarks", landmarks)
                    .put("faces", faces),
            )
            Log.i(TAG, "$name: found=$found landmarks=$landmarks faces=$faces")
        }
        val withPerson = photos.count { it.first == "with" }
        val record = JSONObject()
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("sdk", Build.VERSION.SDK_INT)
            .put("results", results)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "phone.json").writeText(record.toString(2))
        Log.i(
            TAG,
            "with a person refused: $refusedPeople of $withPerson; " +
                "without a person passed: $passedEmpty of ${photos.size - withPerson}",
        )
    }

    /** What a shell command prints, run as the shell user, which can read what `adb push` wrote. */
    private fun shell(command: String): ByteArray {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private companion object {
        const val TAG = "PersonCheckEval"
        const val ROOT = "/data/local/tmp/pix-eval-person"
        val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png")
    }
}
