# 1MABANI MDM — Android Agent

Native Device Owner app for 1MABANI's custom MDM system. Talks to the
backend at `mdm.1mabanihr.com`.

## One-time setup

1. Run the **"Generate release keystore (run once)"** workflow from the
   Actions tab. Download its artifact, and add two repository secrets
   (Settings → Secrets and variables → Actions):
   - `KEYSTORE_BASE64` — paste the content of `mdm-release.jks.b64`
   - `KEYSTORE_PASSWORD` — `Azerty002007!MDM`
2. Push to `main` (or run **"Build Android APK"** manually) — this builds,
   signs, and computes the provisioning checksum. Download the
   `mdm-app-release` artifact: `app-signed.apk` + `app-meta.json`.
3. Upload both files to the VPS at `/opt/mdm/app/download/app.apk` and
   `/opt/mdm/app/download/app-meta.json` (same folder Caddy already serves
   at `mdm.1mabanihr.com/download/...`).

## Enrolling a device

- **Zero-touch (QR, for a factory-reset phone):** from the dashboard's
  device page, open its QR code and scan it on the welcome screen (tap the
  screen 6 times to reveal the QR scanner).
- **Manual (for quick testing, already-set-up phone):** install the APK,
  open it, paste the device's enroll token, tap "تسجيل الجهاز". For full
  Device Owner capabilities (app hiding, etc.) the phone still needs to be
  provisioned with no Google account signed in yet; use
  `adb shell dpm set-device-owner com.mabani.mdm/.AdminReceiver` on a
  freshly wiped test device instead of the in-app button for full testing.
