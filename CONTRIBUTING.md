# 参与开发

改动应附上用途和适用设备范围。行为逻辑通过 JDK 模拟测试验证；UI 变更应提供隔离测试设备截图和遥控导航验证。

```sh
npm run check
npm test
sh android/test-simulation.sh
sh android/gradlew -p android :app:assembleDebug :app:assembleDebugAndroidTest
git diff --check
```

不得提交签名文件、凭证、设备序列号、内网地址、私有日志、APK 或构建缓存。实机测试不替代模拟测试，模拟器也不替代实体遥控器验证。

## Public identity

Project-maintained commits use the GitHub identity Elaine-Lynn and the account noreply address. Project support goes through the GitHub profile or repository issues; no private author contact information is required.

Do not remove legally required third-party copyright notices when changing project contact information.
