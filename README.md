# Media HUB

Native Android app built with Kotlin and Jetpack Compose.

- White + Mint Green UI matching the approved Media HUB home screen
- Native navigation: Home, Downloads, Player, History, Settings
- Share-to-app and paste-link support
- Direct MP4 download using Android DownloadManager for URLs the user is authorized to download
- Android 12+ compatible build pipeline via GitHub Actions

## Platform policy
A social post URL is not a direct media file. This app does not bypass DRM, login walls, or platform restrictions. YouTube content should be saved through YouTube's official download/offline features. Facebook, Instagram and Threads integrations must use platform-authorized APIs or download mechanisms.

## Build
GitHub Actions builds a debug APK on each push. Open the `Android APK Build` workflow and download the `Media-HUB-debug-apk` artifact.
