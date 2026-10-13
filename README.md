# 轻账 · QingJi

离线单机记账 Android 应用，只记支出。Kotlin + Jetpack Compose + Room，账单数据不上传；联网只有一处——打开应用时向更新源读一份版本清单，默认是本仓库 `main` 分支的 `update.json`，可以在「关于」页关掉。

包名 `com.qingxi.ledger`，当前版本 `0.1.0`（versionCode 1）。发布包见 [Releases](https://github.com/zhaosai6869/qingzhang/releases)。

## 功能

**记一笔**
- 金额用系统键盘输入，支持 `+ - × ÷` 算式（先乘除后加减，允许以运算符结尾）
- 分类宫格默认显示一行半，可展开/收起；分类 emoji 图标，内置 13 个支出分类，可改名换图标或增删自定义分类
- 备注、多标签、日期时间选择、「最近常用」一键套用（分类 + 备注 + 标签）
- 附件图片走系统相册选择器（Photo Picker），缩略图可点击全屏查看、左右翻页、单张删除

**流水**
- 月份翻页 + 当月汇总 + 按日分组列表
- 左滑单笔露出删除按钮，长按进入多选批量删除，所有删除都要二次确认且不可撤销
- 搜索页：关键词即时搜索（250ms 防抖）+ 分类 / 标签 / 日期区间筛选

**日历**：月度支出热力网格 + 选中日流水明细，周起始日可切换

**统计**：本月 / 近 3 月 / 近 6 月 / 近 12 月四个区间；支出趋势（自绘柱状 + 折线）、分类占比（自绘环形）、分类排行比例条；分类可下钻到区间明细；另有自然年年度报告（月度趋势、分类与标签排行、记录最密集的一天）

**我的**
- 外观：跟随系统 / 浅色 / 深色，5 套主题色
- 账单月起始日（1~28 日，统计与流水的「本月」按此口径）
- 备份与恢复、分类管理、应用锁、关于

**应用锁**
- 指纹/面容（系统 BiometricPrompt）为主，6 位数字 PIN 备用
- 只在每次打开应用（冷启动）时验证，退到后台再回来不再验证
- 可选隐藏最近任务缩略图（同时开启 FLAG_SECURE 禁止截屏）
- PIN 用 PBKDF2-HMAC-SHA256（120k 次迭代）派生后只存哈希，连错 5 次锁定 1 分钟

**在线更新**
- 更新源内置（本仓库 `update.json`），不用填地址；打开应用后自动检查一次，发现新版本在「我的 → 关于」上标出来
- 不想自动联网就在「关于 → 版本更新」里关掉「打开应用时自动检查」，关掉后仍可手动点「检查更新」
- 也支持自定义更新源地址（高级项，默认收起），填了就走自己的源
- 清单比对 `versionCode`，有新版则下载 APK 到应用缓存，SHA-256 校验通过后调起系统安装器
- 只接受 https 地址；缺 `sha256` 的清单直接拒绝，不安装无校验值的包

**桌面小组件**：今日支出金额 + 「记一笔」直达按钮（Glance）

## 技术栈

| 项 | 版本 |
| --- | --- |
| AGP | 9.4.0（内置 Kotlin 支持） |
| Kotlin | 2.4.10 |
| Gradle | 9.8.0（wrapper） |
| KSP | 2.3.12 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |
| Compose BOM | 2026.09.00（Material3） |
| Room | 2.8.5（schema v1，已导出） |
| Navigation Compose | 2.10.2 |
| DataStore Preferences | 1.2.1 |
| androidx.biometric + fragment | 1.1.0 / 1.8.7 |
| Glance（小组件） | 1.2.0 |
| Coil 3 | 3.6.3 |
| Gson | 2.13.2 |

单模块工程，无第三方DI（手写 `AppGraph` 服务定位器）；在线更新直接用 `HttpURLConnection`，不引入网络库。

## 工程结构

```
app/src/main/java/com/qingxi/ledger/
├── MainActivity.kt          导航壳：底栏 + NavHost + AppLockGate + FLAG_SECURE
├── QingJiApp.kt             Application
├── di/AppGraph.kt           仓库单例 + LocalAppGraph + appViewModel<VM>() 工厂
├── data/
│   ├── db/AppDatabase.kt    Room（qingji.db），建库时播种内置分类
│   ├── tx/                  TransactionEntity / Dao / Repository + Models.kt
│   ├── category/            CategoryEntity / Dao / Repository / CategorySeeder
│   ├── settings/            SettingsRepository（DataStore: qingji_settings）
│   ├── backup/              BackupRepository：JSON / 加密 .qjbak / CSV 导出与导入
│   ├── update/              UpdateRepository（清单解析 + 下载校验）与 UpdateController（进程级检查状态）
│   └── lock 在 ui/lock 下    应用锁偏好独立存于 qingji_lock
├── ui/
│   ├── record/  flow/  calendar/  stats/  settings/  lock/
│   ├── update/              UpdateViewModel + 关于页的「版本更新」区块
│   ├── theme/Theme.kt       动态调色板
│   └── common/AmountDisplay.kt
├── util/                    Dates / Money / AmountExpr / Crypto
└── widget/TodayWidget.kt    Glance 小组件 + Receiver
```

## 环境与构建

需要 JDK 17+（实测用 Android Studio 自带 JBR）与 Android SDK 37。`local.properties` 指向本机 SDK。

依赖仓库已配置阿里云镜像，日常构建可离线：

```bash
cd /d/ai/app
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug --offline --console=plain
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

安装到设备：

```bash
adb -s <serial> install -r -t app/build/outputs/apk/debug/app-debug.apk
```

### 发布签名

`app/build.gradle.kts` 从根目录的 `keystore.properties` 读取 release 签名，该文件与 `keystore/` 目录都已在 `.gitignore` 里，**不要提交、不要分享**。仓库克隆下来如果没有这个文件，release 构建会自动回退到 debug 签名，保证别人也能编出包。

本地要出正式包时，按下面格式建 `keystore.properties`：

```properties
storeFile=keystore/release.keystore
storePassword=<口令>
keyAlias=<别名>
keyPassword=<口令>
```

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleRelease --console=plain
```

产物：`app/build/outputs/apk/release/app-release.apk`。两个注意点：

- 首次跑 release 需要联网，`compose-group-mapping` 这个 release 专用依赖不在离线缓存里
- 发布密钥一旦对外发布就不能再换：换签名必须先卸载旧包，卸载会连带清空 `getExternalFilesDir` 里的备份文件，所以发版前先把备份导出到手机其他位置

### 发版流程

自动更新读的是仓库 `main` 分支的 `update.json`，所以**这个文件没同步，用户就收不到更新提示**。每次发版按顺序做完四步：

1. 改 `app/build.gradle.kts` 里的 `versionCode` / `versionName`
2. `./gradlew :app:assembleRelease`，把产物传到 GitHub Release
3. 用 release 资产的直链和 `sha256sum app-release.apk` 的结果改写根目录 `update.json`
4. 提交并 push `update.json`——push 之前 `https://raw.githubusercontent.com/.../main/update.json` 还是旧内容

## 数据与存储

| 内容 | 位置 |
| --- | --- |
| 账单与分类 | `databases/qingji.db`（Room，`transactions` + `categories`） |
| 界面偏好 | DataStore `qingji_settings` |
| 应用锁（PIN 哈希/盐、开关、失败计数） | DataStore `qingji_lock` |
| 附件图片 | `files/attachments/`（`att_<时间戳>.jpg`） |
| 备份文件 | `getExternalFilesDir("backup")`，文件名 `qingji_backup_yyyyMMdd_HHmm.<ext>` |
| 更新安装包 | `cache/update/`，装完或取消即清，只保留当前这一份 |

关键约定：
- **金额一律存 Long「分」**（`amountMinor`），显示层统一按元格式化，`Money.formatSigned` 给支出加负号
- `kind = 1` 支出；旧版本残留的 `kind = 2` 收入记录界面不再展示，但 DAO 的全量导出/计数保持完整，备份与导入可无损往返
- 标签与附件路径分别以逗号拼接在 `tags` / `attachments` 字段，`Models.kt` 提供 `joinTags/splitTags/joinPaths/splitPaths`
- 账单月区间统一走 `Dates.periodOf/periodStart/periodEnd`，起始日上限 28 以避免月末天数不足

备份格式：
- `.json` — 明文，`version/app/exportedAt/categories/transactions`
- `.qjbak` — 口令加密，`QJBAK1` magic + salt(12) + IV(12) + AES-256-GCM 密文，PBKDF2-HMAC-SHA256 210k 次迭代派生密钥
- `.csv` — 带 BOM，列：日期,时间,类型,分类,金额,备注,标签,来源

导入支持「覆盖」与「合并」两种模式，合并按 `时间|金额|类型|分类|备注` 去重。

## 更新源格式

更新源是一份 **https** JSON 清单。默认地址是仓库根目录的 `update.json`（`https://raw.githubusercontent.com/zhaosai6869/qingzhang/main/update.json`），用户也可以在「关于 → 版本更新」里换成自己托管的地址（对象存储、Releases 直链、静态站都行）：

```json
{
  "versionCode": 2,
  "versionName": "0.2.0",
  "apkUrl": "https://example.com/qingji/app-release-0.2.0.apk",
  "sha256": "64 位十六进制小写，对应 apkUrl 那个文件",
  "sizeBytes": 4521984,
  "notes": "这一版改了什么"
}
```

- `versionCode` 必填且要与 `android:versionCode` 一致，客户端只比较它，`versionName` 仅用于展示
- `sha256` 必填且必须是 64 位十六进制，缺失或格式不对就判定清单非法；下载完成后摘要不一致会删掉文件并报错
- `apkUrl` 同样只接受 https；`sizeBytes`、`notes` 可省略
- 发布时算校验值：`sha256sum app-release-0.2.0.apk`
- 下载走 `HttpURLConnection`，连接超时 15s、读取超时 30s，清单大小上限 256KB

## 隐私与安全

- 账单数据不上传、不采集、不上报；联网只有「读版本清单」一处，默认地址是本仓库的 `update.json`，冷启动发一次 GET，关掉自动检查后只剩手动点「检查更新」
- 权限只有三项：`USE_BIOMETRIC`、`INTERNET`、`REQUEST_INSTALL_PACKAGES`（后两项仅服务于在线更新）
- `allowBackup="false"`，且 `data_extraction_rules` 排除全部域，云端备份与设备迁移均不带走数据
- 指纹/面容由系统托管，应用不接触生物特征；PIN 与备份口令都只存派生结果
- 备份/恢复全程使用系统文件选择器（SAF），不申请传统存储权限
- 更新包落在应用私有缓存，通过 FileProvider 授权给系统安装器，跨应用读不到
- 「隐藏最近任务缩略图」开启后加 FLAG_SECURE，任务卡片显示空白且禁止截屏

## 开发约定

- MVVM：每个页面一个 `ViewModel`，通过 `appViewModel<VM>()` 取，作用域跟随导航返回栈条目
- `AppGraph` 只暴露仓库，ViewModel 构造函数必须**有且仅有一个 `AppGraph` 参数**（工厂用反射匹配，多参或换序会直接崩）；release 的 R8 靠 `proguard-rules.pro` 里那条 `-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }` 保住构造函数，改反射写法要同步改这条规则
- 数据库实例在 `MainActivity.onCreate` 里 `AppDatabase.init()` 初始化，小组件读库前也调它
- 注释写中文，只解释「为什么」；界面文案与 `onClickLabel` 均为中文无障碍标签
- `rememberNavController()` 必须挂在 `AppLockGate` **外层**——上锁会把 content 整棵移出组合，导航栈提到外面才能在解锁后原样恢复
- Room schema 导出到 `app/schemas/`，改表要升 `version` 并补 Migration（当前只有 `fallbackToDestructiveMigrationOnDowngrade`）

## 已知限制

- 无自动化测试，`app/src` 下只有 `main` 源集
- 生物识别验证链路（自动弹窗、手动点按）尚未在真机确认——测试用 AVD 没有指纹录入入口
- 在线更新的「读清单 → 比对 versionCode」已在模拟器上用真实 https 源跑通；「下载 APK → 校验 → 调起安装器」还没跑过，因为还没有第二个版本可下
- 测试 AVD 所在网络直连 `raw.githubusercontent.com` 会卡到超时（本机是通过 127.0.0.1 的代理才拿到的），所以自动检查在国内网络下大概率静默失败；换镜像源还是接受这个限制，尚未定
- 忘记 PIN 只能清除应用数据，唯一的出路是事先导出备份
- 不支持预算、资产账户、周期记账、多账本、云同步、收入记录
