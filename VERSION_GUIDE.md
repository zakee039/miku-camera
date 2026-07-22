# 版本号管理指南

本项目统一使用 **语义化版本** 字符串（如 `1.1`、`1.2.0`），并与 Android 安装包版本、Git 标签对齐。

## 何时改版本号（协作约定）

- **平时写功能 / 修 bug：不要改任何版本号**（不要动 `versionName`、`versionCode`、`VERSION`、README 版本表、Git tag）。
- **只有当维护者明确说「改版本号 / 发版 / bump 到 x.y」时**，才按本指南做 **全局** 版本更新（Gradle + VERSION + README + commit/tag）。
- AI / 协作者默认把版本字段当作只读，除非当前任务就是发版。

## 当前约定

| 名称 | 当前值 | 说明 |
|------|--------|------|
| **versionName** | `1.2.2` | 用户可见版本号（关于页、商店、APK 信息） |
| **versionCode** | `5` | 整数，每次上架/发版必须 **严格递增** |
| **Git tag** | `1.2.2` | 与 versionName 一致（本仓库不用 `v` 前缀） |
| **VERSION 文件** | `1.2.2` | 给人与脚本一眼看到的项目版本 |

> 历史：`0.1.0` 初版；`1.1` 所见即所得布局；`1.2` 定位/门牌/隐私说明与产品化完善；`1.2.1` 更名为 waifu camera 并记住上次选择的水印；`1.2.2` 修复前置摄像头横屏方向和镜像问题。

---

## 发版时必须改的地方（清单）

### 1. Android 应用版本（必改）

**文件：** `app/build.gradle.kts`

```kotlin
defaultConfig {
    versionCode = 5          // 整数，每次发版 +1（不可回退）
    versionName = "1.2.2"    // 与用户沟通的版本号，与 tag 一致
}
```

| 字段 | 作用 | 规则 |
|------|------|------|
| `versionName` | 显示给用户 | 改成新版本，如 `1.2`、`1.1.1` |
| `versionCode` | 系统判断升级 | 每次发布 **+1**（1→2→3…） |

同步 Gradle / 重新构建后，安装包元数据才会更新。

### 2. 项目 VERSION 文件（必改）

**文件：** `VERSION`（仓库根目录）

```
1.1
```

只写一行 versionName，方便脚本或文档引用。

### 3. README（建议改）

**文件：** `README.md`

- 文首「当前版本」
- 「版本与发布」表格中的 versionName / versionCode / tag

### 4. Git 提交与标签（发版必做）

```bash
# 1. 提交所有改动（含上面的版本文件）
git add -A
git commit -m "Release 1.1: …"

# 2. 打 tag（与 versionName 一致）
git tag -a 1.1 -m "v1.1"

# 3. 推送分支与标签
git push origin main
git push origin 1.1
```

GitHub 上可再「Create release」关联该 tag。

---

## 不需要改版本号的地方

| 位置 | 原因 |
|------|------|
| `build.gradle.kts`（根）里 AGP / Kotlin 插件版本 | 那是构建工具版本，不是 App 版本 |
| `gradle/wrapper`、`dependencies { … }` | 依赖库版本 |
| `compileSdk` / `targetSdk` / `minSdk` | SDK 级别，与 App 版本独立 |
| `AndroidManifest.xml` | 未写 `android:versionName`；由 Gradle 注入 |
| XML 头 `<?xml version="1.0"?>` | XML 规范版本，无关 App |
| 代码里业务常量（除非你做了「关于」页读死字符串） | 当前从 `BuildConfig`/Gradle 生成，不必手写 |

---

## 可选：代码里读取版本

若「关于」页要显示版本，优先：

```kotlin
// 需在 build.gradle.kts 开启 buildConfig（AGP 8+ 默认可能需显式 buildFeatures { buildConfig = true }）
val name = BuildConfig.VERSION_NAME
val code = BuildConfig.VERSION_CODE
```

**不要**再在 Kotlin 里写死 `"1.1"`，避免与 Gradle 不一致。

---

## 推荐发版流程（摘要）

1. 改 `app/build.gradle.kts` 的 `versionName` + `versionCode`
2. 改根目录 `VERSION`
3. 更新 `README.md` 版本说明
4. `assembleDebug` / `assembleRelease` 自测
5. `git commit` → `git tag` → `git push` + `git push --tags`
6. （可选）GitHub Release 附 APK 与更新说明

---

## versionName 怎么升

| 变更类型 | 示例 | 说明 |
|----------|------|------|
| 大改版 / 不兼容 | `1.x` → `2.0` | 重大交互或架构变化 |
| 功能更新 | `1.1` → `1.2` | 新功能、布局大改 |
| 修 bug / 小改 | `1.1` → `1.1.1` | 可选第三位 |

`versionCode` 不论上面哪一种，**每次对外发布都 +1**。

---

## 检查命令

```bash
# 看 Gradle 配置的版本
rg "version(Code|Name)" app/build.gradle.kts

# 看已有 tag
git tag -l

# 看远程 tag
git ls-remote --tags origin
```
