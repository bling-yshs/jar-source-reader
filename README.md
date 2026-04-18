<h1 align="center">📦 Jar Source Reader</h1>

<p align="center">
  <strong>AI Skill · 让 AI 助手直接阅读 jar 依赖源码 · 即装即用</strong>
</p>
<br>

<div align="center">
  <a href="https://github.com/bling-yshs/jar-source-reader-kt/stargazers"><img src="https://img.shields.io/github/stars/bling-yshs/jar-source-reader-kt?logo=github&color=yellow" alt="Stars"></a>
  <a href="https://github.com/bling-yshs/jar-source-reader-kt/releases/latest"><img src="https://img.shields.io/github/v/release/bling-yshs/jar-source-reader-kt?label=Release&color=brightgreen" alt="Release"></a>
  <a href="https://github.com/bling-yshs/jar-source-reader-kt/blob/main/LICENSE"><img src="https://img.shields.io/github/license/bling-yshs/jar-source-reader-kt.svg?color=orange" alt="License"></a>
</div>
<br>

## ✨ 项目简介

本项目是一个 **Skill**。当 Agent 需要查看 Maven / Gradle 项目中某个依赖 jar 包的源码，它可以通过此 Skill 直接读取并展示目标类、内部类或指定方法的 Java 源代码，无需你手动去翻阅源码。

### 💡 它解决了什么问题？

在日常开发中，AI 助手无法直接访问 jar 包内的源码。当你请求 AI 分析某个第三方库的实现细节时，AI 只能依赖训练数据中的记忆，可能不准确或已过时。本 Skill 让 AI 能够实时从你本地仓库中读取真实源码，给出更精确的分析。

## 🚀 功能特性

- 🔍 **类源码查看** — 支持传入类名或完全限定类名，直接输出目标类的完整源码
- 🪆 **内部类支持** — 使用 `$` 分隔符可查看嵌套内部类，如 `OuterClass$InnerClass`
- 🎯 **方法级查看** — 精确输出指定方法源码，支持重载方法一并展示
- 📐 **智能骨架降级** — 当类源码超过 500 行时，自动输出类结构摘要（字段 + 公开方法签名），引导 AI 进一步精确查询
- 📁 **双仓库支持** — 同时搜索 Maven（`~/.m2/repository`）和 Gradle（`~/.gradle/caches`）本地仓库
- 🛠️ **自定义仓库路径** — 支持通过参数显式指定仓库根目录

## 📖 安装与使用

### 1️⃣ 下载 Skill

从 [Releases](https://github.com/bling-yshs/jar-source-reader-kt/releases/latest) 页面下载最新版本的压缩包。

### 2️⃣ 放置到 Skills 目录

将下载的文件解压后，放置到以下目录：

```
~/.claude/skills/jar-source-reader/
├── SKILL.md                            # AI Skill 描述文件
└── tool/
    └── jar-source-reader-kt-all.jar    # 工具本体
```

### 3️⃣ 下载源代码（Source Jar）

在使用前，你需要确保目标依赖的 **sources jar** 已下载到本地仓库。

在 IntelliJ IDEA 中，打开 Maven 工具栏，点击 **「下载源代码」** 按钮即可：

<div align="center">
  <img height="500" src="assets/readme/download_sources.jpg" alt="下载源代码"/>
</div>

### 4️⃣ 向 AI 提供目标源码信息

在与 AI 对话时，你需要向 AI 提供你想查看的目标类所在 jar 包的依赖坐标信息（groupId、artifactId、version），AI 就会自动调用此 Skill 读取源码。

<div align="center">
  <img src="assets/readme/usage_demo.gif" alt="使用演示"/>
</div>

## ⚙️ 参数说明

以下参数由 AI 助手在调用时自动填充，供开发者参考：

| 参数 | 必填 | 说明 |
|:---|:---:|:---|
| `--group-id` | ✅ | Maven Group ID，如 `cn.hutool` |
| `--artifact-id` | ✅ | Maven Artifact ID，如 `hutool-all` |
| `--version` | ✅ | 依赖版本号，如 `5.8.36` |
| `--class-name` | ✅ | 类名或完全限定类名，如 `IdUtil` 或 `cn.hutool.core.util.IdUtil`；内部类使用 `$` 分隔 |
| `--method-name` | ❌ | 方法名，传入后只输出对应方法源码；存在重载时会一并输出 |
| `--maven-repo` | ❌ | 指定 Maven 仓库根目录，默认 `~/.m2/repository` |
| `--gradle-repo` | ❌ | 指定 Gradle 仓库根目录，默认 `~/.gradle/caches/modules-2/files-2.1` |
| `--ignore-length-limit` | ❌ | 忽略 500 行的源码长度限制，强制输出完整源码 |

## 📤 输出行为

| 场景 | 输出内容 |
|:---|:---|
| 源码 ≤ 500 行 | 完整类源码 |
| 源码 > 500 行 | 类结构摘要（字段 + 公开方法签名）+ 提示 AI 传入 `--method-name` |
| 传入 `--method-name` | 目标方法源码；存在重载时按源码顺序一并输出 |
| 发生错误 | 错误信息输出到 **stderr** |

## 📂 项目结构

```
jar-source-reader-kt/
├── src/
│   ├── main/kotlin/com/yshs/jsr/
│   │   └── Main.kt                # 🚀 程序入口与核心逻辑
│   └── test/                       # 🧪 单元测试
├── SKILL.md                        # 🤖 AI Skill 描述文件
└── build.gradle.kts                # 🔨 Gradle 构建配置
```

## 🛠️ 开发环境

| 环境 | 版本要求 |
|:---:|:---:|
| ☕ JDK | 8+ |
| 🐘 Gradle | Wrapper 自带 |

## 🔗 技术栈

| 依赖 | 用途 |
|:---|:---|
| [JavaParser](https://github.com/javaparser/javaparser) | 解析 Java 源码 AST，提取类/方法/字段声明 |
| [Clikt](https://github.com/ajalt/clikt) | Kotlin 命令行参数解析框架 |
| [Shadow](https://github.com/GradleUp/shadow) | 构建 fat jar（包含所有依赖） |

## 📄 许可证

本项目采用 [GPL-3.0](LICENSE) 开源许可证。
