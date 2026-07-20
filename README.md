# 水印相机（miku-camera）

原生 Android / Kotlin 水印相机。打开应用直接进入拍照模式。

**当前版本：`1.1`**（`versionCode` 2 · Git tag `1.1`）

版本号改哪里请看 → [VERSION_GUIDE.md](./VERSION_GUIDE.md)

## 1.1 主要能力

- **所见即所得**：CameraX `ViewPort` 与 3:4 取景框对齐，出片范围与预览一致。
- **布局**：屏幕居中 9:16 内容区 + 上下黑边；取景框严格 3:4；顶栏 🌸 / 闪光灯在取景外。
- **闪光灯**：关 ❌ / 开 ⚡ / 自动 ⚡A（角标贴右下）。
- **对焦 + 曝光**：点击对焦，取景框右侧滑动调 EV。
- **水印**：PNG + 时间 + 地点；拍照页可直接拖动缩放；编辑页描边/开关；预设实时保存。
- **快门**：按下光圈收缩动画。
- 前后镜头切换；相册缩略图；水印列表 PNG 预览。

## 打开与运行

1. 用 Android Studio 打开本目录，同步 Gradle。
2. 运行 `app`。首次需要相机权限；开启地点水印时再申请定位。

相册路径：`Pictures/水印相机`。

## 构建

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## 版本与发布

| 项 | 值 |
|----|-----|
| versionName | 1.1 |
| versionCode | 2 |
| Git tag | 1.1 |

发版步骤与字段说明见 [VERSION_GUIDE.md](./VERSION_GUIDE.md)。

## 后续可扩展

- 地点水印详细程度（市/区/完整地址）可配置
- 前置镜像策略
- Room 替代 SharedPreferences
- 大图内存保护
