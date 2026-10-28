# Releasing Lumen

Releases are built and published by `.github/workflows/release.yml` whenever main has a `lumenVersion` (in
`app/build.gradle.kts`) that isn't released yet. Every installed Lumen checks the latest release and offers it.

## The release key (set up once)

Published APKs are signed with a private key. Android only installs an update signed with the **same** key as the
installed app, so:

- **Never lose it.** Without it, no installed Lumen can ever be updated again; everyone would have to uninstall
  (losing their playlists and favourites) and reinstall. Keep a backup of the file and its password, for example in
  your password manager.
- **Never commit it or share it.** Anyone holding it could publish an "update" that every Lumen would accept.

### 1. Create the key (on the Mac, in Terminal)

`keytool` comes with Java (`brew install openjdk@17`, already installed for building Lumen).

```sh
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
"$JAVA_HOME/bin/keytool" -genkeypair -v -keystore ~/lumen-release.jks -alias lumen \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Lumen"
```

It asks for a password twice. Use one strong password (store it in your password manager). This makes
`~/lumen-release.jks`: back it up now.

### 2. Give it to GitHub

On https://github.com/hayatayubi/IPTV → **Settings → Secrets and variables → Actions → New repository secret**, add
four secrets:

| Name | Value |
| --- | --- |
| `LUMEN_KEYSTORE_BASE64` | the key file as text: run `base64 -i ~/lumen-release.jks \| pbcopy`, then paste |
| `LUMEN_KEYSTORE_PASSWORD` | the password |
| `LUMEN_KEY_ALIAS` | `lumen` |
| `LUMEN_KEY_PASSWORD` | the same password |

Secrets can't be read back from GitHub, not even by you, so the backup from step 1 matters.

### 3. Publish

Run the workflow: **Actions → Release → Run workflow** (or merge any change that bumps `lumenVersion`).
It makes release `v<version>` with `lumen.apk` attached.

## Shipping an update

Bump `lumenVersion` (e.g. `0.3.0` → `0.3.1`) in the PR and merge it. The release notes are the commit titles since
the previous release, so write them for people ("Seek faster in films"), not for code.

## Changing to this key (once, in October 2026)

Lumen 0.2.0 and earlier were signed with the shared debug key in `keystore/`. Android can't move an installed app to
a new key, so a TV with an older Lumen must uninstall it and install the first privately signed release (this wipes
its playlists and favourites once).

## The Downloader code

`https://github.com/hayatayubi/IPTV/releases/latest/download/lumen.apk` always serves the newest release. To give
people a short number to type into the Downloader app, enter that link at https://aftv.news (the AFTVnews URL
shortener). Codes are permanent and public.
