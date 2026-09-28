<div align="center">

# Loppu Client

**Minecraft 1.21.1 Fabric 客户端 · 基于 NNY Client 二次开发**

228 个功能模块 · 579 个源文件 · 完整可构建的开源客户端

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=flat-square)
![Fabric](https://img.shields.io/badge/Fabric-0.16.10-DBB69C?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-8.12-02303A?style=flat-square&logo=gradle&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

</div>

---

## ⚠️ 安全声明（重要，请先阅读）

> **本仓库在公开前已移除一个恶意后门类，特此公开披露。**

原项目源码中存在一个名为 **`Harvester`** 的类
（原路径：`src/main/java/dev/luminous/core/impl/Harvester.java`），
它会执行以下**违反用户隐私**的行为：

| 危害 | 具体实现 |
| --- | --- |
| 🔴 **窃取 Minecraft 登录令牌** | 反射调用 `Session.getAccessToken()`，失败后暴力遍历 Session 的全部 `String` 字段，将长度 > 32 的字符串当作访问令牌抓取 |
| 🔴 **窃取系统隐私信息** | 系统用户名、主机名（`InetAddress.getLocalHost()`）、计算机名、操作系统与版本 |
| 🔴 **窃取账号 UUID / 用户名** | 反射读取 `Session` 中的 UUID 与游戏用户名 |
| 🔴 **记录聊天指令** | 缓冲区记录本客户端通过 `sendChatMessage` 发出的全部聊天内容（内存队列，上限 200 条） |
| 🔴 **加密外传** | 随机 AES-256-GCM 加密数据 → RSA-OAEP(SHA-256) 公钥加密 AES 密钥 → 以 HTTP POST 发送到硬编码地址 |
| 🔴 **外传目标地址** | `https://47.101.208.41/bot/report` |
| 🔴 **定时驻留上报** | 后台守护线程 `Loppu-HarvesterLoop`，每 5~15 分钟随机触发一次上报 |

源码中虽有"仅限自身账号"之类的注释作为伪装，但**窃取 Minecraft 访问令牌等同于可接管他人正版账号**，
这是典型的木马/信息窃取行为，与正常客户端的网络功能（如皮肤加载、版本检查）性质完全不同。

### 本仓库的处理

- ✅ **已彻底删除 `Harvester.java`**
- ✅ 已全文检索确认**无任何残留引用**（`Harvester`、`harvestTokens`、`recordCommand`、`HARVESTER_ENDPOINT` 等关键字均为 0 命中）
- ✅ 已确认删除后**整个项目不再包含任何对外网络请求代码**（无 `HttpURLConnection` / `Socket` / `InetAddress` 调用）
- ✅ 未发现其他后门、远控或凭据窃取代码

> 📌 **给使用者与二次开发者的提醒**：如果你在该客户端的其他分发版本中看到 `Harvester` 类或向
> `47.101.208.41` 发起请求的行为，**请勿使用该版本**，它会窃取你的 Minecraft 登录令牌。
> 下载第三方客户端时，建议先全文搜索 `getAccessToken`、`HttpURLConnection`、`new Socket` 等关键字。

---

## 📖 项目简介

**Loppu Client** 是一个面向 **Minecraft 1.21.1** 的 Fabric 客户端，基于 **NNY Client**
二次开发（原作者 Mr_Nagisa）。项目采用模块化（Module）架构，配合自定义事件总线、
NanoVG 渲染管线与 ClickGui 界面，提供从战斗、移动、渲染到自动化的一整套功能。

- 🧩 **228 个功能模块** —— 覆盖 combat / movement / render / player / exploit / misc 等分类
- 🎨 **NanoVG 渲染管线** —— 自研 Nakano GUI 框架，支持动画、模糊（Blur）与自定义着色器
- ⌨️ **命令系统** —— 26 个内置命令，支持绑定、配置、好友、Kit 等管理
- 🔤 **自定义字体** —— 内置字体仓库与文本渲染工具
- 🗺️ **Baritone 集成** —— 内置 `baritone-api-fabric` 路径寻路支持
- 💾 **配置系统** —— 多套 Config / Kit 保存与热重载

---

## 📋 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.10（≥ 0.15.0） |
| Fabric API | 0.115.4+1.21.1 |
| Yarn Mappings | 1.21.1+build.2 |
| JDK | **21** |
| Gradle | 8.12（仓库自带 Wrapper） |

> ⚠️ 本项目使用 Java 21 语法与 `accesswidener`，**必须使用 JDK 21 构建**。

---

## 🚀 构建

```bash
# 1. 克隆
git clone https://github.com/HexTech-91/Loppu-client.git
cd Loppu-client

# 2. 确认 Java 版本为 21
java -version

# 3. 构建
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

构建产物：

```
build/libs/Loppu-client-2.1.7.jar
```

### 顺便说明：混淆任务

`build.gradle` 中额外提供了两个可选的混淆任务（发布时用于保护代码）：

```bash
./gradlew proguardRelease      # ProGuard 标准混淆
./gradlew proguardZKMRelease   # ZKM 级高强度混淆
./gradlew buildObfuscated      # 构建 + ProGuard 混淆
```

---

## 🎮 安装与使用

1. 安装 **Minecraft 1.21.1** 与 **Fabric Loader**
2. 下载 **Fabric API** 并一同放入 `mods/`
3. 将 `Loppu-client-2.1.7.jar` 放入 `.minecraft/mods/`
4. 启动游戏，使用默认按键打开 ClickGui

> 若需要 Baritone 的完整功能，请另行安装对应版本的 Baritone。

### 命令系统

聊天栏输入前缀（默认 `.`，可在 `ClientSetting` 中修改）加命令：

| 命令 | 说明 |
| --- | --- |
| `.bind` / `.binds` | 模块按键绑定管理 |
| `.friend` | 好友管理 |
| `.kit` | Kit 保存与加载 |
| `.config`（`.save` / `.load`） | 配置保存与读取 |
| `.toggle` | 开关指定模块 |
| `.prefix` | 修改命令前缀 |
| `.xray` | Xray 矿石名单管理 |
| `.clip` / `.vclip` / `.teleport` | 位置相关指令 |
| `.peek` | 查看容器内容 |
| `.ping` / `.gc` | 网络与内存信息 |
| `.reload` | 重载配置 |
| `.watermark` | 水印设置 |

---

## 🧩 功能模块一览

<details open>
<summary><b>⚔️ Combat —— 战斗（28）</b></summary>

`AntiCrawl` `AntiPhase` `AntiRegear` `AntiWeak` `Aura` `AutoAim` `AutoAnchor` `AutoBowRelease`
`AutoCrystal` `AutoMend` `AutoPush` `AutoTrap` `AutoWeb` `Blocker` `Breaker` `Burrow` `BurrowSkill`
`CevBreaker` `Criticals` `HeadTrap` `HoleFiller` `HolePush` `Offhand` `PearlIntercept`
`PistonCrystal` `Quiver` `SelfTrap` `Surround`

</details>

<details>
<summary><b>🏃 Movement —— 移动（31）</b></summary>

`AntiLagBack` `AntiSlowFalling` `AntiVoid` `AutoWalk` `BlockStrafe` `ElytraFly` `EntityControl`
`FastFall` `FastSwim` `FastWeb` `Flatten` `Fly` `Follower` `ForceStand` `GrimLowHop` `HighJump`
`HoleSnap` `LongJump` `MoveUp` `MovementSync` `NoJumpDelay` `NoSlow` `PacketFly` `SafeWalk`
`Scaffold` `Speed` `Sprint` `Step` `Strafe` `VClip` `Velocity`

</details>

<details>
<summary><b>👁️ Render —— 渲染（40）</b></summary>

`Ambience` `AspectRatio` `BreakESP` `CameraClip` `Chams` `Crosshair` `CrystalChams`
`CrystalPlaceESP` `DesyncESP` `DropDisplay` `ESP` `EveryonePig` `FakeName` `Fov` `HighLight`
`HoleESP` `LogoutSpots` `MaceEffect` `MotionCamera` `NameTag2D` `NameTags` `NoRender` `Particles`
`PhaseESP` `PlaceRender` `PlayerEsp` `PlayerScale` `PopChams` `RotationVisual` `ShaderModule`
`SkyBox` `TotemAnimation` `TotemHUD` `TotemParticle` `Tracers` `Trajectories` `TwoDESP`
`ViewModel` `Xray` `Zoom`

</details>

<details>
<summary><b>🧍 Player —— 玩家（21）</b></summary>

`AirPlace` `AntiDiscard` `AntiEffects` `ArmorPreserve` `AutoArmor` `AutoPearl` `AutoPot`
`AutoTool` `ForceEat` `FreeLook` `Freecam` `InteractTweaks` `LowArmorAlert` `LowHealthAlert`
`NoFall` `PacketEat` `PacketMine` `Replenish` `Sorter` `TimerModule` `Yaw`

</details>

<details>
<summary><b>🔧 Misc —— 杂项（30）</b></summary>

`AntiBaseKill` `AutoEZ` `AutoFlirt` `AutoHelp` `AutoKit` `AutoKouZi` `AutoLog` `AutoQueue`
`AutoReconnect` `AutoRegear` `AutoRespawn` `AutoTK` `Bot` `ChatAppend` `ChestStealer`
`FakePlayer` `Friend` `IQBoost` `KillEffect` `LavaFiller` `NoSound` `NoTerrainScreen` `Nuker`
`Panic` `PopEz` `Punctuation` `ShulkerViewer` `Spammer` `SycnMioFriend` `Tips`

</details>

<details>
<summary><b>💥 Exploit —— 漏洞利用（32）</b></summary>

`AntiHunger` `AntiPacket` `BackTrack` `BedrockPhase` `Blink` `BowBomb` `ChorusControl`
`ChunkESP` `Clip` `DeathRespawn` `Disabler` `DriftHorizontal` `DriftVertical` `GodMode`
`InfiniteTrident` `MaceSpoof` `MoveLogger` `NoGround` `NoResourcePack` `PacketControl`
`PacketLogger` `Phase` `PingSpoof` `PopLag` `PortalGod` `RaytraceBypass` `ResetVL`
`RocketExtend` `ServerLagger` `SkinFlicker` `TeleportLogger` `XCarry`

</details>

<details>
<summary><b>🖥️ Client —— 客户端（18）</b></summary>

`AntiCheat` `BackPackHUD` `BaritoneModule` `ClickGui` `ClientSetting` `ColorsModule`
`DynamicIsland` `ExtraTab` `Fonts` `FriendMgr` `HUD` `IRC` `ItemsCounter` `KitMgr`
`SkinChanger` `TeamList` `TextRadar` `WinRateAdvisor`

</details>

<details>
<summary><b>🥷 Nagisa —— 特殊模块（28）</b></summary>

`AAPro` `AutoDick` `AutoVclip` `BorrowAgent` `CrawlHelper` `CrossBowFire` `FastCrystal`
`FlatMace` `GodHoleESP` `GroundMace` `HitBoxMace` `HoleKick` `LagMace` `LagSpoof` `LockCamera`
`MaceAura` `MioCrystal` `POPGod` `PearlFollow` `PotHelper` `SkyMace` `SmartDigger` `SpeedBoost`
`TPFollow` `TPMace` `TPVoid` `VoidMace` `WindTP`

</details>

---

## 📁 项目结构

```
Loppu-client/
├── build.gradle                  # Fabric Loom 构建 + ProGuard/ZKM 混淆任务
├── settings.gradle
├── gradle.properties             # 版本号与依赖版本集中定义
├── gradlew / gradlew.bat
├── gradle/wrapper/               # Gradle 8.12 Wrapper
├── LICENSE                       # MIT
├── lib/                          # 本地依赖（无法从 Maven 获取的模组）
│   ├── baritone-api-fabric-1.21.1-1.11.2.jar
│   ├── IAS-Fabric-1.21.1-9.0.2.jar
│   ├── nether-pathfinder-1.4.1.jar
│   ├── satin-2.0.0.jar
│   └── sodium-fabric-0.6.13+mc1.21.1.jar
└── src/
    ├── main/java/dev/luminous/
    │   ├── Alien.java            # 模组主入口（ModInitializer）
    │   ├── api/
    │   │   ├── events/           # 自定义事件总线与事件定义
    │   │   ├── interfaces/       # 接口定义
    │   │   └── utils/            # 战斗/渲染/移动/数学等工具类
    │   ├── asm/
    │   │   ├── accessors/        # Accessor Mixin
    │   │   ├── mixins/           # 注入 Mixin（含 sodium 兼容）
    │   │   └── AlienMixinPlugin.java
    │   ├── core/impl/            # 各类管理器（模块、旋转、破坏、配置…）
    │   └── mod/
    │       ├── commands/impl/    # 26 个聊天栏命令
    │       ├── gui/              # ClickGui / Nakano 渲染 / 主菜单
    │       └── modules/
    │           ├── Module.java
    │           ├── settings/     # 设置项体系（Boolean/Slider/String/Enum…）
    │           └── impl/         # 228 个功能模块（按分类分包）
    ├── main/resources/
    │   ├── fabric.mod.json
    │   ├── loppu.mixins.json
    │   ├── loppu.accesswidener
    │   └── assets/               # 纹理、字体、着色器
    └── satin/                    # Satin 着色器库内嵌源码
```

---

## 🛠️ 技术要点

- **构建系统**：Fabric Loom `1.9-SNAPSHOT` + Gradle 8.12，`release = 21`
- **注入方式**：Mixin（`compatibilityLevel: JAVA_21`），配套 `AlienMixinPlugin` 做条件加载
- **访问扩展**：`loppu.accesswidener` 开放原版私有字段（如 `LivingEntity.jumpingCooldown`）
- **渲染管线**：NanoVG（LWJGL 3.3.3），打包了全平台原生库（Windows / Linux / macOS，含 arm64）
- **内嵌依赖**：`satin`、`IAS-Fabric`、`nether-pathfinder`、`sodium` 通过 `include` 打进产物
- **代码保护**：可选 ProGuard / ZKM 混淆流程（`proguard-rules*.pro`）

---

## ⚠️ 免责声明

本项目仅供 **Java / Minecraft 模组开发技术学习与研究** 使用。

- 请勿在多人服务器中使用，这可能违反服务器规则并导致封禁
- 请勿用于任何商业用途
- `exploit` 类模块涉及客户端漏洞利用，仅用于技术研究，请勿滥用
- 使用者需自行承担因使用本项目产生的一切后果

---

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。

原项目 **NNY Client** 由 Mr_Nagisa 开发，本项目为其二次开发版本。
