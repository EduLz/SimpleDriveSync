# Privacy Policy for SimpleDriveSync

**Effective Date:** July 28, 2026

SimpleDriveSync ("we", "our", or "us") respects your privacy. This Privacy Policy explains how our open-source Android application handles user data.

## 1. Information Collection and Use
SimpleDriveSync is designed as a local client tool for synchronizing files between your personal Google Drive account and your local Android device storage.
* **No Server Storage:** SimpleDriveSync does NOT collect, store, transmit, or share your personal data, credentials, or Google Drive files to external servers. All data transfers occur directly between your device and Google Drive servers via HTTPS.
* **Google OAuth Data:** When you sign in using Google OAuth 2.0, the app receives a temporary access token strictly used to access Google Drive APIs on your behalf.
* **Data Scopes:** We request `https://www.googleapis.com/auth/drive.readonly` solely to list and download files that you choose to synchronize.

## 2. Data Security
All credentials and access tokens are stored securely on your local device using Android DataStore Encrypted storage. We do not sell or share any user data with third parties.

## 3. Contact Us
If you have questions about this Privacy Policy, please open an issue at:
`https://github.com/EduLz/SimpleDriveSync`
