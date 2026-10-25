# CronPaw 整体性能检查与优化

2026 年 10 月 25 日，以 `f7a2c59`、0.3.9（versionCode 12）为基线。优化覆盖启动缓存、日历与 Notion 刷新、搜索、时间轴与月视图、后台轮播和小组件。最终 release APK 已保留数据覆盖安装到 Pixel Tablet，并验证启动、视图切换和日期跳转。

包体与启动耗时改善明确；本次滚动样本没有证明稳定的流畅度提升。以下使用最终交付包的数据，不取中间复测的最佳值。

## 实测条件与结果

设备为 Pixel Tablet，Android 17 / API 37，1600×2560 竖屏；ADB 端口 5038，明确指定设备。前后均为当前工作区构建的 debug 签名 release，使用相同系统日历、单日视图与手势序列。未清除应用数据，未修改个人日历事项，未运行 connectedAndroidTest。两次采样的系统 Thermal Status 均为 0。

| 指标 | 基线 | 最终交付包 | 解读 |
| --- | ---: | ---: | --- |
| APK | 14.38 MiB | 5.28 MiB | 减少 63.3% |
| Warm 启动中位数 | 237.5 ms，4 次 | 162 ms，5 次 | 本次样本减少 31.8% |
| 滚动前 TOTAL PSS | 129.4 MiB | 106.0 MiB | 单次内存快照 |
| 滚动后 TOTAL PSS | 137.6 MiB | 117.0 MiB | 单次内存快照，非内存峰值或泄漏证明 |
| 纵向滚动卡顿帧 | 3 / 446，0.67% | 3 / 444，0.68% | 基本持平 |
| 纵向 P95 / P99 | 14 / 25 ms | 14 / 18 ms | 本轮长尾缩短 |
| 横向翻页卡顿帧 | 8 / 385，2.08% | 7 / 387，1.81% | 样本差异很小 |
| 横向 P95 / P99 | 13 / 21 ms | 17 / 21 ms | P95 本轮变差，不宣称翻页稳定提升 |

启动流程为 force-stop、等待 0.5 秒、`am start -W`，每次启动后等待 3 秒。小组件可能先启动应用进程，因此不能把这些样本全部称为冷启动：基线的 5 次为 244、308、231、228、247 ms，其中 308 ms 标为 COLD，其余为 WARM；最终 158、162、159、169、163 ms 全为 WARM。表中启动对比只统计 WARM。纯冷启动、磁盘冷缓存和首次安装启动未得到足够受控样本。

每个滚动场景执行 8 次交替方向、350 ms 的滑动，读取 `dumpsys gfxinfo`。内存来自 `dumpsys meminfo`，会受 GC、后台任务和共享页变化影响。前台静置仍会发生顶部卡片每 5 秒切换，这是现有产品行为。

## 修改与问题对应

| 修改 | 解决的问题 |
| --- | --- |
| release 启用 R8 与资源裁剪；保留 Glance 类名 | 去除未使用代码与资源，稳定跨升级的小组件身份；保留原签名与版本 |
| 缓存读取与 JSON 解码移到 IO；小组件入口等待缓存就绪 | 避免 Application 主线程读磁盘，同时保持从小组件打开事项的语义 |
| 单一串行写入者，合并等待中的快照，使用 AtomicFile | 防止旧快照后完成覆盖新数据，以及中断写入留下半份 JSON |
| 相同输入的刷新共享任务，范围改变时取消旧任务 | 减少多个启动入口的重复查询；过期结果不再发布 |
| ContentResolver CancellationSignal 与可取消的 OkHttp 请求 | 取消刷新时释放底层查询和网络读取；Notion 错误回退不再吞掉协程取消 |
| 仅在发布内容改变时写缓存、通知小组件 | 消除无变化刷新引起的磁盘写入与 Glance 更新 |
| 主界面与本月小组件使用独立日期范围；重叠范围合并，远距离范围分开查询 | 避免两者互相替换窗口、重复刷新，也不为历史跳转查询中间数年的数据 |
| 小组件 worker 等待真实刷新完成，取消固定 4 秒等待；串行更新；无小组件时取消后台任务与闹钟 | 避免快请求白等、慢请求提前结束、重复渲染，以及无小组件时继续唤醒 |
| Notion 原始页面缓存限制为 1000 条，淘汰后详情可重新获取 | 防止长期浏览持续积累页面 JSON；清理对应写入时间记录 |
| 搜索在 Default 调度器执行，100 ms 合并输入，用有界堆保留前 60 项 | 避免在主线程对全部匹配项排序；保留评分、时间距离与同分顺序 |
| 日程只在显示或切换到日程时计算；日期索引只展开所需窗口 | 减少无关计算，同时修复跨 62 天长事项在后续日期不显示的问题 |
| 全天栏高度动画在布局阶段读取；当前时间线在绘制阶段读取；事项使用稳定 key | 降低动画与时钟刷新引起的重组，保留原视觉效果和交互 |
| 时间刷新与顶部轮播随 STARTED 生命周期运行 | 页面进入后台后暂停计时，返回前台更新当前时间 |
| 小组件勾选在 Main.immediate 更新共享状态 | 避免 Glance 后台回调与 UI 同时修改 Agenda / Notion 内存状态 |

