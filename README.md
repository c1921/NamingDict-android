# 起名字典 NamingDict

一款面向中文起名场景的离线字典 Android 应用。  
应用收录《通用规范汉字表》全部 `8105` 个汉字，支持多维筛选、收藏、起名方案编辑，以及 WebDAV 备份恢复。

## 功能特性

- 多维筛选
  - 按 `部首`、`总笔画`、`部外笔画`、`结构类型`、`声母`、`韵母`、`声调` 过滤字典
- 字典浏览
  - 筛选结果与收藏列表双模式切换
  - 保留两种列表的滚动位置
- 单字搜索
  - 顶部搜索仅接受单个字符输入，快速定位目标字
- 收藏与详情
  - 列表与详情页都可收藏/取消收藏
  - 详情页展示拼音、结构信息、释义等字段
- 起名方案（姓名页）
  - 维护姓氏（最多 4 个字符）
  - 支持多个方案，单字名/双字名切换，性别标记（通用/男/女）
  - 可从收藏字中快速填入当前姓名位
- 数据持久化与同步
  - 本地使用 DataStore 保存筛选条件、收藏顺序、起名草稿、滚动状态等
  - WebDAV 同步 `favorites.json` 与 `name_plans.json`
  - 变更后自动同步（约 30 秒延迟）+ 手动上传/下载
  - WebDAV 密码通过 `EncryptedSharedPreferences` 加密存储
  - WebDAV 地址强制要求 `https://`

## 技术栈

- Kotlin `2.0.21`
- Android Gradle Plugin `9.0.0`
- Gradle `9.1.0`
- Jetpack Compose（Material 3）
- kotlinx.serialization
- OkHttp
- AndroidX DataStore Preferences
- AndroidX Security Crypto
- JUnit4 + MockK + Robolectric + MockWebServer

## 环境要求

- Android Studio（建议最新稳定版）
- JDK `21`（CI 使用 Temurin 21）
- Android SDK
  - `minSdk = 24`
  - `targetSdk = 36`
  - `compileSdk = 36`

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/c1921/NamingDict.git
cd NamingDict
```

### 2. 构建 Debug 包

Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS / Linux:

```bash
./gradlew :app:assembleDebug
```

### 3. 安装到设备（可选）

Windows:

```powershell
.\gradlew.bat :app:installDebug
```

macOS / Linux:

```bash
./gradlew :app:installDebug
```

## 测试与代码质量

Windows:

```powershell
.\gradlew.bat lintDebug testDebugUnitTest
```

macOS / Linux:

```bash
./gradlew lintDebug testDebugUnitTest
```

主要测试位于：

- `app/src/test/java/io/github/c1921/namingdict/data/`
- `app/src/test/java/io/github/c1921/namingdict/ui/`

## WebDAV 备份恢复说明

在应用内进入「设置 -> 备份与恢复」后配置：

- 服务器地址（必须是 `https://`）
- 用户名
- 密码

默认会在服务端目录下读写：

- `NamingDict/favorites.json`
- `NamingDict/name_plans.json`

说明：

- 首次上传会尝试创建 `NamingDict` 目录（`MKCOL`）。
- 下载会覆盖本地收藏与起名方案草稿。

## 项目结构

```text
.
├─ app/
│  ├─ src/main/java/io/github/c1921/namingdict/
│  │  ├─ data/        # 数据加载、偏好存储、WebDAV 同步
│  │  └─ ui/          # Compose 页面与状态管理
│  ├─ src/main/assets/data/
│  │  ├─ dict.json    # 字典主数据
│  │  └─ index.json   # 筛选索引
│  └─ src/test/       # 单元测试 / Robolectric 测试
├─ .github/workflows/ # CI/CD
└─ gradle/
```

## 许可证

本项目采用 `GNU GPL v3.0` 协议。
