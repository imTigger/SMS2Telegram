# Privacy Policy

**Last updated:** November 28, 2025

This Privacy Policy describes how **Minimalist Android SMS to Telegram Forwarder** (the "App") handles your information. The App is developed by **[DEVELOPER NAME]** ("we", "us", or "our").

The App is a minimalist tool that forwards SMS messages received on your Android device to your own Telegram Bot chat.

## 1. Data We Collect

We do **not** collect, store, or share any personal data on our own servers. The App has:

- **No registration**
- **No analytics or tracking**
- **No ads or third-party SDKs**

All processing happens locally on your device, except when you explicitly forward SMS content to Telegram using your own bot configuration.

### 1.1 SMS Messages

To perform its core function, the App requires access to SMS:

- When an SMS is received on your device, the App can read the message content, sender number, and timestamp.
- The message is **immediately forwarded** from your device to the Telegram Bot API using:
  - The **Bot Token** you configured.
  - The **Chat ID** you configured.
- The App does **not** upload SMS data to any server controlled by us.

> **Important:** SMS messages and one-time passwords (OTP) can be highly sensitive. By using this App, you understand that any forwarded SMS content will be sent to Telegram servers under your Telegram account/Bot configuration.

### 1.2 Telegram Bot Token and Chat ID

You manually enter:

- Telegram **Bot Token**
- Telegram **Chat ID**

These values:

- Are stored **only on your device**, in the App's private storage.
- Are used only to send messages to your chosen Telegram chat.
- Are **never** sent to us or any third party other than Telegram (as part of sending messages via the official Telegram Bot API).

## 2. How We Use the Data

The App uses the information described above **only** to:

- Detect incoming SMS messages (with your permission).
- Format the SMS content.
- Send the formatted content to your configured Telegram Bot chat.

We do **not** use your data for:

- Advertising
- Analytics or profiling
- Selling or sharing with third parties

## 3. Data Storage and Retention

- SMS messages are **not** stored by the App beyond what is already stored by the Android SMS system.
- The App does not create its own long-term database of your messages.
- Telegram Bot Token and Chat ID are stored locally on your device for as long as you keep them configured or until you clear the App data or uninstall the App.

## 4. Permissions

The App requests the following Android permissions:

- **RECEIVE_SMS / READ_SMS**: required to detect and read incoming SMS messages for the purpose of forwarding them to Telegram.
- **INTERNET**: required to send the SMS content to the Telegram Bot API.

The App does **not** request any unnecessary permissions.

## 5. Third Parties

The App communicates only with:

- **Telegram Bot API**: to send messages to the chat you configured.

Please refer to Telegram's own privacy policy to understand how Telegram handles data transmitted via the Bot API.

We do not integrate any third-party analytics, ad networks, or tracking services.

## 6. Children's Privacy

The App is not directed to children under 13. We do not knowingly collect personal information from children. Because we do not operate any backend or user accounts, we have no technical means to identify users as children.

## 7. Security

- Your data is processed locally on your device.
- Bot Token and Chat ID are stored in the App's private storage.
- You are responsible for keeping your Bot Token and device secure. Anyone with access to your Bot Token could send messages as your bot.

## 8. Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Any changes will be reflected by updating the "Last updated" date at the top of this document. You should review this page periodically for any changes.

## 9. Contact Us

If you have any questions about this Privacy Policy or how the App handles data, contact:

- **Developer:** Tiger-Workshop
- **Email:** sms2telegram[ at ]tiger-workshop.com