R8 配置遵循 [Android 官方优化文档](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)；Compose 调整依据 [缓存计算、稳定 key 与延后状态读取的官方建议](https://developer.android.com/develop/ui/compose/performance/bestpractices)。未修改 Notion OAuth relay 的代码、配置或部署。

## 验证

执行：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew :app:assembleRelease :app:testDebugUnitTest :app:lintDebug --offline
```

release 构建成功。24 项单元测试通过，其中原有 6 项、新增 18 项；覆盖同请求合并、请求替换与调用方取消、串行写入与失败恢复、真实本地 HTTP socket 的取消关闭、20000 条搜索结果与原排序一致性、跨年长事项、零长度事项、午夜边界、夏令时及分离查询窗口。

真实设备检查包括 1 天、3 天、周、月、日程切换；横向与纵向滚动；日期搜索 `11.10` 跳至日程对应位置；从小组件日期 Intent 跳至 2023 年 10 月 17 日，等待后仍停留于所选日期。原有日历权限和数据保留。检查期间 crash buffer 未记录应用崩溃。后台约 32 秒的 5 次 top 采样中，应用 CPU 为 0.0% 到 0.1%；随后恢复前台成功（HOT，39 ms）。这只是短时静置结果，不能代替长期功耗测量。

Lint 实际执行完成，但仍有原有的 3 个 error、59 个 warning。三个 error 均在未修改的 `RichHaptics.kt`：两个 NewApi 检查无法推导 `canCompose()` 的 API 30 防护，另一个 WrongConstant 针对动态组装的振动 primitive 数组。构建现有配置为 `abortOnError = false`，因此构建成功不代表 lint 零错误。

设备未配置 Notion 数据库，所以 Notion 线上账号读取、限流、更新与 widget 勾选没有得到端到端验收；此部分验证限于取消测试、排序与缓存代码检查。API 26 兼容性、Pixel 11 Pro、长期电量、密集日程的设备帧表现，以及真实桌面上三类 widget 的完整交互仍需专项验证。没有新增账号、授权或修改真实任务来制造测试数据。

## 复现与产物

先将应用设为竖屏单日视图、停在今天、关闭抽屉和键盘，再运行：

```sh
python3 scripts/measure-performance.py \
  --adb /Users/gpo/Library/Android/sdk/platform-tools/adb \
  --port 5038 \
  --serial <serial> \
  --out captures/performance-repeat
```

脚本要求新的输出目录，明确校验屏幕尺寸。原始记录在 `captures/performance-2026-10-25/baseline/` 与 `delivery/`，中间复测单独保存；截图含个人日历数据，因此 `captures/` 继续受 gitignore 排除，不写入本报告。

交付 APK：`app/build/outputs/apk/release/app-release.apk`，5535056 字节。

SHA-256：`783a92b60a35eca1e07277b30761b539b139345ed2cc7b6cf48e2f9018e61d96`。

R8 映射：`app/build/outputs/mapping/release/mapping.txt`。未来发布该 APK 时应一并保留映射，以便还原崩溃栈。本次没有提交 Git commit、推送或发布版本。
