# 杭州市内虚拟定位mac版

在电脑上点选杭州地图，通过 USB 将测试位置发送到 iPhone。支持 WGS 84 经纬度输入和复制、10 米选点参考圈、单点 GPX 导出和恢复真实定位。

## 下载

| 系统 | 安装包 | 启动 |
| --- | --- | --- |
| macOS | [杭州市内虚拟定位mac版](https://github.com/zju-zh-dsh/hangzhou-iphone-location/releases/latest/download/hangzhou-iphone-location-macos.zip) | 解压后双击 `杭州市内虚拟定位mac版.command` |
| Windows 10 / 11（64 位 x64） | [杭州市内虚拟定位Windows版](https://github.com/zju-zh-dsh/hangzhou-iphone-location/releases/latest/download/hangzhou-iphone-location-windows.zip) | 解压整个压缩包，再双击 `Start-Hangzhou-iPhone-Location.cmd` |
| Android 8.0 或以上 | [杭州市内虚拟定位 Android APK](https://github.com/zju-zh-dsh/hangzhou-iphone-location/releases/download/android-v1.0.0/hangzhou-android.apk) | 安装后在系统开发者选项中选择本工具作为模拟定位应用 |

Android 安装与 HarmonyOS 6.0 模拟器的 GPX 用法见 [移动端说明](android/README.md)。

首次启动需要联网下载并校验运行环境。Windows 需要 Apple 的 USB 驱动。可以先用 [Apple Devices 或 iTunes](https://support.apple.com/108643) 确认电脑能识别 iPhone。

## 使用

1. 用 USB 数据线连接一部已解锁的 iPhone 并点按「信任」。USB 定位需要 iOS 17.4 或以上和 [开发者模式](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)。
2. 在浏览器打开的杭州地图上选点，点击「放大到选点」微调，然后点「应用到 iPhone」。
3. 打开 iPhone 的「地图」，用定位箭头确认结果。用完先点「恢复真实定位」，再断开 USB 或关闭窗口。

实际地物精度取决于底图，定位显示也受 iOS 和所用应用影响。绿圈表示地图上的 10 米参考距离。GPX 也可用于 [Xcode 定位模拟](https://developer.apple.com/documentation/xcode/simulating-location-in-tests)。

## 许可证

项目源码采用 [GPL-3.0-or-later](LICENSE)。地图组件、地图数据和运行时依赖采用各自的许可证，见 [第三方组件说明](THIRD_PARTY_NOTICES.md)。

## 从源码构建

使用 Python 3.9 或更新版本。在仓库目录按系统运行对应命令。

```sh
python3 package.py './杭州市内虚拟定位mac版.command'
python3 package-windows.py ./hangzhou-iphone-location-windows.zip
```

Windows 使用细节见 [Windows 版说明](README-Windows.md)。
