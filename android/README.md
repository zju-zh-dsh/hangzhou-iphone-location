# 杭州市内虚拟定位 Android 版

- [Android APK](https://github.com/zju-zh-dsh/hangzhou-iphone-location/releases/download/android-v1.0.0/hangzhou-android.apk)
- [地图选点与 GPX 工具](https://github.com/zju-zh-dsh/hangzhou-iphone-location/releases/download/android-v1.0.0/hangzhou-map-gpx.zip)

## Android 手机

1. 安装 APK（Android 8.0 或以上），开启系统定位。
2. 打开开发者选项，在「选择模拟位置信息应用」中选择「杭州市内虚拟定位」。项目使用 Android 系统的测试定位接口。
3. 在杭州地图点选，使用「放大到选点」查看 10 米圈，或手动录入 WGS 84 经纬度。点「开始模拟」。结束时打开工具，点「恢复真实定位」。
4. 可复制经纬度或导出 GPX。底图需要联网。10 米圈是选点参考，实际地物精度取决于底图；应用可能使用自己的定位来源或过滤模拟位置。

安装包由 GitHub Actions 构建，使用测试签名，签名密钥没有随源码发布。源码和安装包均可从仓库取得。

## HarmonyOS 6.0

HarmonyOS 6.0 真机目前没有经过确认的普通应用系统级模拟定位接口。Huawei 的 [真机 testhelper 文档](https://developer.huawei.com/consumer/cn/doc/harmonyOS-guides/testhelper-guidelines/) 将该工具的支持范围列为 HarmonyOS 7.0.0 及以上。Android APK 仅用于 Android 系统的测试定位。

使用 DevEco Studio 的 HarmonyOS 6.0 模拟器测试时，解压地图选点包并在浏览器打开 `index.html`，点选杭州坐标，点击「导出 GPX」。在 DevEco 模拟器的扩展菜单中打开「GPS 定位」，可直接输入六位小数经纬度，也可在「导入」里选择生成的 `hangzhou-point.gpx`。结束时停止回放。详见 [华为模拟器 GPS 定位说明](https://developer.huawei.com/consumer/cn/doc/HarmonyOS-Guides/ide-emulator-more-features/) 和 [6.0 SDK、模拟器配套信息](https://developer.huawei.com/consumer/cn/doc/doccenter-release-notes/deveco-studio-new-features-600/)。

## 构建

[Android 构建流程](../.github/workflows/android.yml) 使用 Android SDK 35 和 JDK 编译源码、校验 APK 签名并生成地图选点包。地图使用根目录中已有的 MapLibre 文件，底图为 OpenFreeMap，保留 OpenStreetMap 归属标注。许可见 [LICENSE](../LICENSE) 和 [第三方说明](../THIRD_PARTY_NOTICES.md)。
