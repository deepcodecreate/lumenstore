# Changelog

All notable changes are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.5.10-beta]

### Changed
- The Explore Lumen apps slider changes slides sooner and moves with a single smooth, cleaner animation; transitions, page dots and the progress fill share one timing
- Each anti-feature in the app detail now shows a short description in the app language

## [0.5.9-beta]

### Fixed
- The Explore Lumen apps slider now advances automatically on right-to-left layouts too
- Swiping the slider by hand no longer squeezes the banner into a clipped square shape; the transition is now flat and clean

### Changed
- Each anti-feature in the app detail now shows a short description in both Persian and English

## [0.5.8-beta]

### Fixed
- The Home add-button menu no longer cuts the top item while opening and no longer mixes up when the list is scrolled quickly
- The add button could stay hidden after quick direction changes while scrolling; it now always follows the last scroll direction

### Changed
- The Lumen apps slider in Explore is slower, with longer transitions, animated page dots and a progress fill on the active dot
- Anti-features in the app detail now show an icon matching each one
- Releases, including beta versions, are published as the latest release

## [0.5.7-beta]

### Fixed
- An update now stays in the Home update list until the install is really confirmed and the installed version is compared with the latest version
- The three-dot menu has four equal sharper corners and its pointer arrow sits exactly under the button

### Added
- Downloads: long-press an installed item with a kept APK to delete only its APK (the item stays in the list) or extract the APK into a chosen folder
- The Explore Lumen apps slider now plays automatically with strongly animated transitions and returns to the first slide after the last one

## [0.5.6-beta]

### Changed
- Explore hero is now a slider that introduces Lumen's own apps (Terbian, Pond and Proxy Share) with their icons and background colors taken from each icon
- The three-dot menu now has equal, larger corners on all four sides and the pointer arrow moved so it no longer breaks the shape
- Pull-to-refresh indicator now floats above the content instead of pushing it down

## [0.5.5]

### Changed
- All pull-to-refresh views now use the Material 3 Expressive loading indicator in a round container, and the content slides down while you pull
- Releases are published as stable and marked as latest instead of pre-release

## [0.5.4] - Beta

### Fixed
- Updates are no longer taken from debug APKs: debug builds are ignored when a release APK exists
- `com.rk.xededitor` is removed from every list, including Home, Explore, search and repository indexes

### Changed
- The profile README on the GitHub account page now sits right under the account header, above the repositories

## [0.5.2] - Beta

### Changed
- GitHub account page redesigned to be minimal and compact: smaller avatar and header without the oversized card, one slim stats row, a single-row action bar, tighter repository, language, people and activity items

## [0.5.1] - Beta

### Fixed
- Lumen Store is now detected in the Home list so its own updates appear
- Manual repositories showing "None found" during a scan: the saved list is recovered automatically

### Changed
- R8 shrinking turned off again until fully tested on devices
- Releases now include both release and debug APKs with checksums

## [0.5.0] - Beta

### Added
- Scan installed apps and match them against F-Droid, IzzyOnDroid and GitHub releases
- One-tap install and update with ABI-aware APK selection
- GitHub explorer with README rendering, screenshots and profiles
- Custom repositories from GitHub, GitLab and Codeberg
- Google Play search
- Backup and restore, with optional automatic backups
- Material 3 themes, seed colors, icon shapes and live backgrounds
- English and Persian with RTL support
- `lumenstore://app/<package-name>` deep links
- GitHub Actions for builds, releases, CodeQL and dependency tracking

### Security
- GitHub token is encrypted with a hardware-backed Android Keystore key and migrated from plain storage
- System backups are disabled so the token never leaves the device
- File sharing paths restricted to app-private and app download folders
