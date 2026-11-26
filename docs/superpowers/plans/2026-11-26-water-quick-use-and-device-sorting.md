# 饮水页一键接水与设备排序开发计划

**目标**　用户打开饮水页后，点一次已保存设备旁的「解锁接水」即可发起操作，并能在「最近使用」和「自定义排序」之间切换。

**实现方式**　`WaterScreen` 展示设备列表、添加、管理和接水状态。`WaterViewModel` 串联单次操作并持有接水状态。`WaterDevices` 保存设备、使用时间和排序设置，沿用 Kotlin、Jetpack Compose Material 3 和 SharedPreferences。

## 交互约定

- 首屏展示「我的饮水机」，每台设备显示名称、位置及独立的「解锁接水」按钮。已添加设备可直接接水。无设备时突出「添加饮水机」。
- 「＋ 添加」提供扫码和手输。查询成功后按资源号保存设备并回到列表。拒绝相机权限时提供手输入口。
- 「饮水设置 → 设备排序」提供「最近使用」和「自定义排序」，默认选中「最近使用」。最近使用以成功解锁时间倒序排列，同一时间按设备记录原顺序排列。
- 首次切到自定义时，用当时的最近使用顺序作为起点。在「管理设备」中用「上移」「下移」调整并保存，取消可丢弃当前编辑。新设备追加到自定义顺序末尾，移除设备时清除对应排序记录。
- 排序方式及自定义顺序保存到本机。自定义模式下接水后设备位置保持固定，同时照常更新使用时间。切回最近使用时按最新时间排序，再切回自定义时恢复保存的顺序。
- 接水按钮先查所选设备的实时状态，再为该设备发起解锁。操作期间串行处理提交。解锁成功后显示正在使用的设备、「请按饮水机上的出水键」和「结束接水」。结束失败保留对应设备与订单，可在当前卡片重试。

## 1. 增加排序和持久化

涉及 `fktm-android/app/src/main/java/com/fktm/app/water/WaterDevices.kt`，新增 `fktm-android/app/src/test/java/com/fktm/app/water/WaterDeviceOrderTest.kt`。

- [ ] 写 `legacyRecordsUseRecentOrder`、`customOrderSurvivesModeRoundTrip`、`customOrderAppendAndRemove`、`customOrderRepairsDuplicateOrStaleIds`。用 A、B 两台设备验证 B 最近解锁时最近顺序是 B、A。保存自定义 A、B 后，B 再次解锁时自定义仍为 A、B。加入 C 追加到末尾，删除 A 后是 B、C。排序里重复或不存在的资源号应被清理。
- [ ] 在 `fktm-android` 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.fktm.app.water.WaterDeviceOrderTest"`，确认新增测试先暴露排序缺口。
- [ ] 增加 `WaterDeviceSortMode { RECENT, CUSTOM }`。`WaterDevices.sortMode(context: Context): WaterDeviceSortMode` 读取设置，`setSortMode(context: Context, mode: WaterDeviceSortMode)` 保存设置并在首次切到自定义时初始化顺序，`saveCustomOrder(context: Context, resNos: List<String>)` 保存按资源号排列的顺序。现有 `list(context: Context): List<Saved>` 根据模式返回设备。沿用设备记录及最多 30 台的上限，排序设置使用独立的本地键。将排序及资源号归并逻辑提取为可单测的纯函数。
- [ ] 重跑定向单测。补测满 30 台时添加第 31 台的反馈，重复扫码同一台仍更新原记录，原有设备及排序位置保留。

## 2. 串联一键解锁并保留当前订单

涉及 `fktm-android/app/src/main/java/com/fktm/app/vm/WaterViewModel.kt`，复用 `WaterApi.deviceInfo`、`WaterApi.start` 和 `WaterApi.stop`。新增 `fktm-android/app/src/test/java/com/fktm/app/vm/WaterQuickUseTest.kt`，通过本地替身记录调用。

- [ ] 写 `quickUseChecksAndStartsSameDevice`、`unavailableDeviceNeverStarts`、`rapidTapsStartAtMostOnce`、`failedStopRetainsActiveDevice`。断言选 A 时依次查询 A、至多解锁 A 一次，设备离线、停用、非营业时解锁调用次数为 0，结束请求失败后当前设备及订单号仍指向 A。
- [ ] 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.fktm.app.vm.WaterQuickUseTest"`，确认新用例先暴露流程缺口。
- [ ] 增加 `quickUse(context: Context, resNo: String)`，以所选资源号串联会话检查、设备查询和解锁。进行中的操作绑定资源号，查询失败回到该设备的错误状态。服务端成功时记录当前接水及可用订单标识，更新最近使用时间。结束操作使用当前接水设备和订单标识，成功后清理当前接水。解锁请求超时只提示结果未确认，避免自动重发。
- [ ] 将设备录入收敛到 `addDevice(context: Context, input: String)`，沿用二维码和手输解析并复用设备查询。保存成功后刷新列表，添加路径上的服务端解锁调用次数为 0。设备重复添加时按资源号更新，满额时提示通过管理页先移除设备。
- [ ] 接入排序与列表刷新，提供 `setSortMode(context: Context, mode: WaterDeviceSortMode)` 和 `saveCustomOrder(context: Context, resNos: List<String>)`。登录过期时保留设备列表，并把用户引到现有登录入口。
- [ ] 重跑定向单测，覆盖解锁返回空订单号时仍展示结束入口，以及切换页面返回后当前接水设备保持正确。

## 3. 重构 Compose 界面

涉及 `fktm-android/app/src/main/java/com/fktm/app/ui/WaterScreen.kt` 与 `fktm-android/app/src/main/java/com/fktm/app/ui/QrScannerScreen.kt`。

- [ ] 用「我的饮水机」替换现有主区域。每行按资源号绑定 `quickUse`。显示对应设备的查询或解锁进度，并把错误反馈放在同一设备旁。空列表显示添加操作。
- [ ] 在「＋ 添加」中接入扫码和手输。扫码权限被拒绝时，扫码页的「手动输入」直接打开输入界面。扫码和手输共用 `addDevice`。
- [ ] 在「饮水设置」提供两个排序单选项。在「管理设备」提供删除，自定义模式下提供「上移」「下移」「保存」「取消」，首尾方向按钮按边界禁用。使用带设备名的无障碍标签。
- [ ] 接水中显示当前设备、物理出水提示与「结束接水」。结束时显示进度，结束失败留在该卡片，其他设备操作等当前流程结束后恢复。

## 4. 回归与验收

- [ ] 在 `fktm-android` 运行 `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`。要求单测通过、Debug APK 生成，并运行 `git diff --check`。
- [ ] 在 Android 设备或模拟器验证空列表、新增设备、重复扫码、手输校验、相机权限被拒绝、切换排序、自定义移动及取消。重启应用后确认排序模式与自定义顺序恢复。
- [ ] 用有权限的饮水机验证已添加设备从首屏点一次即可进入解锁流程，解锁后按物理出水键接水，App 中可找到「结束接水」。验证两种排序在成功接水后的表现，以及网络失败时界面可恢复。
