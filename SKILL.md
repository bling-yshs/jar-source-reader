---
name: jar-source-reader
description: 如果需要读取当前 Maven 或者 Gradle 项目，引入的 jar 包的具体源代码，则调用此 skill
---

## 前置需求

- 你必须提前获知本 skill 所在的路径，下方的 /path/to/this/skill 则代表本 skill 的路径
- 你必须知道当前项目是 Gradle 还是 Maven 项目：根目录存在 `gradlew` / `gradlew.bat` 时按 Gradle 项目处理，存在 `pom.xml` 时按 Maven 项目处理

## 使用

当用户需要查看某个依赖 jar 包的源码时，使用以下工具读取：

``` bash
java '-Dfile.encoding=UTF-8' -jar /path/to/this/skill/tool/jar-source-reader.jar --mode=<exact|fuzzy|search>
```

### 参数说明

#### exact 模式

已知完整 Maven 坐标时，精确定位 sources jar 并读取类或方法源码。

| 参数 | 必填 | 说明 |
|------|:----:|------|
| `--mode` | 是 | 固定为 `exact` |
| `--group-id` | 是 | Maven group ID，例如 `cn.hutool` |
| `--artifact-id` | 是 | Maven artifact ID，例如 `hutool-all` |
| `--version` | 是 | 版本号，例如 `5.8.36` |
| `--class-name` | 是 | 类名、完全限定类名或使用 `$` 分隔的内部类名 |
| `--method-name` | 否 | 只输出指定方法；存在重载时输出所有同名方法 |
| `--maven-repo` | 否 | Maven 仓库根目录，默认 `~/.m2/repository` |
| `--gradle-repo` | 否 | Gradle 仓库根目录，默认 `~/.gradle/caches/modules-2/files-2.1` |
| `--ignore-length-limit` | 否 | 忽略 500 行限制，强制输出完整源码 |

```bash
java '-Dfile.encoding=UTF-8' -jar /path/to/this/skill/tool/jar-source-reader.jar --mode=exact --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --class-name=cn.hutool.core.util.IdUtil --method-name=fastSimpleUUID
```

读取内部类时，使用 `$` 分隔外部类与内部类：

```bash
java '-Dfile.encoding=UTF-8' -jar /path/to/this/skill/tool/jar-source-reader.jar --mode=exact --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --class-name=cn.hutool.core.util.IdUtil$Inner
```

如果 sources jar 中存在多个同名类，使用完全限定类名。

#### fuzzy 模式

只知道类名时，从当前 Maven 或 Gradle 项目的依赖中定位来源 jar，再反查 sources jar。

| 参数 | 必填 | 说明 |
|------|:----:|------|
| `--mode` | 是 | 固定为 `fuzzy` |
| `--class-name` | 是 | 类名、完全限定类名或使用 `$` 分隔的内部类名 |
| `--method-name` | 否 | 只输出指定方法；存在重载时输出所有同名方法 |
| `--maven-repo` | 否 | Maven 仓库根目录，默认 `~/.m2/repository` |
| `--gradle-repo` | 否 | Gradle 仓库根目录，默认 `~/.gradle/caches/modules-2/files-2.1` |
| `--ignore-length-limit` | 否 | 忽略 500 行限制，强制输出完整源码 |

```bash
java '-Dfile.encoding=UTF-8' -jar /path/to/this/skill/tool/jar-source-reader.jar --mode=fuzzy --class-name=IdUtil
```

`fuzzy` 模式若命中多个 sources jar，会提示改用 `exact` 模式和完整 Maven 坐标。

如果发现同一个包存在多个版本，必须直接询问用户要使用哪个版本，不要尝试分析项目依赖关系来推断版本。

#### search 模式

在完整 Maven 坐标对应的唯一 sources jar 内逐行正则搜索文本。

| 参数 | 必填 | 说明 |
|------|:----:|------|
| `--mode` | 是 | 固定为 `search` |
| `--group-id` | 是 | Maven group ID，例如 `cn.hutool` |
| `--artifact-id` | 是 | Maven artifact ID，例如 `hutool-all` |
| `--version` | 是 | 版本号，例如 `5.8.36` |
| `--pattern` | 是 | JVM 正则表达式 |
| `--max-count` | 否 | 最大匹配行数，必须是正整数，默认 `100` |
| `--context` | 否 | 为每个匹配行附加的前后文行数，不能是负数，默认 `0` |
| `--maven-repo` | 否 | Maven 仓库根目录，默认 `~/.m2/repository` |
| `--gradle-repo` | 否 | Gradle 仓库根目录，默认 `~/.gradle/caches/modules-2/files-2.1` |

```bash
java '-Dfile.encoding=UTF-8' -jar /path/to/this/skill/tool/jar-source-reader.jar --mode=search --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --pattern='create.*UUID' --max-count=100 --context=3
```

`search` 模式不支持 fuzzy 定位，不会扫描当前项目的其他依赖。参考 `rg` 的输出格式，匹配行使用 `文件路径:行号:内容`，上下文行使用 `文件路径-行号-内容`，不连续的结果块使用 `--` 分隔。到达匹配行上限后立即停止。
