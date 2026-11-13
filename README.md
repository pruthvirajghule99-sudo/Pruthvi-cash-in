# Pruthvi Cash

Pruthvi Cash is an Android loan-application client built with Kotlin, Jetpack Compose, Android SDK 24+, and Supabase Auth/PostgREST. It includes customer registration/sign-in, loan submission and status tracking, a profile screen, and a role-gated admin review view.

## Build

Install JDK 17, Android SDK platform 35/build tools 35, `curl` (or `wget`), and `unzip`, then run:

```sh
./gradlew assembleDebug
```

The lightweight `gradlew` launcher downloads the pinned Gradle 8.9 distribution on first run. Android Studio can open this project directly and sync with Gradle.

## Supabase setup

1. Create a Supabase project.
2. In the Supabase SQL Editor, run [`supabase/schema.sql`](supabase/schema.sql). The tables have RLS enabled; users can see/create their own applications, while only admins can review/update applications.
3. Set the project URL and **public anon/publishable key** in root `local.properties` (never commit this file):

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_PUBLIC_ANON_KEY
```

Alternatively pass Gradle properties, e.g. `./gradlew assembleDebug -PSUPABASE_URL=https://... -PSUPABASE_ANON_KEY=...`.

The Android app must only use the public anon/publishable key. **Never place a `service_role` or secret key in this app.** Authorization depends on Supabase Auth and database RLS; the anon key is not a substitute for RLS.

4. Register your account in the app, then promote the intended administrator from the Supabase SQL Editor using the commented statement at the end of `schema.sql`. Admin access is determined from the protected `profiles.role` row, not from an email suffix or client-side toggle.
5. For email/password registration, configure Supabase Auth email confirmation to match your desired onboarding. If confirmation is enabled, users must verify their email before signing in.

## Notes

- Credentials and access tokens are held in memory for the current process; sign out clears the in-memory token. For a production release, add encrypted persistent session storage, rate limiting/monitoring, privacy disclosures, and a server-side loan decision process.
- RLS protects the database even though the app is a public client. Review the policies and business constraints before real lending use.
- GitHub Actions uses `assembleDebug` and uploads `app/build/outputs/apk/debug/app-debug.apk`.
