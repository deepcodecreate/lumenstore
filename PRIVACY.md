# Privacy Policy

Lumen Store does not collect, sell or share personal data. There are no analytics, advertising or crash-reporting libraries in the app, and no account is required.

## What stays on your device

- Your list of installed apps, which is read locally to check for updates
- Your settings, theme, favorites and backups
- Your optional GitHub token, kept in the app's private storage and removed when you sign out

## Network requests

The app contacts only the services it needs to do its job:

| Service | Purpose |
|---------|---------|
| F-Droid and IzzyOnDroid | Download app indexes and APKs |
| GitHub (api.github.com, raw.githubusercontent.com, codeload.github.com) | Releases, READMEs, profiles and search |
| GitLab and Codeberg | Releases for repositories you add manually |
| Google Play | Public store listings for search |
| DNS over HTTPS resolvers | Name resolution when the system resolver is unreliable |

Requests carry the information required by each service, such as the package name being looked up and your IP address as seen by that service. If you sign in with a GitHub token, it is sent only to GitHub.

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Reach the services above |
| `QUERY_ALL_PACKAGES` | Compare installed apps with open-source releases |
| `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES` | Install, update and remove apps you choose |
| `POST_NOTIFICATIONS` | Update and download notifications |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Reliable background scans |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Downloads and scans that must keep running |

## Your control

Remove the GitHub token by signing out, clear all data from Android settings, or uninstall the app to delete everything it stores.

## Changes

Changes to this policy are published in this repository and noted in the [changelog](CHANGELOG.md).

---

## خلاصه فارسی

لومن استور هیچ داده شخصی‌ای جمع‌آوری یا به اشتراک نمی‌گذارد. هیچ ابزار تحلیل، تبلیغات یا گزارش خطایی در برنامه نیست و به حساب کاربری نیازی ندارد. فهرست برنامه‌ها و توکن اختیاری گیتهاب فقط روی دستگاه شما می‌ماند و درخواست‌های شبکه تنها به سرویس‌های لازم (F-Droid، IzzyOnDroid، GitHub، GitLab، Codeberg و Google Play) ارسال می‌شود.
