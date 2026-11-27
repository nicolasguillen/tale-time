# Store releases

How the app gets to the App Store and Google Play: fastlane (`fastlane/Fastfile`, run from the repo root, installed with Homebrew) builds a signed release, uploads it and moves it toward production. Stories aren't part of a release: release builds download the **production** channel, so publish it first (`cd tools && uv run story publish production`, only when the user asks). Every `build` lane checks that the production catalog is online and stops otherwise (`skip_catalog_check:true` skips the check).

## Versions

- **Version name**: `MARKETING_VERSION` in `iosApp/project.yml`, used by both stores. Bump it there for a new version.
- **Build number**: fastlane picks it. On iOS it's the latest TestFlight build + 1, passed to xcodebuild (don't edit `CURRENT_PROJECT_VERSION`). On Android it's the highest versionCode on any Play track + 1, passed as the Gradle property `taletime.versionCode` (`version_code:N` overrides it).

## iOS

```bash
fastlane ios build                       # signed App Store .ipa in iosApp/build/fastlane/, no upload
fastlane ios beta                        # build + upload to TestFlight (doesn't wait for Apple's processing, 5–20 min)
fastlane ios distribute groups:Friends [build:N] [changelog:"…"]   # once processed: external testers (Beta App Review)
fastlane ios submit_review [build:N] [auto_release:true]           # once processed: App Review for the version in project.yml
```

- Signs in with the personal App Store Connect API key (`~/.appstoreconnect/AuthKey_C78CP77HT2.p8`; override with `ASC_KEY_ID` / `ASC_ISSUER_ID` / `ASC_KEY_PATH`). Xcode manages signing automatically through that key (team `T9TJS97YP6`), so no certificates or profiles are kept by hand.
- `submit_review` sends only the build: the listing, screenshots, age rating, Kids category, privacy answers and review notes are set in App Store Connect. Without `auto_release:true`, release the approved version from App Store Connect.
- The app record already exists in App Store Connect (`com.nicolasguillen.taletime`).

## Android

```bash
fastlane android build [version_code:N]                 # signed .aab in androidApp/build/outputs/bundle/release/
fastlane android beta [status:draft]                    # build + upload to the internal testing track
fastlane android promote [from:internal] [to:alpha] [status:draft] [rollout:0.2]
fastlane android promote to:production [rollout:0.2]    # production, all at once or a staged rollout
```

- Release builds are signed with the **upload key** from `~/.googleplay/taletime-upload.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`; override with `TALETIME_UPLOAD_KEY_PROPERTIES`). Without it, `./gradlew :androidApp:bundleRelease` builds an unsigned bundle and `fastlane android build` stops. Never commit the key or print its passwords. Google re-signs the bundles for players (Play App Signing).
- fastlane talks to Play as the service account key `~/.googleplay/taletime-play-publisher.json` (override with `PLAY_JSON_KEY`).
- While the app has never been published, Play takes only draft releases: pass `status:draft`, then roll the draft out from the Console.
- Track names: `alpha` is closed testing, `beta` is open testing.

### One-time setup

1. **Upload key** (back up both files in a password manager: a lost upload key takes days to reset in the Console):
   ```bash
   keytool -genkeypair -v -keystore ~/.googleplay/taletime-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```
   Then write `~/.googleplay/taletime-upload.properties` with `storeFile=/Users/<you>/.googleplay/taletime-upload.jks`, `storePassword=…`, `keyAlias=upload`, `keyPassword=…`, and `chmod 600` both.
2. **Service account** (done 2026-11-27): `play-publisher@tale-time-b537d.iam.gserviceaccount.com`, Tale Time's own, in the Cloud project `tale-time-b537d` where the Google Play Android Developer API is enabled:
   ```bash
   gcloud services enable androidpublisher.googleapis.com --project tale-time-b537d
   gcloud iam service-accounts create play-publisher --project tale-time-b537d --display-name "Play Console publisher (fastlane)"
   gcloud iam service-accounts keys create ~/.googleplay/taletime-play-publisher.json \
     --iam-account play-publisher@tale-time-b537d.iam.gserviceaccount.com --project tale-time-b537d
   chmod 600 ~/.googleplay/taletime-play-publisher.json
   ```
   Then in Play Console → Users and permissions → Invite new users: add that email with access to Tale Time only (view app information, release to testing tracks and production, manage testing tracks, manage store presence).
3. **Create the app** in the Play Console (`com.nicolasguillen.taletime`, free, an app).
4. **First upload by hand**: the API needs a bundle on Play already. `fastlane android build version_code:1`, upload the `.aab` to Internal testing in the Console and accept Play App Signing.

## What still needs the stores' consoles

- **Families / Kids:** Play's target audience (children: the app falls under the Families policy and the Teacher Approved program is optional) and App Store's Kids category with its age band. Both forbid third-party analytics and ads, which the app doesn't have.
- **Privacy:** nothing is collected (App Store "Data Not Collected", Play Data safety: no data collected or shared), a privacy policy URL, content rating questionnaires.
- **Listing:** text, screenshots (phone and tablet), icon, Play's feature graphic.
- **Paid stories:** in-app products need StoreKit and Play Billing in the app first (`domain/Purchases` is `UnavailablePurchases`).
- **Production access on Play:** a personal developer account created after November 2023 needs a closed test with at least 12 testers opted in for 14 days in a row before it can apply for production.

## Gotchas

- A release APK signed with the upload key can't install over a debug build (different signature).
- versionCode must go up with every upload, drafts included. Let fastlane pick it.
- `fastlane lanes` lists the lanes and fails if the Fastfile doesn't parse.
