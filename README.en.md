# GenTV Fish Launcher

**Your TV home screen can be a living pond.**

[中文](README.md) · [Build & install](android/README.md) · [Privacy](PRIVACY.md) · [MIT License](LICENSE)

![GenTV running on Android TV](docs/media/gentv-hero.gif)

GenTV is an interactive Android TV home screen where you can watch koi, feed them, and wake a fishing cat to catch fish from the same living pond. Apps remain within reach, while your home screen becomes somewhere worth staying.

## Feed a little joy into the room

Drop a handful of food and watch the koi approach, turn and gather around it. Tails flick, ripples spread, and the pond fills with movement. There is no progress bar to chase: sometimes a few quiet minutes watching fish is all you need.

![Feeding the koi](docs/media/feeding-time.png)

## Wake the sleepy cat and go fishing

The little cat dozes on a stool beneath its straw hat. Let it rest, or wake it for a few casts beside the pond. Watch the float, wait for a fish to swim beneath it, and enjoy the small surprise of a catch.

![The fishing cat taking a nap](docs/media/cat-dozing.png)

![The cat awake beside the float](docs/media/fishing-time.png)

## Collect today's little moments

Each catch records its name, weight and time, together with a little daily merit. Open Achievements to look back at the fish you met today. There is no leaderboard to keep up with—just a small record of time pleasantly spent.

![A catch adds a little daily merit](docs/media/catch-moment.png)

Catches and merit stay on the device. No account, no check-in task, no pressure.

## Or simply enjoy the view

Koi wander together and apart, the turtle paddles slowly, and an occasional drop or maple leaf touches the water. After 15 seconds, controls and the clock fade to 30% opacity without disappearing. Your apps remain within reach; the scenery gets room to breathe.

![Quiet pond with the interface faded](docs/media/immersive-pond.png)

Five editable favorites, up to two deduplicated recent apps, and a remote-friendly application library keep everyday TV use simple. No account, ads or analytics SDK; the Android app does not request internet access.

The GIF and scene images are actual Android-engine captures from an isolated TV emulator, not concept renders. Fish positions vary naturally. “Keep fish” means viewing and feeding, not breeding, leveling, or hunger management.

## Build

Requirements: JDK 17+, Android SDK 35, Android 6.0 / API 23+ target device with OpenGL ES 2.0.

~~~sh
git clone https://github.com/Elaine-Lynn/GenTV-launcher_fish.git
cd GenTV-launcher_fish
sh android/gradlew -p android :app:assembleDebug
adb -s YOUR_DEVICE_SERIAL install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR_DEVICE_SERIAL shell am start -n com.gentv.fishlauncher/.MainActivity
~~~

Published builds, when available, are listed in [Releases](https://github.com/Elaine-Lynn/GenTV-launcher_fish/releases). Installation does not automatically replace your default Home or disable other apps.

The current package is com.gentv.fishlauncher, version 1.11.0. Apps using an older package name keep separate private data. Automatic migration is not implemented; preserve your old installation if you need its settings or catches.

Device firmware, screensavers, remote mappings and GPU drivers can affect behavior. A 60 FPS option is not a promise of 60 FPS on every device. Samsung Tizen and LG webOS cannot install this Android APK.

## Develop

~~~sh
npm run check
npm test
sh android/test-simulation.sh
python3 tools/check_public_source.py
~~~

See [CONTRIBUTING.md](CONTRIBUTING.md). Instrumentation tests modify local test data; run them only on an isolated device.

## License and contact

Project code and supplied assets are available under the [MIT License](LICENSE). Third-party copyright notices and brand rights remain separate; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Maintainer: [Elaine-Lynn](https://github.com/Elaine-Lynn). Use [Issues](https://github.com/Elaine-Lynn/GenTV-launcher_fish/issues) for feedback.
