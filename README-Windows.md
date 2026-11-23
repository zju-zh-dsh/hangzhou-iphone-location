# 杭州市内虚拟定位 Windows 版

本工具通过 USB 向 iPhone 发送杭州地图上的模拟定位坐标。地图中的 10 米圆圈供选点参考，地图数据和各 App 显示的实际精度可能不同。

## 使用方法

1. 使用 Windows 10 或 Windows 11 的 x64 电脑，以及 iOS 17.4 或更新版本的 iPhone。
2. 在 Windows 安装并打开 [Apple 设备 App](https://apps.microsoft.com/detail/9np83lwlpz9k) 或 [iTunes 官方安装程序](https://www.apple.com/itunes/download/win64)，以安装 Apple 移动设备服务。
3. 用支持数据传输的 USB 线接上 iPhone。解锁 iPhone，点「信任此电脑」，在 Apple 设备 App 或 iTunes 中确认能识别 iPhone。按需开启 [iPhone 开发者模式](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)。
4. 解压**整个** ZIP 包，双击其中的 `Start-Hangzhou-iPhone-Location.cmd`。首次启动需要联网下载并校验运行环境。保持命令窗口打开。若浏览器没有自动开启，请复制命令窗口里的本机地址打开。
5. 用地图选点，点击「应用到 iPhone」，再打开手机的「地图」核对定位。用完点击「恢复真实定位」，随后结束程序。

首次设置定位可能需要联网准备 iOS 开发组件。在这一步请保持设备解锁和 USB 连接。地图在线读取 OpenFreeMap 的底图。电脑上只需保留一个已连接的 iPhone。

项目源码采用 GPL-3.0-or-later。参见 [LICENSE](LICENSE) 和 [第三方组件声明](THIRD_PARTY_NOTICES.md)。
