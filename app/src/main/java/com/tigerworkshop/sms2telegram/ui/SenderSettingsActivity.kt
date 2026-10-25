package com.tigerworkshop.sms2telegram.ui

import android.os.Bundle
import android.content.Intent
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.core.view.isVisible
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.tigerworkshop.sms2telegram.R
import com.tigerworkshop.sms2telegram.data.SettingsRepository
import com.tigerworkshop.sms2telegram.databinding.ActivitySenderSettingsBinding

class SenderSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySenderSettingsBinding
    private lateinit var repository: SettingsRepository

    private var returningFromMessages = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySenderSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = SettingsRepository(this)
        binding.toolbar.setNavigationOnClickListener { finish() }
        render()
        returningFromMessages = savedInstanceState?.getBoolean("returning_from_messages") ?: false
        if (savedInstanceState?.getBoolean("editing_sender") == true) showSenderInput()
        binding.switchSenderFilter.setOnCheckedChangeListener { _, checked ->
            val current = repository.loadSenderAllowlist()
            if (current.enabled == checked) return@setOnCheckedChangeListener
            if (checked) {
                repository.saveSenderAllowlist(current.copy(enabled = true))
                render()
            } else {
                // Keep filtering until the user confirms allowing all senders.
                binding.switchSenderFilter.isChecked = true
                AlertDialog.Builder(this)
                    .setTitle(R.string.sender_disable_title)
                    .setMessage(R.string.sender_disable_message)
                    .setPositiveButton(R.string.sender_disable_confirm) { _, _ ->
                        repository.saveSenderAllowlist(repository.loadSenderAllowlist().copy(enabled = false))
                        render()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        binding.inputSender.doAfterTextChanged { binding.inputLayoutSender.error = null }
        binding.buttonAddSender.setOnClickListener {
            if (binding.inputLayoutSender.isVisible) addSender() else showAddSenderOptions()
        }
    }

    override fun onResume() {
        super.onResume()
        if (returningFromMessages) {
            returningFromMessages = false
            showSenderInput()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("returning_from_messages", returningFromMessages)
        outState.putBoolean("editing_sender", binding.inputLayoutSender.isVisible)
        super.onSaveInstanceState(outState)
    }

    private fun showAddSenderOptions() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sender_add)
            .setMessage(R.string.sender_messages_explanation)
            .setPositiveButton(R.string.sender_open_messages) { _, _ -> openMessages() }
            .setNeutralButton(R.string.sender_enter_manually) { _, _ -> showSenderInput() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openMessages() {
        returningFromMessages = true
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
        } catch (_: ActivityNotFoundException) {
            messagesUnavailable()
        } catch (_: SecurityException) {
            messagesUnavailable()
        }
    }

    private fun messagesUnavailable() {
        returningFromMessages = false
        showSenderInput()
        Toast.makeText(this, R.string.sender_messages_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun showSenderInput() {
        binding.inputLayoutSender.isVisible = true
        binding.buttonAddSender.setText(R.string.sender_save)
        binding.inputSender.requestFocus()
    }

    private fun addSender() {
        val sender = binding.inputSender.text?.toString()?.trim().orEmpty()
        val current = repository.loadSenderAllowlist()
        when {
            sender.isEmpty() -> binding.inputLayoutSender.error = getString(R.string.sender_blank_error)
            current.senders.any { it.equals(sender, ignoreCase = true) } ->
                binding.inputLayoutSender.error = getString(R.string.sender_duplicate_error)
            else -> {
                repository.saveSenderAllowlist(current.copy(senders = current.senders + sender))
                binding.inputSender.text?.clear()
                binding.inputLayoutSender.isVisible = false
                binding.buttonAddSender.setText(R.string.sender_add)
                render()
            }
        }
    }

    private fun render() {
        val current = repository.loadSenderAllowlist()
        binding.switchSenderFilter.isChecked = current.enabled
        binding.textFilterStatus.text = getString(
            when {
                !current.enabled -> R.string.sender_filter_off
                current.senders.isEmpty() -> R.string.sender_filter_empty
                else -> R.string.sender_filter_active
            }, current.senders.size
        )
        binding.senderList.removeAllViews()
        if (current.senders.isEmpty()) {
            binding.senderList.addView(TextView(this).apply { setText(R.string.sender_list_empty) })
        }
        current.senders.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { sender ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = sender
                textSize = 16f
                setTextIsSelectable(true)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                setText(R.string.sender_remove)
                contentDescription = getString(R.string.sender_remove_description, sender)
                setOnClickListener {
                    val latest = repository.loadSenderAllowlist()
                    repository.saveSenderAllowlist(latest.copy(senders = latest.senders - sender))
                    render()
                }
            })
            binding.senderList.addView(row)
        }
    }
}
