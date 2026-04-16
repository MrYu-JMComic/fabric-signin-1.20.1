# 签到模组（Fabric 1.20.1）

`signin` 是一个适配 Minecraft Fabric 1.20.1 的签到模组，支持服务端与客户端联动、可视化奖励编辑、多语言文本与权限控制。

## 功能概览

- 每日签到奖励：支持经验、物品、补签卡。
- 补签机制：可消耗补签卡补签昨天。
- OP 可视化编辑：可在 GUI 中编辑 7 日循环奖励。
- 多物品奖励：单日支持多种物品，支持 NBT/组件文本。
- 入服提醒：未签到玩家入服后会收到可点击入口（签到/界面/补签）。
- 服务端权威同步：客户端界面优先使用服务端同步数据，避免本地缓存误导。

## 兼容要求

- Minecraft: `1.20.1`
- Fabric Loader: `>=0.18.4`
- Fabric API: `0.92.7+1.20.1`（或兼容版本）
- Java: `17+`

## 安装方式

1. 将构建产物 `signin-1.0.1.jar` 放入服务端 `mods`。
2. 客户端也放入同版本 `signin-1.0.1.jar`（建议与服务端一致）。
3. 确保同时安装 Fabric Loader 与 Fabric API。

## 指令说明

### 根指令

- `/signin`（英文根）
- `/签到`（中文根）
- `/qd`（中文快捷根）

### 子指令

- 英文根 `/signin`:
  - `/signin status`
  - `/signin gui`
  - `/signin makeup`
  - `/signin clear [玩家]`
- 中文根 `/签到` 与 `/qd`:
  - `/签到 状态`
  - `/签到 界面`
  - `/签到 补签`
  - `/签到 清空 [玩家]`

## 权限节点

- `signin.user.sign`
- `signin.user.status`
- `signin.user.gui`
- `signin.user.makeup`
- `signin.admin.rewards.edit`
- `signin.admin.clear`
- `signin.admin.clear.others`

若未安装权限管理模组，将回退到原版 OP 等级判断。

## 构建

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```
