package com.tigerworkshop.sms2telegram.ui

import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.tigerworkshop.sms2telegram.R
import com.tigerworkshop.sms2telegram.data.SettingsRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import androidx.appcompat.app.AlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SenderSettingsActivityTest {
    private lateinit var repository: SettingsRepository
    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = SettingsRepository(context)
    }
    @Test fun userCanAddEnableAndRemoveLastSender() {
        val controller = Robolectric.buildActivity(SenderSettingsActivity::class.java).setup()
        val activity = controller.get()
        beginAdd(activity)
        activity.findViewById<EditText>(R.id.input_sender).setText(" ExampleService ")
        activity.findViewById<android.view.View>(R.id.button_add_sender).performClick()
        activity.findViewById<SwitchMaterial>(R.id.switch_sender_filter).performClick()
        assertTrue(repository.loadSenderAllowlist().allows("exampleservice"))
        assertFalse(repository.loadSenderAllowlist().allows("OtherSender"))
        val row = activity.findViewById<LinearLayout>(R.id.sender_list).getChildAt(0) as LinearLayout
        row.getChildAt(1).performClick()
        assertTrue(repository.loadSenderAllowlist().enabled)
        assertTrue(repository.loadSenderAllowlist().senders.isEmpty())
        assertEquals(activity.getString(R.string.sender_filter_empty), activity.findViewById<TextView>(R.id.text_filter_status).text.toString())
        controller.pause().stop().destroy()
    }
    @Test fun duplicateAndBlankSenderDoNotChangeList() {
        val controller = Robolectric.buildActivity(SenderSettingsActivity::class.java).setup()
        val activity = controller.get()
        beginAdd(activity)
        val input = activity.findViewById<EditText>(R.id.input_sender)
        val add = activity.findViewById<android.view.View>(R.id.button_add_sender)
        input.setText(" "); add.performClick()
        assertTrue(repository.loadSenderAllowlist().senders.isEmpty())
        input.setText("ExampleService"); add.performClick()
        beginAdd(activity)
        input.setText("EXAMPLESERVICE"); add.performClick()
        assertEquals(setOf("ExampleService"), repository.loadSenderAllowlist().senders)
        controller.pause().stop().destroy()
    }
    @Test fun disablingRequiresConfirmationAndCancelKeepsFilter() {
        val controller = Robolectric.buildActivity(SenderSettingsActivity::class.java).setup()
        val toggle = controller.get().findViewById<SwitchMaterial>(R.id.switch_sender_filter)
        toggle.performClick(); toggle.performClick()
        assertTrue(repository.loadSenderAllowlist().enabled)
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(toggle.isChecked)
        toggle.performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(repository.loadSenderAllowlist().enabled)
        assertFalse(toggle.isChecked)
        controller.pause().stop().destroy()
    }
    @Test fun settingsSurviveActivityRecreation() {
        val controller = Robolectric.buildActivity(SenderSettingsActivity::class.java).setup()
        beginAdd(controller.get())
        controller.get().findViewById<EditText>(R.id.input_sender).setText("ExampleService")
        controller.get().findViewById<android.view.View>(R.id.button_add_sender).performClick()
        controller.get().findViewById<SwitchMaterial>(R.id.switch_sender_filter).performClick()
        controller.recreate()
        assertTrue(controller.get().findViewById<SwitchMaterial>(R.id.switch_sender_filter).isChecked)
        val row = controller.get().findViewById<LinearLayout>(R.id.sender_list).getChildAt(0) as LinearLayout
        assertEquals("ExampleService", (row.getChildAt(0) as TextView).text.toString())
        controller.pause().stop().destroy()
    }
    private fun beginAdd(activity: SenderSettingsActivity) {
        activity.findViewById<android.view.View>(R.id.button_add_sender).performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog)
            .getButton(android.content.DialogInterface.BUTTON_NEUTRAL).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test fun openMessagesRequiresConfirmationAndShowsEntryOnReturn() {
        val controller = Robolectric.buildActivity(SenderSettingsActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<android.view.View>(R.id.button_add_sender).performClick()
        assertNull(org.robolectric.Shadows.shadowOf(activity).nextStartedActivity)
        (ShadowDialog.getLatestDialog() as AlertDialog)
            .getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val intent = requireNotNull(org.robolectric.Shadows.shadowOf(activity).nextStartedActivity)
        assertTrue(requireNotNull(intent.selector).hasCategory(android.content.Intent.CATEGORY_APP_MESSAGING))
        controller.pause().resume()
        assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.input_layout_sender).visibility)
        assertEquals(activity.getString(R.string.sender_save), activity.findViewById<TextView>(R.id.button_add_sender).text.toString())
        controller.pause().stop().destroy()
    }

    @Test fun cancelAddSenderDoesNotOpenMessagesOrChangeList() {
        val controller = Robolectric.buildActivity(SenderSettingsActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<android.view.View>(R.id.button_add_sender).performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog)
            .getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(org.robolectric.Shadows.shadowOf(activity).nextStartedActivity)
        assertTrue(repository.loadSenderAllowlist().senders.isEmpty())
        assertEquals(android.view.View.GONE, activity.findViewById<android.view.View>(R.id.input_layout_sender).visibility)
        controller.pause().stop().destroy()
    }

}
