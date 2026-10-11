# update/ —— 更新清单

客户端（TMKey App）从这里拉取更新信息。

**清单地址**（写死在 `UpdateSources.MANIFEST_URL`）：

```
https://raw.githubusercontent.com/lmc2007/TMallKey/main/update/stable.json
```

`raw.githubusercontent.com` 在国内经常连不上，所以 App 会先走 6 个第三方 GitHub
加速镜像，全部失败才回落直连。改仓库名或分支时，**两处都要改**：

- 本目录的实际位置
- `fktm-android/app/src/main/java/com/fktm/app/update/UpdateSources.kt` 里的 `MANIFEST_URL`

## 目录结构

```
update/
├── stable.json              客户端实际读取的清单（发布状态的真源）
├── changelogs/
│   └── {versionCode}.changelog   每个版本的更新日志，按 versionCode 命名
└── README.md
```

发版相关的 CI 与脚本在仓库根的 `.github/` 下：

```
.github/
├── workflows/
│   ├── prepare-release.yml      改版本号 + 写 changelog + 清单置为灰度中
│   └── release.yml              构建 → 签名 → 建 Release → 清单置为可下载
└── scripts/
    ├── prepare-version-update.py           ↑ 上面两个流程的核心逻辑
    ├── write-published-update-manifest.py  登记已发布的 APK
    ├── validate-update-manifest.py         校验清单 + changelog
    ├── update_manifest_common.py           清单字段顺序等共用规则
    └── export-signing-secrets.sh           导出 CI 需要的签名 Secrets
```

## 字段说明

```json
{
  "channel": "stable",
  "versionName": "1.0.0",
  "versionCode": 1,
  "isReady": true,
  "minVersionCode": 1,
  "releaseNotes": "简介，纯文本",
  "releaseUrl": "https://github.com/lmc2007/TMallKey/releases/tag/v1.0.0",
  "assets": [
    {
      "type": "universal-apk",
      "platform": "android",
      "url": "https://github.com/lmc2007/TMallKey/releases/download/v1.0.0/TMKey-1.0.0.apk",
      "sha256": "APK 的 SHA-256（小写十六进制）",
      "size": 12345678
    }
  ]
}
```

（这是**已发布**的形态。发版过程中会先经过一段 `isReady: false` + `assets: []` 的
「灰度中」状态，见下面第 1 步。）

| 字段 | 说明 |
|---|---|
| `versionCode` | **必填**。与 App 的 `versionCode` 比较判新旧，每次发版必须递增 |
| `versionName` | **必填**。展示给用户的版本号 |
| `isReady` | `false` 表示还在灰度/未上传完成 —— App 只提示「灰度中」，**不提供下载**。发版成功后才置 `true` |
| `minVersionCode` | 可选。为将来的强制更新留的下界，当前 App 只读取不强制 |
| `releaseNotes` | 纯文本简介。拿不到远端 changelog 时作为兜底 |
| `releaseUrl` | 「打开发布页」按钮的目标 |
| `assets[].type` | Android 只认 `apk` / `universal-apk` |
| `assets[].sha256` | **正式发布必须填**。App 下载后会逐字节校验，不一致直接判失败 |
| `assets[].size` | 可选，用于下载进度条的分母 |

## 发布一个新版本（CI 自动化）

发布拆成两步，对应两个手动触发的 workflow：

| 步骤 | workflow | 做什么 |
|---|---|---|
| 1 | **Prepare Version Update** | 改版本号 → 写 changelog → 清单置 `isReady=false`（客户端显示「灰度中」） |
| 2 | **Release APK** | 构建 → 签名 → 建 Release → 清单置 `isReady=true`（可下载） |

两步分开的意义：**清单永远指向一个已经存在的产物**。第 1 步跑完，客户端只会提示
「有新版在灰度中」，不会点到一个还不存在的下载地址；第 2 步把包装上去之后才开闸。

### 0. 一次性配置：签名密钥

`Release APK` 需要下面 4 个仓库 Secrets（Settings → Secrets and variables → Actions）：

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | `fktm-android/tmkey-release.jks` 的 base64（不换行） |
| `KEYSTORE_PASSWORD` | keystore 口令 |
| `KEY_ALIAS` | 通常为 `tmkey` |
| `KEY_PASSWORD` | key 口令 |

本地导出（会打印上面 4 项的值，注意别把输出外传）：

```bash
bash .github/scripts/export-signing-secrets.sh
```

> **换签名文件的后果**：已装老版本的用户无法覆盖安装（系统会因签名不一致拒绝升级），
> 只能卸载重装。`Release APK` 里钉死了证书指纹
> `14783decaa2141edc41e8864a0c6535939a36cffbd43ab3ddcc7a17454572906`，
> 对不上会直接失败，不会静默发出去。

### 1. 跑 Prepare Version Update

Actions → Prepare Version Update → Run workflow：

| 输入 | 说明 |
|---|---|
| `version_name` | 留空 = 当前补丁号 +1（例如 `1.0.0` → `1.0.1`） |
| `version_code` | 留空 = 当前 +1。**必须递增**，客户端靠它判新旧 |
| `changelog_mode` | `auto` = 从上次发版以来的提交自动整理；`auto+extra` = 自动 + 补充；`manual` = 只用手写内容 |
| `changelog_extra` | 补充 / 手写的更新内容，多行。`manual` 模式必填 |
| `release_notes` | 清单里的一句话简介，留空自动取第一条要点 |
| `dry_run` | 只预览，不写文件、不提交。第一次可以先这样跑一遍 |
| `trigger_release_after` | 勾上 = 准备完自动接着触发 Release APK |

它改三个文件并提交到 `main`：

