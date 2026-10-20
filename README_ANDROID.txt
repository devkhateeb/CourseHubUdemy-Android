CourseHub Udemy Android project
================================

This is the Android source project (not a prebuilt APK). It includes a native dashboard, manual scan/post, per-course manual posting, Telegram bot/channel settings, local duplicate history, optional Supabase shared history, and scheduled background scanning/posting.

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
6. AUTOMATIC BACKGROUND MODE (new in 1.1):
   - Under "Scan & posting" set the timers: "Scan every N minutes" (min 5), "Post every N minutes" (min 1)
     and "Max courses per post run". Tap "Save timers", then switch on "Automatic scanning & posting".
   - Allow notifications when Android asks (Android 13+). A persistent notification then shows that the app is
     working: the next scan/post times, queued offers and the last result. While scanning or posting it shows a
     live progress bar ("Scanning courses - reading course 4 of 12", "Posting 2 of 5: <title>"). After every run a
     short summary notification appears. The notification has "Run now" and "Stop" buttons.
   - The scan timer fills a queue with offers not yet posted; the post timer sends up to the chosen number from the
     queue (about 1.5 s apart). Timers survive app restarts and phone reboots.
   - Tap "Allow unrestricted battery use" and, on some phones (Xiaomi, Oppo, Vivo, Samsung, etc.), also set the app's
     battery mode to Unrestricted / allow autostart in system settings. Otherwise the phone may pause the app.
   - Battery tips: scan every 120+ minutes (the site changes slowly), post every 30+ minutes, keep "Max courses per post
     run" modest, and turn automatic mode off when you don't need it. The app now makes no periodic wake-ups, skips
     course pages it already read in the last 6 hours, and does nothing while offline.
   - A 15-minute WorkManager watchdog restarts the background service if the phone killed it.

DIRECT UDEMY LINKS (1.3)
========================
Telegram posts now use the real udemy.com course link, not the freewebcart.com/redirect/... link. The app follows
the redirect when posting (HTTP redirects, page/JavaScript redirects and affiliate links that wrap the Udemy URL).
By default tracking parameters are removed, giving https://www.udemy.com/course/<name>/?couponCode=<CODE>.
Turn off "Clean Udemy link" in Telegram setup to keep the full link with all parameters. If the Udemy link cannot be
found, that course is NOT posted (so a freewebcart link never goes out) and it is retried on the next run.

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
- Background timers are best-effort: aggressive battery managers can still stop the app. If you swipe the app away and
  your phone force-stops it, open the app once to restart automatic mode.
- The Android APK has not been compiled in this environment; use the included GitHub Actions workflow to build it.
- Keep your Telegram bot token private. It is stored in app-private preferences on the device.
