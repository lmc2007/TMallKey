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

## 字段说明

```json
{
  "channel": "stable",
  "versionName": "1.0.0",
  "versionCode": 1,
  "isReady": false,
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

## 发布一个新版本

1. 改 `fktm-android/app/build.gradle.kts` 里的 `versionCode` 与 `versionName`（**版本号唯一来源**）。
2. 新建 `update/changelogs/{新的 versionCode}.changelog`，写这一版改了什么。
3. 构建并签名 release APK：

   ```bash
   cd fktm-android
   ./gradlew assembleRelease
   ```

   产物在 `app/build/outputs/apk/release/app-release.apk`。
   签名参数从 `fktm-android/keystore.properties` 读取（该文件与 `.jks` 都不入库，
   **丢了就无法再覆盖安装**，务必自行备份）。

4. 算 SHA-256：

   ```bash
   sha256sum app/build/outputs/apk/release/app-release.apk
   ```

5. 在 GitHub 上建一个 tag 为 `v{versionName}` 的 Release，把 APK 传上去。
6. 把 `update/stable.json` 更新成新版本：`versionCode` 递增、`isReady: true`、
   填上 `releaseUrl`、`assets[].url`、`sha256`、`size`。
7. 提交并推送 `main` —— App 下次检查就能看到新版本。

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