```
fktm-android/app/build.gradle.kts            versionCode / versionName
update/stable.json                           versionCode / versionName，isReady=false，assets 清空
update/changelogs/{versionCode}.changelog    新建
```

### 2. 跑 Release APK

Actions → Release APK → Run workflow（`dry_run` 勾上则只构建、不发布）。

1. 校验 `update/stable.json` 与 `build.gradle.kts` 的版本号一致 —— 对不上直接失败；
2. 建 tag `v{versionName}` 并推到远端；
3. 从 Secrets 还原 keystore，`./gradlew :app:assembleRelease`；
4. `apksigner verify` 验签，并核对证书指纹；
5. `gh release create` 建 Release，上传 `TMKey-{versionName}.apk`；
6. 把 `update/stable.json` 改成 `isReady=true`，填上 `releaseUrl` / `assets`；
7. 提交推回 `main`。

跑完客户端再点「检查更新」就能看到新版本。

> tag 用 `v{versionName}` 而不是 versionCode。重跑同一个版本时，如果 tag 已经指向
> **别的**提交，流程会拒绝执行 —— 那意味着这个版本可能已经发布过。

### 本地手动发布（不用 CI 的备选路径）

1. 改 `fktm-android/app/build.gradle.kts` 的 `versionCode` / `versionName`；
2. 新建 `update/changelogs/{新的 versionCode}.changelog`；
3. `cd fktm-android && ./gradlew assembleRelease`，产物在
   `app/build/outputs/apk/release/app-release.apk`（签名参数从 `fktm-android/keystore.properties`
   读取，该文件与 `.jks` 都不入库，**丢了就无法再覆盖安装**）；
4. 在 GitHub 上建 tag `v{versionName}` 的 Release，把 APK 传上去；
5. 用脚本登记清单（等价于 CI 的第 6 步）：

   ```bash
   APK=fktm-android/app/build/outputs/apk/release/app-release.apk
   MANIFEST_FILE=update/stable.json \
   RELEASE_URL="https://github.com/lmc2007/TMallKey/releases/tag/v1.0.1" \
   ASSET_URL="https://github.com/lmc2007/TMallKey/releases/download/v1.0.1/TMKey-1.0.1.apk" \
   APK_NAME="TMKey-1.0.1.apk" \
   APK_SHA256="$(sha256sum "$APK" | awk '{print $1}')" \
   APK_SIZE="$(stat -c%s "$APK")" \
     python3 .github/scripts/write-published-update-manifest.py
   ```

6. 校验再提交：

   ```bash
   python3 .github/scripts/validate-update-manifest.py update/stable.json --require-ready
   ```

> `assets[].url` 必须是**能匿名直接下载**的地址。GitHub Release 的
> `/releases/download/...` 地址可以，网页地址不行。

## 本地自测

**A. 只验证「检查更新」链路**：保持 `stable.json` 的 `versionCode` 等于 App 的版本号，
点「检查更新」应显示「已是最新版本」。这能验证镜像可达 + 清单解析 + 陈旧守卫三件事。

**B. 验证「下载并安装」全链路**：把 `stable.json` 临时改成更高版本，并指向一个**真实存在**的 APK：

```json
{
  "channel": "stable",
  "versionName": "1.0.1",
  "versionCode": 2,
  "isReady": true,
  "releaseUrl": "https://github.com/lmc2007/TMallKey/releases/tag/v1.0.0",
  "assets": [{
    "type": "universal-apk",
    "platform": "android",
    "url": "https://github.com/lmc2007/TMallKey/releases/download/v1.0.0/TMKey-1.0.0.apk",
    "sha256": "填该 APK 的真实 SHA-256",
    "size": 1434410
  }]
}
```

`versionCode` 只要比当前安装的高，App 就会提示更新；下载的是同一个 APK 也没关系 ——
安装时系统会因为「签名相同 + 版本号不高于已装版本」而拒绝，这正好说明**签名链是通的**
（AOSP 会提示「应用未安装，已存在具有相同包名的应用」这种是签名不一致的情况）。
测完记得把 `versionCode` 改回真实值。

**快速验证镜像是否可达**（在你自己机器上跑，本机执行环境的网络策略会拦住这些域名）：

```bash
RAW="https://raw.githubusercontent.com/lmc2007/TMallKey/main/update/stable.json"
for h in github.cnxiaobai.com gh.halonice.com ghproxy.sakuramoe.dev \
         gh.padao.fun gh.jasonzeng.dev ghproxy.mirror.skybyte.me; do
  printf '%-32s ' "$h"
  curl -s -o /dev/null -w '%{http_code}  %{time_total}s\n' --max-time 6 \
    -A "TMKey-Updater" "https://$h/$RAW"
done
```

返回 `200` 且耗时短的即为可用节点。若某个镜像长期不可用，从
`UpdateSources.MIRROR_HOSTS` 里删掉，并同步更新本文件。

> 已知风险：6 个镜像里 `gh.jasonzeng.dev`、`ghproxy.mirror.skybyte.me`
> 在 2026-10-11 检查时 DNS 解析不出记录，可能是已下线。App 端靠「上次成功的源轮转到队首」
> 来规避 —— 一旦某次检查成功，之后都会先试那个可用节点，不会每次都白等 6 个超时。
> 确认这两个镜像彻底失效后，建议从列表中移除。

## 修改镜像列表时要同步的地方

| 文件 | 内容 |
|---|---|
| `UpdateSources.MIRROR_HOSTS` | 检查用的加速镜像 |
| `UpdateSources.DOWNLOAD_PROXIES` | APK 下载用的加速代理（与检查列表是两回事） |
| 本文件 | 上面的说明与自测脚本 |

