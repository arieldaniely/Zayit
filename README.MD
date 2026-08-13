# Zayita — independent Jewish-text study app

[עברית](README_HE.md)

Zayita (זיתא) is an independent fork of [Zayit](https://github.com/kdroidFilter/Zayit)
for reading, searching, and studying Jewish texts on Windows, macOS, and Linux.
It has its own product name, application identifiers, install paths, update feed,
and release artifacts. It is not created, distributed, endorsed, or supported by
the Zayit developers.

## Distribution status

The application and website are configured to obtain Zayita releases from
[`arieldaniely/Zayit`](https://github.com/arieldaniely/Zayit). The distinct Zayita
icon is integrated into the desktop packages and website. A clean release build
and installation checks on the target operating systems are still required before
public distribution; code signing is recommended but is not required to create the artifacts.

The books database is downloaded separately from the upstream
[`kdroidFilter/SeforimLibrary`](https://github.com/kdroidFilter/SeforimLibrary)
release. It is not presented as an asset created or distributed by the Zayita project.

## Build and test

Use JBR/JDK 25 and clone with submodules:

```bash
git clone --recurse-submodules https://github.com/arieldaniely/Zayit.git
./gradlew :SeforimApp:jvmTest
./gradlew :SeforimApp:run
```

The website is under `website/`:

```bash
cd website
npm install
npm run build
```

## Links and identifiers

- Application ID: `io.github.arieldaniely.zayita`
- macOS bundle ID: `io.github.arieldaniely.zayita.desktopApp`
- Desktop package/binary: `zayita`
- Native URL scheme: `zayita://`
- Legacy input compatibility: `zayit://` and `otzaria://`

## Fonts

The Zayit-exclusive Rashi Amiti font is not included. Zayita uses
[Mekorot Rashi](https://github.com/aharonium/fonts/tree/master/Fonts/Hebrew%20Letters%20with%20Vowels%20%28no%20cantillation%29/Mekorot%20%28LPPL%29/Rashi)
under the LPPL; its notice is included with the application resources.

## License and attribution

The application code is distributed under GNU AGPL v3 with the additional
Section 7(b) attribution term in [LICENSE](LICENSE). Each book or corpus may have
separate terms; see [CONDITIONS.md](SeforimApp/src/commonMain/composeResources/files/CONDITIONS.md).

> Powered by the technologies that drive Zayit — https://zayitapp.com/
