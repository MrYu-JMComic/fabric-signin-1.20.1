# Signin Mod (Fabric 1.20.1)

`signin` is a daily sign-in mod for Minecraft Fabric 1.20.1, including client GUI, server-authoritative sync, visual reward editing, localization, and permission support.

## Features

- Daily rewards: XP, items, and make-up cards.
- Make-up flow: spend a card to make up the latest missed day in the current year.
- OP visual editor: edit 7-day loop rewards in GUI.
- Multi-item rewards: each day can contain multiple items with NBT text support.
- Join reminder: unsigned players receive clickable entries (Sign In / GUI / Make Up).
- Server-first sync: UI prefers server sync state to avoid stale local cache display.

## Compatibility

- Minecraft: `1.20.1`
- Fabric Loader: `>=0.18.4`
- Fabric API: `0.92.7+1.20.1` (or compatible)
- Java: `17+` for running the game; builds use the JDK 21 path pinned in `gradle.properties`.

## Installation

1. Put `signin-1.0.3.jar` into server `mods`.
2. Put the same `signin-1.0.3.jar` into client `mods` (recommended).
3. Ensure Fabric Loader and Fabric API are installed.

## Commands

### Root commands

- `/signin` (English root)
- `/签到` (Chinese root)
- `/qd` (Chinese short root)

### Subcommands

- For `/signin`:
  - `/signin status`
  - `/signin gui`
  - `/signin makeup`
  - `/signin clear [player]`
- For `/签到` and `/qd`:
  - `/签到 状态`
  - `/签到 界面`
  - `/签到 补签`
  - `/签到 清空 [player]`

## Permission Nodes

- `signin.user.sign`
- `signin.user.status`
- `signin.user.gui`
- `signin.user.makeup`
- `signin.admin.rewards.edit`
- `signin.admin.clear`
- `signin.admin.clear.others`

If no permissions mod is installed, checks fall back to vanilla OP levels.

## Build

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```
