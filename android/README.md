# 杭州市内虚拟定位 Android 版

- [下载 Android APK](https://github.com/zju-zh-dsh/hangzhou-iphone-location/releases/download/android-v1.2.0/hangzhou-android.apk)
- [下载地图选点与 GPX 工具](https://github.com/zju-zh-dsh/hangzhou-iphone-location/releases/download/android-v1.2.0/hangzhou-map-gpx.zip)

## 安装和检查

1. 安装 APK（Android 8.0 或以上），开启系统定位。在开发者选项的「选择模拟位置信息应用」中选择「杭州市内虚拟定位」。
2. 在杭州地图上选点，或输入 WGS 84 经纬度。点击「放大到选点」可查看 10 米参考圈。点「开始模拟」。
3. 点「检查系统定位」，按提示授予本工具精确位置权限。状态栏分别显示 Android 系统和融合服务是否接受定位写入，以及是否回读到本次选点 10 米内、带模拟标记的新坐标。
4. 结束时点「恢复真实定位」。如融合服务提示停止未确认，保持定位开关开启，再点一次恢复。

如果装过旧版，先恢复真实定位，卸载旧 APK 后安装新版，再重新选择模拟定位应用。本次 APK 使用不同的测试签名。

## 兼容范围

小米、红米、vivo、iQOO、OPPO、荣耀及兼容 Android 的华为机型，都根据实际可用的定位服务走同一套逻辑。APK 会尝试系统的 GPS、网络和系统融合通道。设备有可用的 Google Play 服务时，也会尝试 Google 融合定位。各通道独立显示状态。

华为 HMS 融合定位适配见下方的构建说明。它需要 AppGallery Connect 配置，公开 APK 当前使用 Android 通道和可用的 Google 通道。部分 Android 厂商会限制模拟位置。高德、腾讯地图等应用也可能过滤系统的模拟标记或使用其他来源。工具的回读结果反映该接口返回的坐标。

底图需要联网，屏幕上的 10 米圈用于选点参考。与现实地物的偏差受底图数据和坐标系影响。

## HarmonyOS 6.0

HarmonyOS 6.0 真机目前没有经过确认的普通应用系统级模拟定位接口。Huawei 的 [真机 testhelper 文档](https://developer.huawei.com/consumer/cn/doc/harmonyOS-guides/testhelper-guidelines/) 将该工具的支持范围列为 HarmonyOS 7.0.0 及以上。Android APK 仅用于 Android 系统的测试定位。

使用 DevEco Studio 的 HarmonyOS 6.0 模拟器测试时，解压地图选点包并在浏览器打开 `index.html`，点选杭州坐标，点击「导出 GPX」。在 DevEco 模拟器的扩展菜单中打开「GPS 定位」，可直接输入六位小数经纬度，也可在「导入」里选择生成的 `hangzhou-point.gpx`。结束时停止回放。详见 [华为模拟器 GPS 定位说明](https://developer.huawei.com/consumer/cn/doc/HarmonyOS-Guides/ide-emulator-more-features/) 和 [6.0 SDK、模拟器配套信息](https://developer.huawei.com/consumer/cn/doc/doccenter-release-notes/deveco-studio-new-features-600/)。

## 构建

[GitHub Actions](../.github/workflows/android.yml) 使用 JDK 17、Gradle 8.9 和 Android SDK 35，在云端生成 APK 和 GPX 选点包。也可用安装了相同工具的环境构建：

~~~sh
gradle -p android/sdk -Pandroid.useAndroidX=true :assembleDebug
~~~

如需启用华为 HMS，按 [AppGallery Connect 的 Location Kit 准备流程](https://developer.huawei.com/consumer/en/doc/hms-guides/location-preparation/) 创建 Android 应用，启用定位服务，登记与构建匹配的签名证书 SHA-256，并把应用配置文件存放在 <code>android/sdk/agconnect-services.json</code>。默认 debug 构建使用该环境的 Android 调试证书。配置文件和签名密钥由构建者保管。

~~~sh
gradle -p android/sdk -Pandroid.useAndroidX=true -PenableHms=true :assembleDebug
~~~

地图使用 MapLibre 和 OpenFreeMap，保留 OpenStreetMap 归属标注。许可见 [LICENSE](../LICENSE) 和 [第三方说明](../THIRD_PARTY_NOTICES.md)。
