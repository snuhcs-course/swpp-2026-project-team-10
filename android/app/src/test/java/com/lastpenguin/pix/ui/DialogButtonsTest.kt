// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui

import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/*
 * The dialogs' buttons (R&S 6.9, #329), in the app's theme on the Galaxy S22's 360 × 705 dp: the last button's text ends
 * as far in from the dialog's right side as the title starts from its left, 24 dp, with a message and without one, in
 * English and in Korean; the two buttons' texts stay 32 dp apart.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class DialogButtonsTest {

    private fun shown(build: MaterialAlertDialogBuilder.() -> MaterialAlertDialogBuilder): AlertDialog {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_Pix)
        val dialog = MaterialAlertDialogBuilder(activity).build().show()
        shadowOf(Looper.getMainLooper()).idle()
        return dialog
    }

    private fun View.leftInWindow(): Int = IntArray(2).also(::getLocationInWindow)[0]

    /** Where the button's text ends, inside its padding, in the window. */
    private fun Button.textEnd(): Int = leftInWindow() + width - paddingRight

    private fun assertButtonsAsFarInAsTheTitle(dialog: AlertDialog, label: String) {
        val density = dialog.context.resources.displayMetrics.density
        val decor = dialog.window!!.decorView
        // The dialog's card is the window inside the background's insets.
        val cardLeft = decor.leftInWindow() + decor.paddingLeft
        val cardRight = decor.leftInWindow() + decor.width - decor.paddingRight
        val title = dialog.findViewById<View>(androidx.appcompat.R.id.alertTitle)!!
        val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        val start = (title.leftInWindow() + title.paddingLeft - cardLeft) / density
        val end = (cardRight - positive.textEnd()) / density
        val apart = (positive.leftInWindow() + positive.paddingLeft - negative.textEnd()) / density
        assertTrue("$label: the title $start dp in", abs(start - 24f) <= 1f)
        assertTrue("$label: the button's text $end dp in", abs(end - 24f) <= 1f)
        assertTrue("$label: the buttons' texts $apart dp apart", abs(apart - 32f) <= 1f)
    }

    @Test
    fun aQuestion_endsItsButtonsAsFarInAsItsTitleStarts() {
        val dialog = shown {
            setTitle(R.string.leave_session_title).setPositiveButton(R.string.leave, null)
                .setNegativeButton(R.string.cancel, null)
        }
        assertButtonsAsFarInAsTheTitle(dialog, "Leave the session?")
    }

    @Test
    fun aQuestionWithAMessage_endsItsButtonsAsFarInAsItsTitleStarts() {
        val dialog = shown {
            setTitle(R.string.delete_photo_title).setMessage(R.string.delete_photo_message)
                .setPositiveButton(R.string.delete, null).setNegativeButton(R.string.cancel, null)
        }
        assertButtonsAsFarInAsTheTitle(dialog, "Delete this photo?")
    }

    @Test
    @Config(qualifiers = "ko-w360dp-h705dp-xxhdpi")
    fun inKorean_endsItsButtonsAsFarInAsItsTitleStarts() {
        val dialog = shown {
            setTitle(R.string.delete_photo_title).setMessage(R.string.delete_photo_message)
                .setPositiveButton(R.string.delete, null).setNegativeButton(R.string.cancel, null)
        }
        assertButtonsAsFarInAsTheTitle(dialog, "사진을 삭제할까요?")
    }
}
