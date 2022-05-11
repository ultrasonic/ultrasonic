# Ultrasonic
[![Build Status](https://circleci.com/gh/ultrasonic/ultrasonic/tree/develop.svg?style=shield&circle-token=:circle-token)](https://circleci.com/gh/ultrasonic)
[![Codecov branch](https://img.shields.io/codecov/c/github/ultrasonic/ultrasonic/develop.svg)]()
[![ktlint](https://img.shields.io/badge/code%20style-%E2%9D%A4-FF4081.svg)](https://ktlint.github.io/)

Ultrasonic is free and open-source music streaming Android client for [Subsonic](http://www.subsonic.org/) [API](http://www.subsonic.org/pages/api.jsp) (version 1.7.0 or higher) compatible servers.

## Help wanted

We currently don't have that much time to spend developing Subsonic, so any
contributions or active developers are always welcomed.

## Screenshots

| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" alt="Screenshot-1" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" alt="Screenshot-2"/> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" alt="Screenshot-3"/> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png" alt="Screenshot-4" /> |
|------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------| ---- |-----------------------------------|

## Download

App is available to download at following stores:

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="70">](https://play.google.com/store/apps/details?id=org.moire.ultrasonic)
[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="70">](https://f-droid.org/packages/org.moire.ultrasonic/)
[<img src="https://ultrasonic.github.io/assets/img/get-it-on-github.png" alt="Get it on GitHub" height="70">](https://github.com/ultrasonic/ultrasonic/releases)

**Warning**: All three versions (Google Play, F-Droid and the APKs) are not
compatible (not signed by the same key)! You must uninstall one to install
the other, which will delete all your data.  

If you want to use the version downloaded from F-Droid or form Github with **Android Auto**, you must enable Unknown Sources as it is described in [this wiki page](https://github.com/ultrasonic/ultrasonic/wiki/Using-Ultrasonic-with-Android-Auto).

## Bugs and issues

First, see if your issue haven’t been yet reported [here](https://github.com/ultrasonic/ultrasonic/issues),
otherwise open [a new issue](https://github.com/ultrasonic/ultrasonic/issues/new).

### Known (not our) bugs

If you are using *Madsonic 5.1.X* several sections of Ultrasonic will not
work. This is caused by bad implementation of Subsonic API by Madsonic. For
more info about this you can read [this bug](https://github.com/ultrasonic/ultrasonic/issues/129).

## Contributing

See [CONTRIBUTING](CONTRIBUTING.md).

## Supported (tested) Subsonic API implementations

- [Subsonic](http://www.subsonic.org/pages/index.jsp)
- [Airsonic-Advanced](https://github.com/airsonic-advanced/airsonic-advanced)
- [Supysonic](https://github.com/spl0k/supysonic)
- [Ampache](https://ampache.org/)

Other *Subsonic API* implementations should work as well as long as they follow API
[documentation](http://www.subsonic.org/pages/api.jsp).

## License

This software is licensed under the terms of the GNU General Public License version 3 (GPLv3).

Full text of the license is available in the [LICENSE](LICENSE) file and [online](https://opensource.org/licenses/gpl-3.0.html).
