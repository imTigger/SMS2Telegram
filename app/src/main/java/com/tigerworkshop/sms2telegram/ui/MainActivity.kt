package com.tigerworkshop.sms2telegram.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tigerworkshop.sms2telegram.R
import com.tigerworkshop.sms2telegram.data.SettingsRepository
import com.tigerworkshop.sms2telegram.data.TelegramForwarder
import com.tigerworkshop.sms2telegram.databinding.ActivityMainBinding
import com.tigerworkshop.sms2telegram.sms.SmsReceiver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepository: SettingsRepository
    private val telegramForwarder = TelegramForwarder()
    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ")

    private enum class WizardStep { STEP0_WELCOME, STEP1_CONFIG, STEP2_PERMISSION, STEP3_SUMMARY }

    private val statusUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateLastStatus()
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    getString(R.string.permission_required_message),
                    Toast.LENGTH_LONG
                ).show()
            }
            updatePermissionUi()
            if (granted) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.step2_completed),
                    Toast.LENGTH_SHORT
                ).show()

                // Enable forwarding by default once permission is granted
                settingsRepository.setForwardingEnabled(true)

                // Move to step 3 automatically once permission is granted
                showStep(WizardStep.STEP3_SUMMARY)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        settingsRepository = SettingsRepository(this)

        bindListeners()
        updatePermissionUi()
        updateLastStatus()
        initWizardInitialStep()
    }

    private fun initWizardInitialStep() {
        val settings = settingsRepository.loadSettings()
        val hasSettings = settings != null
        if (hasSettings) {
            binding.inputToken.setText(settings!!.apiToken)
            binding.inputChatId.setText(settings.chatId)
        }

        val hasPermission = hasSmsPermission()
        val initialStep = when {
            settingsRepository.isFirstLaunch() -> WizardStep.STEP0_WELCOME
            !hasSettings -> WizardStep.STEP1_CONFIG
            !hasPermission -> WizardStep.STEP2_PERMISSION
            else -> WizardStep.STEP3_SUMMARY
        }
        showStep(initialStep)
        if (initialStep != WizardStep.STEP0_WELCOME) {
            settingsRepository.setFirstLaunch(false)
        }
    }

    private fun openHowToUsePage() {
        val uri = "https://github.com/imTigger/SMS2Telegram/tree/main?tab=readme-ov-file#how-to-use".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No browser installed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            statusUpdateReceiver,
            IntentFilter(SmsReceiver.ACTION_STATUS_UPDATED)
        )
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusUpdateReceiver)
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_how_to_use -> {
                openHowToUsePage()
                true
            }
            R.id.action_reset_app -> {
                showResetConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        updateLastStatus()
    }

    private fun bindListeners() {
        binding.buttonStep0Continue.setOnClickListener {
            settingsRepository.setFirstLaunch(false)
            showStep(WizardStep.STEP1_CONFIG)
        }

        // Step 1 button: validate & continue
        binding.buttonValidateAndContinue.setOnClickListener {
            validateAndContinue()
        }

        // Small helper link: open How to Use page
        binding.buttonLearnHowToObtain.setOnClickListener {
            openHowToUsePage()
        }

        // Step 2: request permission
        binding.buttonRequestPermission.setOnClickListener {
            requestPermissionIfNeeded()
        }

        // Step 3: forwarding switch
        binding.switchForwarding.setOnCheckedChangeListener { view, isChecked ->
            if (!view.isPressed) return@setOnCheckedChangeListener
            settingsRepository.setForwardingEnabled(isChecked)
            updateLastStatus()

            val message = if (isChecked) {
                getString(R.string.forwarding_enabled)
            } else {
                getString(R.string.forwarding_disabled)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Step 3: test message
        binding.buttonTestMessage.setOnClickListener {
            sendTestMessage()
        }
    }

    private fun showStep(step: WizardStep) {
        binding.containerStep0.isVisible = step == WizardStep.STEP0_WELCOME
        binding.containerStep1.isVisible = step == WizardStep.STEP1_CONFIG
        binding.containerStep2.isVisible = step == WizardStep.STEP2_PERMISSION
        binding.containerStep3.isVisible = step == WizardStep.STEP3_SUMMARY

        // Update step 3 status texts whenever we enter it
        if (step == WizardStep.STEP3_SUMMARY) {
            updateSummaryStatuses()
        }
    }

    private fun updateSummaryStatuses() {
        val smsGranted = hasSmsPermission()
        binding.textStatusSmsPermission.text = if (smsGranted) {
            getString(R.string.sms_permission_granted)
        } else {
            getString(R.string.sms_permission_not_granted)
        }

        val hasSettings = settingsRepository.loadSettings() != null
        binding.textStatusTelegram.text = if (hasSettings) {
            getString(R.string.telegram_configured)
        } else {
            getString(R.string.telegram_not_configured)
        }
    }

    private fun validateAndContinue() {
        val token = binding.inputToken.text?.toString()?.trim().orEmpty()
        val chatId = binding.inputChatId.text?.toString()?.trim().orEmpty()

        var hasError = false

        if (token.isBlank()) {
            binding.inputLayoutToken.error = getString(R.string.hint_api_token)
            hasError = true
        } else {
            binding.inputLayoutToken.error = null
        }

        if (chatId.isBlank()) {
            binding.inputLayoutChatId.error = getString(R.string.hint_chat_id)
            hasError = true
        } else {
            binding.inputLayoutChatId.error = null
        }

        if (hasError) return

        lifecycleScope.launch {
            binding.buttonValidateAndContinue.isEnabled = false
            try {
                val result = telegramForwarder.sendMessage(
                    token = token,
                    chatId = chatId,
                    message = getString(R.string.test_message_body)
                )

                if (result.isSuccess) {
                    // Save settings only when validation passes
                    settingsRepository.saveSettings(token, chatId)
                    val successText = timeFormatter.format(Date()) + " " + getString(R.string.test_message_success)
                    settingsRepository.saveLastForwardStatus(successText)

                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.step1_completed),
                        Toast.LENGTH_SHORT
                    ).show()

                    updateLastStatus()

                    // Proceed to next step depending on permission state
                    if (hasSmsPermission()) {
                        showStep(WizardStep.STEP3_SUMMARY)
                    } else {
                        showStep(WizardStep.STEP2_PERMISSION)
                    }
                } else {
                    val errorMessage = result.exceptionOrNull()?.localizedMessage ?: "unknown error"
                    val errorText = timeFormatter.format(Date()) + " " + getString(
                        R.string.test_message_error,
                        errorMessage
                    )
                    settingsRepository.saveLastForwardStatus(errorText)
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                    updateLastStatus()
                }
            } finally {
                binding.buttonValidateAndContinue.isEnabled = true
            }
        }
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_confirm_title)
            .setMessage(R.string.reset_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                resetApp()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resetApp() {
        // Clear stored settings and status
        settingsRepository.setFirstLaunch(true)
        settingsRepository.saveSettings("", "")
        settingsRepository.saveLastForwardStatus("")
        settingsRepository.setForwardingEnabled(false)

        binding.inputToken.setText("")
        binding.inputChatId.setText("")
        binding.switchForwarding.isChecked = false
        updateLastStatus()
        showStep(WizardStep.STEP0_WELCOME)
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionIfNeeded() {
        if (hasSmsPermission()) {
            // Already granted, move on to summary
            showStep(WizardStep.STEP3_SUMMARY)
        } else {
            permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        }
    }

    private fun updatePermissionUi() {
        val granted = hasSmsPermission()

        val statusText = if (granted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }

        binding.textPermission.text =
            getString(R.string.label_permission_status, statusText)
    }

    private fun updateLastStatus() {
        val forwardingEnabled = settingsRepository.isForwardingEnabled()
        if (binding.switchForwarding.isChecked != forwardingEnabled) {
            binding.switchForwarding.isChecked = forwardingEnabled
        }

        val status = settingsRepository.loadLastForwardStatus()
        binding.textPreview.text = status ?: getString(R.string.label_not_configured)
    }

    private fun sendTestMessage() {
        val settings = settingsRepository.loadSettings()
        if (settings == null) {
            Toast.makeText(this, getString(R.string.label_not_configured), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.buttonTestMessage.isEnabled = false
            try {
                val result = telegramForwarder.sendMessage(
                    token = settings.apiToken,
                    chatId = settings.chatId,
                    message = getString(R.string.test_message_body)
                )

                if (result.isSuccess) {
                    val successText = timeFormatter.format(Date()) + " " + getString(R.string.test_message_success)
                    settingsRepository.saveLastForwardStatus(successText)
                    Toast.makeText(this@MainActivity, successText, Toast.LENGTH_SHORT).show()
                } else {
                    val errorText = timeFormatter.format(Date()) + " " + getString(
                        R.string.test_message_error,
                        result.exceptionOrNull()?.localizedMessage ?: "unknown error"
                    )
                    settingsRepository.saveLastForwardStatus(errorText)
                    Toast.makeText(this@MainActivity, errorText, Toast.LENGTH_LONG).show()
                }
                updateLastStatus()
            } finally {
                binding.buttonTestMessage.isEnabled = true
            }
        }
    }
}
