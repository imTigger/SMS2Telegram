package com.tigerworkshop.sms2telegram.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.tigerworkshop.sms2telegram.R
import com.tigerworkshop.sms2telegram.data.SettingsRepository
import com.tigerworkshop.sms2telegram.data.TelegramForwarder
import com.tigerworkshop.sms2telegram.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepository: SettingsRepository
    private val telegramForwarder = TelegramForwarder()
    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ")

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
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        settingsRepository = SettingsRepository(this)

        populateFields()
        bindListeners()
        updatePermissionUi()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_how_to_use -> {
                val uri = Uri.parse("https://github.com/imTigger/SMS2Telegram/tree/main?tab=readme-ov-file#how-to-use")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "No browser application can handle this action", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        updateLastStatus()
    }

    private fun populateFields() {
        val settings = settingsRepository.loadSettings()
        if (settings != null) {
            binding.inputToken.setText(settings.apiToken)
            binding.inputChatId.setText(settings.chatId)
        }
        binding.switchForwarding.isChecked = settingsRepository.isForwardingEnabled()
        updateLastStatus()
    }

    private fun bindListeners() {
        binding.buttonSave.setOnClickListener {
            saveSettings()
        }
        binding.buttonRequestPermission.setOnClickListener {
            requestPermissionIfNeeded()
        }
        binding.switchForwarding.setOnCheckedChangeListener { view, isChecked ->
            if (!view.isPressed) return@setOnCheckedChangeListener
            settingsRepository.setForwardingEnabled(isChecked)
            updateLastStatus()
        }
        binding.buttonTestMessage.setOnClickListener {
            sendTestMessage()
        }
    }

    private fun saveSettings() {
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

        settingsRepository.saveSettings(token, chatId)
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
    }

    private fun requestPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                getString(R.string.permission_already_granted),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        }
    }

    private fun updatePermissionUi() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val statusText = if (granted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }

        binding.textPermission.text =
            getString(R.string.label_permission_status, statusText)
        binding.buttonRequestPermission.isVisible = !granted
    }

    private fun updateLastStatus() {
        val forwardingEnabled = settingsRepository.isForwardingEnabled()
        if (binding.switchForwarding.isChecked != forwardingEnabled) {
            binding.switchForwarding.isChecked = forwardingEnabled
        }

        if (!forwardingEnabled) {
            binding.textPreview.text = getString(R.string.forwarding_disabled_status)
            return
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
