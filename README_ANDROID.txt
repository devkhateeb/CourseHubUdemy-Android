CourseHub Udemy Android project
================================

This is the Android source project (not a prebuilt APK). The GitHub Actions workflow was updated to use the Android SDK preinstalled on GitHub's Ubuntu runner and avoid the failing android-actions/setup-android sdkmanager update step. It includes a native dashboard, manual scan/post, per-course manual posting, Telegram bot/channel settings, local duplicate history, optional Supabase shared history, and scheduled background scanning/posting.

BUILD AN APK USING GITHUB ACTIONS (can be done from a phone)
===========================================================
1. Create a new repository on GitHub (private is fine) and upload the CONTENTS of this extracted folder to the repository root (not the ZIP as a single file).
2. Make sure `.github/workflows/build-apk.yml` is included. It is included in this source ZIP.
3. Open the repository's Actions tab and select "Build CourseHub Udemy APK". If prompted, tap Run workflow.
4. Wait for the workflow to finish successfully. Open the run and download the `CourseHubUdemy-debug-apk` artifact.
5. Extract the artifact ZIP and install `app-debug.apk` on your Android phone. Only install APKs you built and trust.
6. If Android blocks installation, allow your browser/file manager to install unknown apps for this installation.

BUILD ON A COMPUTER
===================
1. Install Android Studio: https://developer.android.com/studio
2. Open the extracted project folder in Android Studio and allow Gradle sync.
3. Choose Build > Build Bundle(s) / APK(s) > Build APK(s).
4. APK output is normally app/build/outputs/apk/debug/app-debug.apk.

SETUP
=====
1. Open the app and enter your Telegram bot token and channel username (e.g. @coursehubudemy).
2. Add the bot as a channel administrator with permission to post.
3. Optional shared history: enter the same Supabase Project URL and public publishable/legacy anon key on every device, then tap Save shared history settings. Do not use a service_role or secret key in the app.
4. Supabase must have a public.course_history table with the columns below, and API permissions/policies must allow the intended operations. Restrict policies carefully; broad anonymous write access can let other people alter the history.
5. Tap Scan Courses, then Post New Offers (Manual), or enable Automatic scanning & posting.
6. Android WorkManager periodic jobs run no more often than every 15 minutes and may be delayed by battery optimization. Automatic mode is best-effort.

SUPABASE TABLE SQL
==================
Run in Supabase SQL Editor if the table does not already exist:

create table if not exists public.course_history (
  offer_id text primary key,
  title text not null default '',
  source_url text not null default '',
  enroll_url text not null default '',
  coupon text not null default '',
  posted_at text not null default '',
  status text not null default 'posting' check (status in ('posting', 'posted', 'failed'))
);

alter table public.course_history enable row level security;

IMPORTANT SECURITY NOTE
=======================
A publishable/anon key is not a secret. RLS policies are the security boundary. Do not use unrestricted anonymous write policies for a public or shared production app; anyone who obtains the app's URL and public key may be able to modify the history if policies permit it. For stronger security, put database access behind a small authenticated server/API. Never put a Supabase service_role or secret key in an Android APK.

LIMITATIONS
===========
- The scanner uses HTML parsing and the FreeWebCart page structure may change. Missing numbered pages are now skipped instead of failing the whole scan, but dynamically loaded "Load More" content may not be available to Jsoup.
- The scanner does not verify final Udemy price or coupon validity. Always check the course page before relying on a free offer.
- Shared duplicate prevention requires the same Supabase project/key and compatible table policies on both devices. Offers already recorded locally are seeded into shared history when cloud history is first enabled.
- The Android APK has not been compiled in this environment; use the included GitHub Actions workflow to build it.
- Keep your Telegram bot token private. It is stored in app-private preferences on the device.

DIRECT UDEMY ENROLLMENT LINKS
The scanner now attempts to resolve FreeWebCart redirect links into direct Udemy course URLs while preserving coupon/affiliate parameters. If it cannot resolve a direct Udemy course URL, it will not publish the FreeWebCart redirect link. Coupon validity and current price are not verified.
