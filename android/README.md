# GenTV Android

包名和 Java namespace：`com.gentv.fishlauncher`。当前版本：1.11.0 / versionCode 31。

## 构建

使用 JDK 17+、Android SDK 35。Gradle Wrapper 会解析项目依赖：

```sh
sh android/gradlew -p android :app:assembleDebug
sh android/test-simulation.sh
```

默认 Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`。

## 设备验证

所有 ADB 命令均显式指定自己的已授权测试设备：

```sh
adb -s YOUR_TEST_DEVICE install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR_TEST_DEVICE shell am start -W -n com.gentv.fishlauncher/.MainActivity
sh android/gradlew -p android :app:assembleDebugAndroidTest
adb -s YOUR_TEST_DEVICE install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s YOUR_TEST_DEVICE shell am instrument -w com.gentv.fishlauncher.test/com.gentv.fishlauncher.LauncherSmoke
```

Instrumentation 仅用于隔离的测试设备：会重置测试应用的鱼数、操作测试偏好和渔获记录。不要在用户日常使用的应用上运行。导航测试应用来自独立 `navigation-fixtures` 模块，不打包到主应用，也不要安装到用户盒子。

## Release 签名

创建自己管理的 keystore，并在被忽略的 `android/signing.local.properties` 中设置：

```properties
storeFile=signing/release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

```sh
sh android/gradlew -p android :app:assembleRelease
```

无签名配置时，Release 为未签名产物；也可通过 `-PunsignedRelease` 显式生成未签名 Release，避免使用本机已有的开发证书。不要提交私钥、密码、个人 SDK 路径或设备备份。使用相同包名升级时必须保持签名连续。

## 新包名

`com.gentv.fishlauncher` 与旧包名是不同应用。新版本不会自动读取旧应用的本地设置和渔获；没有跨包数据迁移器。安装和启动不设置默认 Home。
