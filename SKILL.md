---
name: jar-source-reader
description: 如果需要读取当前 Maven 或者 Gradle 项目，引入的 jar 包的具体源代码，则调用此 skill
---

## 使用

当用户需要查看某个依赖 jar 包的源码时，使用以下工具读取：

``` bash
cd /path/to/this/skill && java -Dfile.encoding=UTF-8 -jar ./tool/jar-source-reader-kt-all.jar
```

### 参数说明

| 参数 | 必填 | 说明 |
|------|------|------|
| `--group-id` | 是 | Maven group ID，例如 `cn.hutool` |
| `--artifact-id` | 是 | Maven artifact ID，例如 `hutool-all` |
| `--version` | 是 | 版本号，例如 `5.8.36` |
| `--class-name` | 是 | 完全限定类名，例如 `cn.hutool.core.util.IdUtil`；读取内部类时可写成 `cn.hutool.core.util.IdUtil$Inner` |
| `--method-name` | 否 | 方法名，例如 `fastSimpleUUID`；传入后只输出对应方法源码，若存在重载会一起输出 |
| `--maven-repo` | 否 | 指定 Maven 仓库根目录，例如 `C:/Users/yshs/.m2/repository` |
| `--gradle-repo` | 否 | 指定 Gradle 仓库根目录，例如 `C:/Users/yshs/.gradle/caches/modules-2/files-2.1` |
| `--ignore-length-limit` | 否 | 忽略源码行数限制（默认超过 500 行时报错退出） |

### 使用示例

```bash
java -Dfile.encoding=UTF-8 -jar ./tool/jar-source-reader-kt-all.jar --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --class-name=cn.hutool.core.util.IdUtil
```

如果只想读取类中的某个方法，可以这样传：

```bash
java -Dfile.encoding=UTF-8 -jar ./tool/jar-source-reader-kt-all.jar --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --class-name=cn.hutool.core.util.IdUtil --method-name=fastSimpleUUID
```

如果要读取内部类，可以这样传：

```bash
java -Dfile.encoding=UTF-8 -jar ./tool/jar-source-reader-kt-all.jar --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --class-name=cn.hutool.core.util.IdUtil$Inner
```

如果需要显式指定本地仓库目录，可以这样传：

```bash
java -Dfile.encoding=UTF-8 -jar ./tool/jar-source-reader-kt-all.jar --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --class-name=cn.hutool.core.util.IdUtil --maven-repo=C:/Users/yshs/.m2/repository --gradle-repo=C:/Users/yshs/.gradle/caches/modules-2/files-2.1
```

如果源码超过 500 行需要强制输出，可以添加 `--ignore-length-limit`：

```bash
java -Dfile.encoding=UTF-8 -jar ./tool/jar-source-reader-kt-all.jar --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --class-name=cn.hutool.core.util.IdUtil --ignore-length-limit
```

### 输出说明

- **stderr**：错误信息（如果有）
- **stdout**：未传 `--method-name` 时，输出目标类源码；若源码超过 500 行且未传 `--ignore-length-limit`，会自动降级为类结构摘要
- **stdout**：传入 `--method-name` 时，输出对应方法源码；若存在同名重载，会按源码顺序一起输出

### 注意事项

- 当类源码超过 500 行时，工具会自动输出类结构摘要，并提示后续传入更具体的 `--method-name`。
- 若确实需要完整类源码，可以显式添加 `--ignore-length-limit`。
