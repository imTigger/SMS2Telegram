# Minimalist Android SMS to Telegram Forwarder

Free and open source under the MIT license. Supports Android 5.0+ without requiring Google Play services.

Forward incoming SMS to your own Telegram bot and selected chats. SMS may contain sensitive information; review the code and choose recipients carefully. Messages are transmitted to Telegram through its Bot API.

![Screenshots](docs/images/cover.png)

## Setup

1. Open [@BotFather](https://t.me/botfather) in Telegram, use `/newbot`, and follow its instructions.
2. Copy the bot token into **Telegram Bot API Token**. Keep the token private; anyone with it can control the bot.
3. Each private recipient must open the bot and tap **Start**. Enter their chat IDs or use **Find Chat IDs** to select several chats with checkboxes.
4. Tap **Apply**, then **Validate and Continue**. Validation sends a test message to every destination and saves settings only if all succeed.
5. Configure **Allowed senders** before granting SMS permission if you want restricted forwarding. Filtering defaults off and otherwise permits all senders.

Use **Edit Telegram settings** to change the bot token or recipients. Chat IDs may be separated by commas, semicolons, spaces, or new lines. Duplicate IDs are removed; negative group IDs and `@channel` usernames are supported. The token is masked by default, with a visibility toggle.

## Sender filtering

Open **Allowed senders** from the three-dot menu or forwarding status screen. Add or remove sender names and phone numbers, then enable **Only forward allowed senders**. Changes save automatically.

**Add sender** offers **Open Messages**, **Enter manually**, or **Cancel**. Check/copy the originating sender in Messages, return, enter or paste it, and tap **Save sender**. This flow does not read the inbox or automatically import a conversation.

- Matching uses the complete originating address, ignoring case and surrounding whitespace. Phone numbers must use the same country code and formatting as the SMS sender.
- No message-body, substring, wildcard, contact-name, or country-code matching is performed.
- An enabled empty list forwards nothing. Disabling the filter requires confirmation and permits all senders.
- Filtering happens before queuing and is rechecked before each recipient delivery. A request already in flight cannot be recalled.
- Manual test messages are not filtered. Android SMS and OTP restrictions still apply.

## Queued deliveries

Recipients are recorded when an SMS arrives. Successful deliveries are tracked separately, so failed destinations do not block others and retries skip recorded successes. Removing recipients cancels their pending deliveries; adding recipients does not share messages already assigned to other destinations. Older queue entries bind to the existing recipient list before settings change or on their first delivery attempt.

A process failure between Telegram acknowledging a message and the app recording success can still cause a duplicate.

## Build and test

Import the project in Android Studio, or use JDK 17, Android SDK Platform 36, and Gradle 8.13:

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

The Gradle wrapper JAR is not included; use installed Gradle or restore the matching wrapper JAR before running `./gradlew`. GitHub Actions runs tests, lint, and a debug build. Tests use fictional sender names, numbers, and chat IDs; they do not configure production recipients.

Debug APKs are development builds, not production releases, and cannot update an installation signed with another key. Device testing should cover allowed and blocked senders, app restarts, offline retries, and multiple recipients.

## Downloads

[Releases](https://github.com/imTigger/SMS2Telegram/releases/latest) · [Google Play](https://play.google.com/store/apps/details?id=com.tigerworkshop.sms2telegram)
