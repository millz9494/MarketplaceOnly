# Marketplace Only (Android)

A small Android WebView app that opens Facebook Marketplace and blocks normal navigation into the rest of Facebook.

## What it allows
- Facebook sign-in and security/checkpoint pages
- Facebook Marketplace home, search, categories and listings
- Marketplace seller pages that live under `/marketplace/...`
- A Messenger thread only when that thread is opened directly from Marketplace

## What it blocks
- Facebook Feed/Home
- Reels / Watch
- Groups
- Pages outside Marketplace
- General profiles outside Marketplace
- General Messenger inbox / arbitrary threads

## Build in Android Studio
1. Install the current stable Android Studio.
2. Open the `MarketplaceOnly` folder.
3. Let Android Studio install Android SDK 35 if requested and sync Gradle.
4. Build > Build App Bundle(s) / APK(s) > Build APK(s).
5. Install the APK on your Android phone.

## Important limitation
Facebook controls its website and can change routes, login flows, anti-bot checks, or WebView support at any time. If a required Marketplace page gets blocked after a Facebook change, update the URL allow-list in `MainActivity.kt` rather than opening all of Facebook.

This project does not collect or save a Facebook password. Authentication is handled on Facebook's own web pages inside the WebView, with Facebook cookies retained by Android's WebView cookie store.
