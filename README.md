# miku camera

一款以 **Miku** 为主角的原生 Android 相机，由 [zakee](https://zakee.fun) 开发。围绕“让 Miku 进入现实照片”设计的拍摄体验。

[个人主页](https://zakee.fun) · [Buy me a coffee](https://ifdian.net/a/zakee/plan)

当前版本：`1.7.0`（`versionCode 20`）

## 关于 miku camera

miku camera 是一款以 Miku 为主角的 Android 相机。普通模式可将 Miku 水印、时间与地点自然叠加在照片中；AI mode 会结合人物或风景重新创作，让 Miku 与现实画面互动融合，并生成有趣可爱的专属时间地点水印。

### 遇到不兼容问题怎么办？

如果设备、Android 版本或本地构建环境出现不兼容问题，可以使用 Agent 辅助排查和构建。将具体的错误日志、Android 版本、设备型号以及 JDK、Gradle 和 Android Gradle Plugin 版本提供给 Agent，它可以帮助检查项目配置、调整依赖并定位构建失败原因。

## 主要功能

- **Miku 合影**：内置名为 `miku` 的默认 PNG 水印，首次安装即可拍摄。
- **所见即所得**：取景框与最终照片保持一致，支持前后镜头、横竖方向、闪光灯、点击对焦和曝光调节。
- **自由水印**：Miku PNG 可移动、旋转和调整描边；时间与地点固定排版，不随 PNG 旋转。
- **时间地点**：时间精确到小时，地点可选择是否显示门牌或 POI。
- **预设管理**：可以新建、命名、编辑、删除水印，并记住上次选择。
- **AI mode（四步式创作流）**：支持相机直拍或从相册导入原图，遵循「`1 拍摄选图` → `2 提示词确认` → `3 AI生成` → `4 结果保存`」清晰工作流，并支持后台事务处理与可拖动悬浮窗进度追踪。
- **AI 风格与双参考图**：支持二次元与三次元（真人写实）风格、公式服或依据场景自动搭配服装、`1K / 2K / 4K` 分辨率档位选择，并支持为二次元/三次元分别配置独立预设参考图。
- **结果确认与失败诊断**：生成后可一键对比原图，选择保存 AI 图、原图或同时保存两张；生成失败时在第三步直接展示完整脱敏报错详情，并提供「重试 / 返回编辑 / 放弃」快捷操作。
- **多协议接口配置**：支持 OpenAI、Gemini、火山方舟、通义千问等多协议与多套 API 配置切换，可自由设置 API Key、Base URL 和图像模型。

## AI 工作流

```text
干净照片/相册导图 + 时间地点信息 + 预设参考图(可选) → 提示词确认 → 后台 AI 生成(悬浮窗追踪) → 结果确认 → 保存
```

AI mode 沿用普通模式的取景范围、方向处理、前置镜像与相机原始出片尺寸。普通水印和用户上传的普通模式 PNG 不会发送给 AI。

## 隐私说明

- 普通模式的取景、水印合成、定位文字处理和相册保存均在设备本地完成。
- 只有用户主动进入 AI mode 并开始生成时，应用才会向用户配置的服务发送干净照片、参考图（若启用）、提示词和已启用的时间地点信息。
- API Key 通过 Android Keystore 加密后保存在本机，不内置于源码或发布 APK。
- 应用无账号系统、无统计埋点，不向开发者服务器上报照片或个人数据。
- 照片保存在系统相册的 `Pictures/miku camera` 目录。

## 构建

使用 Android Studio 打开项目，或在项目根目录执行：

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

生成文件：

```text
app/build/outputs/apk/debug/miku-camera-1.7.0.apk
app/build/outputs/apk/release/miku-camera-1.7.0.apk
```

本地构建使用未纳入 Git 的 `app/miku-camera-signkey-v2.keystore` 固定签名。请妥善保存该文件；相同包名的后续版本必须使用同一签名才能覆盖安装。

版本更新位置及发布流程见 [VERSION_GUIDE.md](./VERSION_GUIDE.md)。
