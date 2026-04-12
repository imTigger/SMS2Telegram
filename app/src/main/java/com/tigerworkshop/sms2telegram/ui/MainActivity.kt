package com.tigerworkshop.sms2telegram.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tigerworkshop.sms2telegram.R
import com.tigerworkshop.sms2telegram.data.PendingMessageOutbox
import com.tigerworkshop.sms2telegram.data.SettingsRepository
import com.tigerworkshop.sms2telegram.data.StatusUpdateBus
import com.tigerworkshop.sms2telegram.data.TelegramChatInfo
import com.tigerworkshop.sms2telegram.data.TelegramDeliveryWorker
import com.tigerworkshop.sms2telegram.data.TelegramForwarder
import com.tigerworkshop.sms2telegram.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var pendingMessageOutbox: PendingMessageOutbox
    private val telegramForwarder = TelegramForwarder()
    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ", Locale.US)

    private enum class WizardStep { STEP0_WELCOME, STEP1_CONFIG, STEP2_PERMISSION, STEP3_SUMMARY }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.permission_required_message),
                    Toast.LENGTH_LONG
                ).show()

                // Check if we should show rationale (user can still be asked)
                // If false, it means "don't ask again" was selected
                // Only available on API 23+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)) {
                    showSmsPermissionDeniedDialog()
                }
            }
        }

    private val phoneStatePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // Permission granted, enable the feature
                settingsRepository.setShowSimNameEnabled(true)
                binding.switchShowSimName.isChecked = true
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.phone_state_permission_granted),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // Permission denied, keep toggle off
                binding.switchShowSimName.isChecked = false
                settingsRepository.setShowSimNameEnabled(false)

                // Check if we should show rationale (user can still be asked)
                // If false, it means "don't ask again" was selected
                // Only available on API 23+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
                    showPermissionDeniedDialog()
                }
            }
            updateSummaryStatuses()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        settingsRepository = SettingsRepository(this)
        pendingMessageOutbox = PendingMessageOutbox(this)

        bindListeners()
        updatePermissionUi()
        updateLastStatus()
        observeStatusUpdates()
        initWizardInitialStep()

        binding.inputToken.doAfterTextChanged {
            val hasToken = !it.isNullOrBlank()
            binding.buttonGetChatId.isEnabled = hasToken
            if (hasToken) {
                binding.inputLayoutToken.error = null
            }
        }
        binding.inputChatId.doAfterTextChanged {
            if (!it.isNullOrBlank()) {
                binding.inputLayoutChatId.error = null
            }
        }
    }

    private fun initWizardInitialStep() {
        val settings = settingsRepository.loadSettings()
        val hasSettings = settings != null
        if (hasSettings) {
            binding.inputToken.setText(settings!!.apiToken)
            binding.inputChatId.setText(settings.chatId)
        }
        binding.buttonGetChatId.isEnabled = !binding.inputToken.text.isNullOrBlank()

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

        // Refresh permission status in case user granted permission in settings
        if (binding.containerStep3.isVisible) {
            updateSummaryStatuses()
        }
    }

    private fun observeStatusUpdates() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                StatusUpdateBus.updates.collect {
                    updateLastStatus()
                }
            }
        }
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

        binding.buttonGetChatId.setOnClickListener {
            fetchChatIds()
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

            if (isChecked && pendingMessageOutbox.pendingCount() > 0) {
                TelegramDeliveryWorker.enqueue(this)
            }
        }

        // Step 3: show SIM name switch
        binding.switchShowSimName.setOnCheckedChangeListener { view, isChecked ->
            if (!view.isPressed) return@setOnCheckedChangeListener

            if (isChecked && !hasPhoneStatePermission()) {
                binding.switchShowSimName.isChecked = false
                showSimNamePermissionExplanationDialog()
            } else {
                settingsRepository.setShowSimNameEnabled(isChecked)
            }
        }

        // Step 4: test message
        binding.buttonTestMessage.setOnClickListener {
            sendTestMessage()
        }

        binding.buttonRetryPending.setOnClickListener {
            retryPendingMessages()
        }

        binding.textPendingQueue.setOnClickListener {
            showPendingMessagesDialog()
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
            updateLastStatus()
        }
    }

    private fun updateSummaryStatuses() {
        val smsGranted = hasSmsPermission()
        binding.textStatusSmsPermission.text = if (smsGranted) {
            getString(R.string.sms_permission_granted)
        } else {
            getString(R.string.sms_permission_not_granted)
        }

        val phoneStateGranted = hasPhoneStatePermission()
        binding.textStatusPhoneStatePermission.text = if (phoneStateGranted) {
            getString(R.string.phone_state_permission_granted)
        } else {
            getString(R.string.phone_state_permission_not_granted)
        }

        // Update show SIM name toggle state
        val showSimNameEnabled = settingsRepository.isShowSimNameEnabled()
        binding.switchShowSimName.isChecked = showSimNameEnabled && phoneStateGranted

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
            binding.inputLayoutChatId.error = getString(R.string.error_chat_id_required)
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
                    if (pendingMessageOutbox.pendingCount() > 0 && settingsRepository.isForwardingEnabled()) {
                        TelegramDeliveryWorker.enqueue(this@MainActivity)
                    }

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

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_denied_title)
            .setMessage(R.string.permission_denied_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSimNamePermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sim_name_permission_explanation_title)
            .setMessage(R.string.sim_name_permission_explanation_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSmsPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sms_permission_denied_title)
            .setMessage(R.string.sms_permission_denied_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetApp() {
        // Clear stored settings and status
        settingsRepository.setFirstLaunch(true)
        settingsRepository.saveSettings("", "")
        settingsRepository.saveLastForwardStatus("")
        settingsRepository.setForwardingEnabled(false)
        settingsRepository.setShowSimNameEnabled(false)
        pendingMessageOutbox.clear()
        TelegramDeliveryWorker.cancel(this)

        binding.inputToken.setText("")
        binding.inputChatId.setText("")
        binding.switchForwarding.isChecked = false
        binding.switchShowSimName.isChecked = false
        binding.buttonGetChatId.isEnabled = false
        binding.buttonGetChatId.text = getString(R.string.button_get_chat_id)
        updateLastStatus()
        showStep(WizardStep.STEP0_WELCOME)
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
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

        val pendingCount = pendingMessageOutbox.pendingCount()
        val status = settingsRepository.loadLastForwardStatus()?.takeIf { it.isNotBlank() }
        binding.textPendingQueue.text = getString(R.string.pending_queue_count, pendingCount)
        binding.textPreview.text = status ?: getString(R.string.label_not_configured)
        binding.buttonRetryPending.isEnabled =
            pendingCount > 0 && forwardingEnabled && settingsRepository.loadSettings() != null
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

    private fun retryPendingMessages() {
        val pendingCount = pendingMessageOutbox.pendingCount()
        if (pendingCount == 0) {
            Toast.makeText(this, getString(R.string.retry_pending_none), Toast.LENGTH_SHORT).show()
            updateLastStatus()
            return
        }

        if (!settingsRepository.isForwardingEnabled()) {
            Toast.makeText(this, getString(R.string.retry_pending_enable_forwarding), Toast.LENGTH_LONG).show()
            updateLastStatus()
            return
        }

        if (settingsRepository.loadSettings() == null) {
            Toast.makeText(this, getString(R.string.label_not_configured), Toast.LENGTH_SHORT).show()
            updateLastStatus()
            return
        }

        settingsRepository.saveLastForwardStatus(
            timeFormatter.format(Date()) + " " + getString(R.string.retry_pending_scheduled, pendingCount)
        )
        TelegramDeliveryWorker.enqueue(this)
        updateLastStatus()
        Toast.makeText(this, getString(R.string.retry_pending_started), Toast.LENGTH_SHORT).show()
    }

    private fun showPendingMessagesDialog() {
        val pendingMessages = pendingMessageOutbox.listAll()
        val dialogMessage = if (pendingMessages.isEmpty()) {
            getString(R.string.pending_messages_empty)
        } else {
            pendingMessages.mapIndexed { index, pendingMessage ->
                buildString {
                    appendLine(
                        getString(
                            R.string.pending_message_entry_title,
                            index + 1,
                            pendingMessage.sender,
                            timeFormatter.format(Date(pendingMessage.queuedAtMillis))
                        )
                    )
                    append(pendingMessage.message)
                }
            }.joinToString(separator = "\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pending_messages_dialog_title, pendingMessages.size))
            .setMessage(dialogMessage)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun fetchChatIds() {
        val token = binding.inputToken.text?.toString()?.trim().orEmpty()
        if (token.isBlank()) {
            binding.inputLayoutToken.error = getString(R.string.hint_api_token)
            return
        }

        lifecycleScope.launch {
            toggleChatFetchUi(true)
            try {
                val result = telegramForwarder.fetchUpdates(token)
                if (result.isSuccess) {
                    val chats = result.getOrNull().orEmpty()
                    if (chats.isEmpty()) {
                        Toast.makeText(this@MainActivity, getString(R.string.chat_id_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        showChatSelectionDialog(chats)
                    }
                } else {
                    val message = result.exceptionOrNull()?.localizedMessage ?: "unknown error"
                    Toast.makeText(this@MainActivity, getString(R.string.chat_id_fetch_error, message), Toast.LENGTH_LONG).show()
                }
            } finally {
                toggleChatFetchUi(false)
            }
        }
    }

    private fun toggleChatFetchUi(loading: Boolean) {
        binding.buttonGetChatId.isEnabled = !loading
        binding.buttonGetChatId.text = if (loading) {
            getString(R.string.button_get_chat_id_loading)
        } else {
            getString(R.string.button_get_chat_id)
        }
    }

    private fun showChatSelectionDialog(chats: List<TelegramChatInfo>) {
        val entries = chats.map { chat ->
            val labelName = when {
                !chat.title.isNullOrBlank() -> chat.title
                !chat.firstName.isNullOrBlank() -> chat.firstName
                else -> chat.id.toString()
            }
            val usernameSuffix = if (!chat.username.isNullOrBlank()) " (@${chat.username})" else ""
            "$labelName$usernameSuffix: ID = ${chat.id}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.chat_id_selection_title)
            .setItems(entries) { _, which ->
                val selected = chats[which]
                binding.inputChatId.setText(selected.id.toString())
                binding.inputLayoutChatId.error = null
                Toast.makeText(this, getString(R.string.chat_id_selected), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
