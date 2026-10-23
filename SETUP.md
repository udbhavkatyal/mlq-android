# MLQ Work for Android · One-Time Setup

About 30–45 minutes, once. After this, the app updates itself: every MLQ update you push from the PC reaches phones too, and a new app build is only needed if the app shell itself changes (rare).

You'll need these files from Claude:

- **mlq-android.zip** — the app's code (goes to GitHub)
- **MLQ Android Signing Key.zip** — the key that proves each new version is yours. Keep a copy somewhere private (e.g. your password manager). If it's lost, phones can't update the app and everyone has to reinstall.
- **mlq-push.ts** and **mlq-google.ts** — two Supabase functions
- **MLQ Web.zip** — from the latest MLQ update

Do the steps in order.

---

## 1 · Push the latest MLQ update

1. Install **MLQ Work Update.zip** from your PC app (Settings → Updates → Push To Everyone), as usual.
2. Cloudflare → Workers & Pages → **mlq-work** → Create deployment → upload **MLQ Web.zip**.

## 2 · Google sign-in on phones

1. **Supabase** → Edge Functions → **mlq-google** → replace its code with **mlq-google.ts** → Deploy.
2. **Google Cloud Console** (console.cloud.google.com, the project MLQ's Google sign-in uses) → APIs & Services → Credentials → the OAuth client used by MLQ (ID starts `733293143939-`) → **Authorized redirect URIs** → Add URI:
   `https://mlq-work.pages.dev/google-back.html` → Save.

## 3 · Firebase (phone notifications)

1. Go to **console.firebase.google.com** → Create a project → name it **MLQ Work** → turn Google Analytics **off** → Create.
2. On the project page, click the **Android** icon (Add app):
   - Package name: `gg.maxlevel.mlqwork` (exactly)
   - App nickname: MLQ Work
   - Register app → **Download google-services.json** → keep it → click Next / Continue to the end (skip the SDK steps).
3. Gear icon → **Project settings** → **Service accounts** → **Generate new private key** → Generate. A second JSON file downloads. Treat it like a password.
4. **Supabase** → Edge Functions → **Secrets** → Add new secret:
   - Name: `FCM_SERVICE_ACCOUNT`
   - Value: open the service-account JSON (from step 3) in Notepad, copy **all** of it, paste → Save.
5. **Supabase** → Edge Functions → Deploy a new function → name **mlq-push** → paste the code from **mlq-push.ts** → in the function's settings turn **Verify JWT off** → Deploy.

## 4 · GitHub (builds the app)

1. **github.com/new** → Repository name: `mlq-android` → **Public** → Create repository.
   Public is needed so phones can download new versions and check for updates without a GitHub login. Nothing secret is in the code: the key and Firebase file go into hidden Secrets below.
2. On the new repo page click **uploading an existing file**. Unzip **mlq-android.zip**, open the folder, select **everything inside it** (including the `.github` folder) and drag it in → **Commit changes**.
3. Check the repo shows a `.github` folder. If it doesn't: Add file → Create new file → name it `.github/workflows/android.yml` → paste the contents of that file from the zip → Commit.
4. Repo → **Settings** → Secrets and variables → **Actions** → **New repository secret**, three times:

   | Name | Value |
   |---|---|
   | `MLQ_KEYSTORE_B64` | everything in `keystore-base64.txt` (from the signing key zip) |
   | `MLQ_KEYSTORE_PASSWORD` | the password in `password.txt` (same zip) |
   | `GOOGLE_SERVICES_JSON` | everything in **google-services.json** (from Firebase step 2) |

5. Repo → **Actions** tab → enable workflows if asked → **Build Android APK** → **Run workflow**. It takes about 8–10 minutes. A green tick = done.
   If it shows a red cross, open it, copy the red error lines and send them to Claude.
6. Repo → **Releases** (right side) → the newest one has **MLQ-Work.apk**.

## 5 · Tell MLQ where the app lives

MLQ (PC) → Settings → **Updates** → **Android App** → type `your-github-name/mlq-android` → press Enter.
**Copy APK Link** now gives a link that always points to the newest build. Send it to Faizan.

## 6 · Install on a phone

1. Open the APK link on the phone → download → open the file.
2. Android asks to allow installs from the browser → **Allow** → Install.
3. Open **MLQ Work** → sign in → the **Set Up MLQ On This Phone** screen appears → tap **Turn On** for Notifications (Google and Trello are usually already ticked; if not, tap them too).
4. **Xiaomi / Redmi / POCO / Realme / OnePlus / Vivo / Oppo phones:** these stop apps in the background. Long-press the MLQ Work icon → App info → **Battery** → **No restrictions / Unrestricted** (and **Autostart: On** where it exists). Without this, notifications can arrive late.

## 7 · Test (with Claude)

- You at your PC and active → someone messages you → **no** phone buzz, PC shows it.
- Lock the PC / walk away for 2+ minutes → someone messages you → PC **and** phone notify.
- Tap the phone notification → it opens that chat.
- A ZAP → buzzes on the phone even if you're at your PC.

## Later: a new app build

Only when Claude says the app shell changed: upload the changed files to the repo (or re-upload the zip's contents) → the build runs by itself → phones see "A New Version Of The App" and tap Download.
