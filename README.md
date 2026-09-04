# HuHoBotPerformance

HuHoBot 的单服性能监控 Addon。QQ群发送 `/性能监控` 后，插件在 Bukkit 主线程抓取一份轻量快照，在独立线程中绘制 1200×900 的“运行核心”图片，并通过 HuHoBot 当前分支可用的图片字节入口回复。

## 兼容目标

- Spigot/Paper 1.16.5 及以上，Java 8 字节码。
- HuHoBot/PenguinClient 主分支：使用原生 `BaseCommand` 注册。
- PenguinAgent AGENT 分支：自动识别 `Addon` API，携带名称、版本、描述和作者注册。
- 自定义 QQ 命令名或较旧分支：回退到 `registerBotCommand` 与 `OnBotCommand` 事件入口。
- 图片发送依次尝试事件原生字节方法、QQ SDK `MessageChain`、QQ OpenAPI `file_data` 上传。

插件不依赖 HuHoBotOnlineList，也不会在运行时读取另一个 Addon 的资源。

## 指标来源

- TPS：优先读取服务端公开入口和兼容字段，最后使用插件自己的 60 秒 tick 采样器。
- MSPT：Paper/服务端提供的真实平均 tick 时间用于内部健康判断；界面参照 MiniHUD，以 `1000 / TPS` 显示明确标注的“预估MSPT”。纯 Spigot 没有真实 MSPT 入口时，仍可显示预估值，但不会将它冒充真实采样值。
- 系统 CPU、进程 CPU：JVM 提供的操作系统管理接口；不支持时显示 `--`。
- 运行内存：当前 Java 进程已用堆内存与最大堆内存。
- 在线玩家：Bukkit 当前在线人数与服务器人数上限。

## 安装

1. 安装并配置 HuHoBot 的 Spigot 适配器。
2. 将 `HuHoBotPerformance-*.jar` 放入服务器 `plugins` 目录。
3. 重启服务器，在 QQ 群发送 `/性能监控`。

首次启动生成 `plugins/HuHoBotPerformance/config.yml`。可以修改服务器名称、QQ 命令、冷却时间、字体和告警阈值。

## 本地预览

服务器控制台或拥有 `huhobotperformance.admin` 权限的玩家可以生成四类预览：

```text
/huhobotperformance preview healthy
/huhobotperformance preview warning
/huhobotperformance preview critical
/huhobotperformance preview unavailable
```

图片写入 `plugins/HuHoBotPerformance/preview-状态.png`，不会发送 QQ 消息。

不启动服务器时也可以直接运行：

```powershell
.\gradlew.bat renderPreview
```

输出为 `build/preview/performance-preview-v1.png`。

## 构建与测试

```powershell
.\gradlew.bat test jar renderPreview
```

发布 JAR 位于 `build/libs/`。构建会把仅用于编译的 HuHoBot ABI 桩排除在最终 JAR 外。
