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

| 参数 | 必填 | 说明                                                                                  |
|------|------|-------------------------------------------------------------------------------------|
| `--group-id` | 是 | Maven group ID，例如 `cn.hutool`                                                       |
| `--artifact-id` | 是 | Maven artifact ID，例如 `hutool-all`                                                   |
| `--version` | 是 | 版本号，例如 `5.8.36`                                                                     |
| `--class-name` | 是 | 类名或完全限定类名，例如 `IdUtil` 或 `cn.hutool.core.util.IdUtil`；读取内部类时可写成 `IdUtil$Inner` 或 `cn.hutool.core.util.IdUtil$Inner`。如果 sources jar 里有多个同名类，需要改传完整类名 |
| `--method-name` | 否 | 方法名，例如 `fastSimpleUUID`；不传入则展示整个类的源码，传入后只输出对应方法的源码，若存在方法重载会输出所有同名方法                 |
| `--maven-repo` | 否 | 指定 Maven 仓库根目录，例如 `C:/Users/yshs/.m2/repository`                                    |
| `--gradle-repo` | 否 | 指定 Gradle 仓库根目录，例如 `C:/Users/yshs/.gradle/caches/modules-2/files-2.1`               |
| `--ignore-length-limit` | 否 | 忽略源码行数限制（默认超过 500 行时，只展示源码骨架）                                                   |

### 使用示例

```bash
java -Dfile.encoding=UTF-8 -jar ./tool/jar-source-reader-kt-all.jar --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --class-name=cn.hutool.core.util.IdUtil --method-name=fastSimpleUUID
```

如果只知道类名，也可以直接这样传：

```bash
java -Dfile.encoding=UTF-8 -jar ./tool/jar-source-reader-kt-all.jar --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --class-name=IdUtil
```

如果要读取内部类，可以这样传：

```bash
java -Dfile.encoding=UTF-8 -jar ./tool/jar-source-reader-kt-all.jar --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --class-name=cn.hutool.core.util.IdUtil$Inner
```

如果提示存在多个同名类，就改成完整类名再传一次。

如果需要显式指定本地仓库目录，可以这样传：

```bash
java -Dfile.encoding=UTF-8 -jar ./tool/jar-source-reader-kt-all.jar --group-id=cn.hutool --artifact-id=hutool-all --version=5.8.36 --class-name=cn.hutool.core.util.IdUtil --maven-repo=C:/Users/yshs/.m2/repository --gradle-repo=C:/Users/yshs/.gradle/caches/modules-2/files-2.1
```