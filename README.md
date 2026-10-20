# 轻账 · QingJi

离线单机记账 Android 应用，只记支出。Kotlin + Jetpack Compose + Room，账单数据不上传；联网只有一处——打开应用时向更新源读一份版本清单，默认是本仓库 `main` 分支的 `update.json`，连不上时依次改走公开镜像读同一份文件，可以在「关于」页关掉。

包名 `com.qingxi.ledger`，当前版本 `0.2.0`（versionCode 3）。发布包见 [Releases](https://github.com/zhaosai6869/qingzhang/releases)。

## 功能

**记一笔**
- 金额用系统键盘输入，支持 `+ - × ÷` 算式（先乘除后加减，允许以运算符结尾）
- 分类宫格默认显示一行半，可展开/收起；分类 emoji 图标，内置 8 个支出分类（餐饮 / 交通 / 购物 / 居住 / 娱乐 / 医疗 / 通讯 / 其他），可改名换图标或增删自定义分类
- 备注、多标签、日期选择（只选年月日，时分沿用当前时刻或原账单时间）
- 附件图片走系统相册选择器（Photo Picker），缩略图可点击全屏查看、左右翻页、单张删除

**流水**
- 月份翻页 + 当月汇总 + 按日分组列表
- 左滑单笔露出删除按钮，长按进入多选批量删除，所有删除都要二次确认且不可撤销
- 搜索页：关键词即时搜索（250ms 防抖）+ 分类 / 标签 / 日期区间筛选

**日历**：月度支出热力网格 + 选中日流水明细，周起始日可切换

**统计**：本月 / 近 3 月 / 近 6 月 / 近 12 月四个区间；支出趋势（自绘柱状 + 折线）、分类占比（自绘环形）、分类排行比例条；分类可下钻到区间明细；另有自然年年度报告（月度趋势、分类与标签排行、记录最密集的一天）

**周期记账**
- 规则三种频率：每天 / 每周（勾选周几）/ 每月（选几号），金额为固定值，带分类、备注与标签
- 结束条件三选一：无限 / 到指定日期 / 共 N 次
- 到期项**只在冷启动时计算**，只列最近 90 天内的，生成「待确认」清单；点确认才写进账单，跳过表示这笔不记、之后不再提醒
- 规则本身不等于已发生的支出，规则页与「我的」只显示规则条数，不做任何金额合计
- 幂等：`transactions` 上 `(ruleId, dueDayKey)` 唯一索引 + `OnConflictStrategy.IGNORE`，同一期重复计算也只入一笔

**我的**
- 外观：跟随系统 / 浅色 / 深色，5 套主题色
- 账单月起始日（1~28 日，统计与流水的「本月」按此口径）
- 备份与恢复、分类管理、周期记账、应用锁、关于

**应用锁**
- 指纹/面容（系统 BiometricPrompt）为主，6 位数字 PIN 备用
- 只在每次打开应用（冷启动）时验证，退到后台再回来不再验证
- 可选隐藏最近任务缩略图（同时开启 FLAG_SECURE 禁止截屏）
- PIN 用 PBKDF2-HMAC-SHA256（120k 次迭代）派生后只存哈希，连错 5 次锁定 1 分钟

**在线更新**
- 更新源内置（本仓库 `update.json`），不用填地址；打开应用后自动检查一次，发现新版本在「我的 → 关于」上标出来
- 内置地址按候选顺序依次试：`raw.githubusercontent.com` → `gh-proxy.com` → `ghfast.top` → `jsDelivr`，任一读到就停；命中镜像时安装包也走同一镜像，SHA-256 照旧校验
- 每个源最多等 12 秒：`connectTimeout` 约束的是「一次 connect」而不是「一次请求」（DNS 无时限，OkHttp 还会对解析出的每个地址各试一遍），卡住的源由这个时限放弃等待，否则备胎永远走不到；四个全挂最坏约 48 秒，后台静默进行
- 不想自动联网就在「关于 → 版本更新」里关掉「打开应用时自动检查」，关掉后仍可手动点「检查更新」
- 也支持自定义更新源地址（高级项，默认收起），填了只试这一个、不再回退内置，清空即回到内置
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
| Room | 2.8.5（schema v3，`1.json` / `2.json` / `3.json` 均已导出） |
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
│   ├── recurring/           RecurringRuleEntity / Dao / Repository + RecurringDue（到期计算）/ RecurringPendingStore（待确认清单）
│   ├── update/              UpdateRepository（清单解析 + 下载校验）与 UpdateController（进程级检查状态）
│   └── lock 在 ui/lock 下    应用锁偏好独立存于 qingji_lock
├── ui/
│   ├── record/  flow/  calendar/  stats/  settings/  lock/  recurring/
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

内置的三级镜像只是代理同一个 raw URL，不需要为它们上传第二份文件、也不需要在镜像侧做任何操作；`update.json` push 上去它们就能读到。唯一例外是 `jsDelivr` 对 `@main` 有约一天的缓存，极端情况下新版本要晚一天才被发现（实测：push 之后 gh-proxy 立刻给出新清单，jsDelivr 仍返回旧清单）。

本机没装 `gh` CLI，第 2 步的建 Release 与传资产是直接用 Git 凭据管理器里已存的令牌调 API 做的：

```bash
TOKEN=$(printf "protocol=https\nhost=github.com\n" | git credential fill | grep '^password=' | cut -d= -f2-)
# 建 Release（请求体里有中文，必须写成 UTF-8 文件再 @，命令行内联 JSON 会被转码弄坏）
curl --ssl-no-revoke -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary "@release-body.json" https://api.github.com/repos/zhaosai6869/qingzhang/releases
# 传资产（name 要和 update.json 的 apkUrl 尾段一致）
curl --ssl-no-revoke -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary "@app/build/outputs/apk/release/app-release.apk" \
  "<返回的 upload_url>?name=app-release.apk"
```

上传成功后 GitHub 会回资产的 `digest: sha256:...`，拿它和 `sha256sum` 的本地结果对一遍，能确认传上去的就是这个文件。

## 数据与存储

| 内容 | 位置 |
| --- | --- |
| 账单与分类 | `databases/qingji.db`（Room，`transactions` + `categories`） |
| 周期规则与跳过记录 | 同一库的 `recurring_rules` + `recurring_dismissals` |
| 界面偏好 | DataStore `qingji_settings` |
| 应用锁（PIN 哈希/盐、开关、失败计数） | DataStore `qingji_lock` |
| 附件图片 | `files/attachments/`（`att_<时间戳>.jpg`） |
| 备份文件 | `getExternalFilesDir("backup")`，文件名 `qingji_backup_yyyyMMdd_HHmm.<ext>` |
| 更新安装包 | `cache/update/`，装完或取消即清，只保留当前这一份 |

关键约定：
- **金额一律存 Long「分」**（`amountMinor`），显示层统一按元格式化，`Money.formatSigned` 给支出加负号
- `kind = 1` 支出；旧版本残留的 `kind = 2` 收入记录界面不再展示，但 DAO 的全量导出/计数保持完整，备份与导入可无损往返
- 手动记的流水 `ruleId`/`dueDayKey` 都留 NULL——SQLite 里 NULL 之间不算冲突，唯一索引只约束周期生成的那几笔
- 标签与附件路径分别以逗号拼接在 `tags` / `attachments` 字段，`Models.kt` 提供 `joinTags/splitTags/joinPaths/splitPaths`
- 账单月区间统一走 `Dates.periodOf/periodStart/periodEnd`，起始日上限 28 以避免月末天数不足

备份格式：
- `.json` — 明文，`version = 2`，字段 `version/app/exportedAt/categories/transactions/rules/dismissals`；旧版 v1 备份缺 `rules`/`dismissals` 两段，导入时只当「没有周期规则」，恢复照常
- `.qjbak` — 口令加密，`QJBAK1` magic + salt(12) + IV(12) + AES-256-GCM 密文，PBKDF2-HMAC-SHA256 210k 次迭代派生密钥
- `.csv` — 带 BOM，列：日期,时间,类型,分类,金额,备注,标签,来源

导入支持「覆盖」与「合并」两种模式，合并按 `时间|金额|类型|分类|备注` 去重。合并导入的规则会重新发号，跳过记录跟着改 `ruleId` 指向，否则会指到别的规则上。

## 更新源格式

更新源是一份 **https** JSON 清单。默认地址是仓库根目录的 `update.json`（`https://raw.githubusercontent.com/zhaosai6869/qingzhang/main/update.json`，连不上时依次改走 `gh-proxy.com`、`ghfast.top`、`jsDelivr` 三个公开镜像读同一份文件），用户也可以在「关于 → 版本更新」里换成自己托管的地址（对象存储、Releases 直链、静态站都行，填了只试这一个、不回退内置）：

```json
{
  "versionCode": 3,
  "versionName": "0.2.0",
  "apkUrl": "https://github.com/zhaosai6869/qingzhang/releases/download/v0.2.0/app-release.apk",
  "sha256": "64 位十六进制小写，对应 apkUrl 那个文件",
  "sizeBytes": 3106295,
  "notes": "这一版改了什么"
}
```

- `versionCode` 必填且要与 `android:versionCode` 一致，客户端只比较它，`versionName` 仅用于展示
- `sha256` 必填且必须是 64 位十六进制，缺失或格式不对就判定清单非法；下载完成后摘要不一致会删掉文件并报错
- `apkUrl` 同样只接受 https；`sizeBytes`、`notes` 可省略
- 发布时算校验值：`sha256sum app/build/outputs/apk/release/app-release.apk`（这个目录会被下次构建清掉，发版前先把产物拷出去留档）
- 下载走 `HttpURLConnection`，连接超时 15s、读取超时 30s，清单大小上限 256KB；读清单另有 12s 的**每源总时限**，因为上面两个超时都管不到 DNS 与逐地址重试

## 隐私与安全

- 账单数据不上传、不采集、不上报；联网只有「读版本清单」一处，默认地址是本仓库的 `update.json`，连不上时按候选顺序最多再问三个公开镜像，读的仍是同一份文件，关掉自动检查后只剩手动点「检查更新」
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
- Room schema 导出到 `app/schemas/`，改表要升 `version` 并补 Migration（v1→v2 是 `MIGRATION_1_2`；v2→v3 是 `MIGRATION_2_3`，表结构没动、只精简内置分类，另配 `fallbackToDestructiveMigrationOnDowngrade`）

## 已知限制

- 无自动化测试，`app/src` 下只有 `main` 源集
- 生物识别验证链路（自动弹窗、手动点按）尚未在真机确认——测试用 AVD 没有指纹录入入口
- 在线更新的「读清单 → 比对 versionCode」已在模拟器上用真实 https 源跑通；「下载 APK → 校验 → 调起安装器」仍未在设备上跑过。v0.1.1 就是为这条链路准备的包，v0.2.0 的清单同样把 `sha256`/`sizeBytes` 与 Release 资产两侧核对过（GitHub 直链与镜像都返回同一摘要），但这只证明包可下、URL 拼接正确，不代表应用内的下载与安装弹窗已验证
- release 包（R8 全量混淆）只验证到「能编出来、签名正确、`versionCode/versionName` 写对」，没有装到设备跑过：模拟器上装的是 debug 签名，换成 release 签名必须先卸载，会连带清空测试账本数据。真机验证走的是 debug 包
- 内置源的回退链路只验证了「读不通就放弃」这一半：四个候选各等 12 秒后被放弃、界面回到「检查更新」，取消也能中断；「主源超时后由镜像读成功」这半还没跑过——验证时那台 AVD 整机没有外网（IP ping 与 DNS 都不通），拿不到任何一条成功响应
- `repository.download()` 仍是「一次 connect 打断不了」的老形状：下载阶段的取消要等各地址的 `connectTimeout`（15 秒）逐个走完，比检查阶段更拖
- jsDelivr 备胎对 `@main` 有约一天缓存，极端情况下会晚一天才发现新版本
- 忘记 PIN 只能清除应用数据，唯一的出路是事先导出备份
- 周期记账不做后台唤醒：到期项只在冷启动算一次，应用长期不打开就一直不提醒；清单只回溯 90 天，比这更早的到期项视为永久漏记，不会再列出
- 不支持预算、资产账户、多账本、云同步、收入记录
