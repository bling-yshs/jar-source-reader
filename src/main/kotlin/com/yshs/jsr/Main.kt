package com.yshs.jsr

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile
import kotlin.system.exitProcess

private const val DEFAULT_LINE_LIMIT = 500

private const val MODE_EXACT = "exact"

private const val MODE_FUZZY = "fuzzy"

/** 目标类解析结果 */
data class ClassTarget(
    val javaFilePath: String,
    val outerSimpleName: String,
    val nestedSimpleNames: List<String>,
)

/** 本地仓库路径上下文。 */
data class RepositoryContext(
    val home: String,
    val gradleHome: String,
    val mavenRepoBase: File,
    val gradleRepoBase: File,
)

/**
 * 读取命令行参数，定位 sources jar，并输出目标类源码或指定方法源码。
 *
 * @param args 命令行参数
 */
fun main(args: Array<String>) {
    val command = parseCommandOrExit(args)
    println(readSource(command))
}

/**
 * 解析命令行参数，并在参数错误时按命令行工具约定退出。
 *
 * @param args 原始参数数组
 * @return 解析完成的命令对象
 */
fun parseCommandOrExit(args: Array<String>): SourceReadRequestParserCommand {
    val command = SourceReadRequestParserCommand()
    return try {
        command.parse(args)
        command
    } catch (e: CliktError) {
        command.echoFormattedHelp(e)
        exitProcess(e.statusCode)
    } catch (e: ProgramResult) {
        exitProcess(e.statusCode)
    }
}

/**
 * 根据参数读取并返回目标源码文本。
 *
 * @param command 已解析的命令对象
 * @return 目标源码或骨架输出
 */
fun readSource(command: SourceReadRequestParserCommand): String {
    val mode = command.mode.lowercase()
    if (mode != MODE_EXACT && mode != MODE_FUZZY) {
        die("--mode 仅支持 exact 或 fuzzy")
    }

    if (mode == MODE_FUZZY) {
        val repositoryContext = buildRepositoryContext(command)
        val mavenRepoBase = repositoryContext.mavenRepoBase
        val gradleRepoBase = repositoryContext.gradleRepoBase
        val exactClassFilePath = command.className.replace('.', '/') + ".class"
        val simpleClassFilePath = command.className.substringAfterLast('.') + ".class"
        val hasPackageName = command.className.substringBefore('$').contains('.')
        val jarFiles = collectFuzzyDependencyJarFiles()
        val matchedSourcesByClassName = linkedMapOf<String, MutableSet<String>>()

        jarFiles.forEach { jarFile ->
            ZipFile(jarFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val matchedClass = if (hasPackageName) {
                        entry.name == exactClassFilePath
                    } else {
                        entry.name == simpleClassFilePath || entry.name.endsWith("/$simpleClassFilePath")
                    }

                    if (!entry.isDirectory && matchedClass) {
                        val sourceJars = linkedSetOf<String>()
                        val canonicalJar = jarFile.canonicalFile
                        if (canonicalJar.toPath().startsWith(gradleRepoBase.toPath())) {
                            val relativePath = gradleRepoBase.toPath().relativize(canonicalJar.toPath())
                            if (relativePath.nameCount >= 5) {
                                val sourceDir = Paths.get(
                                    gradleRepoBase.absolutePath,
                                    relativePath.getName(0).toString(),
                                    relativePath.getName(1).toString(),
                                    relativePath.getName(2).toString(),
                                ).toFile()
                                if (sourceDir.isDirectory) {
                                    sourceDir.walkTopDown()
                                        .filter { file -> file.isFile && file.name.endsWith("-sources.jar") }
                                        .forEach { sourceJar -> sourceJars += sourceJar.canonicalFile.absolutePath }
                                }
                            }
                        }
                        if (canonicalJar.toPath().startsWith(mavenRepoBase.toPath())) {
                            val sourceDir = canonicalJar.parentFile
                            if (sourceDir != null && sourceDir.isDirectory) {
                                sourceDir.walkTopDown()
                                    .filter { file -> file.isFile && file.name.endsWith("-sources.jar") }
                                    .forEach { sourceJar -> sourceJars += sourceJar.canonicalFile.absolutePath }
                            }
                        }
                        if (sourceJars.isNotEmpty()) {
                            val className = entry.name.removeSuffix(".class").replace('/', '.')
                            matchedSourcesByClassName.getOrPut(className) { linkedSetOf() }.addAll(sourceJars)
                        }
                        break
                    }
                }
            }
        }

        if (matchedSourcesByClassName.isEmpty() && hasPackageName) {
            jarFiles.forEach { jarFile ->
                ZipFile(jarFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val matchedClass = entry.name == simpleClassFilePath || entry.name.endsWith("/$simpleClassFilePath")

                        if (!entry.isDirectory && matchedClass) {
                            val sourceJars = linkedSetOf<String>()
                            val canonicalJar = jarFile.canonicalFile
                            if (canonicalJar.toPath().startsWith(gradleRepoBase.toPath())) {
                                val relativePath = gradleRepoBase.toPath().relativize(canonicalJar.toPath())
                                if (relativePath.nameCount >= 5) {
                                    val sourceDir = Paths.get(
                                        gradleRepoBase.absolutePath,
                                        relativePath.getName(0).toString(),
                                        relativePath.getName(1).toString(),
                                        relativePath.getName(2).toString(),
                                    ).toFile()
                                    if (sourceDir.isDirectory) {
                                        sourceDir.walkTopDown()
                                            .filter { file -> file.isFile && file.name.endsWith("-sources.jar") }
                                            .forEach { sourceJar -> sourceJars += sourceJar.canonicalFile.absolutePath }
                                    }
                                }
                            }
                            if (canonicalJar.toPath().startsWith(mavenRepoBase.toPath())) {
                                val sourceDir = canonicalJar.parentFile
                                if (sourceDir != null && sourceDir.isDirectory) {
                                    sourceDir.walkTopDown()
                                        .filter { file -> file.isFile && file.name.endsWith("-sources.jar") }
                                        .forEach { sourceJar -> sourceJars += sourceJar.canonicalFile.absolutePath }
                                }
                            }
                            if (sourceJars.isNotEmpty()) {
                                val className = entry.name.removeSuffix(".class").replace('/', '.')
                                matchedSourcesByClassName.getOrPut(className) { linkedSetOf() }.addAll(sourceJars)
                            }
                            break
                        }
                    }
                }
            }
        }

        if (matchedSourcesByClassName.isEmpty()) {
            die("未找到任何包含目标类的 sources jar，请检查是否下载了 sources jar")
        }

        val matchedSourcesJars = matchedSourcesByClassName.values.flatten().distinct()
        if (matchedSourcesJars.size > 1) {
            die(
                buildString {
                    appendLine("找到多个可能的 sources jar，请改用 exact 模式并传入完整 Maven 坐标。候选如下:")
                    matchedSourcesByClassName.forEach { (className, sourceJars) ->
                        sourceJars.forEach { sourceJar ->
                            appendLine("  $className -> $sourceJar")
                        }
                    }
                }.trimEnd()
            )
        }

        val matchedClassName = matchedSourcesByClassName.keys.first()
        val matchedSourcesJar = File(matchedSourcesJars.single())
        println("fuzzy 模式命中 source jar: ${matchedSourcesJar.absolutePath}")
        println("fuzzy 模式命中类: $matchedClassName")
        println("提示：后续可使用 exact 模式并传入完整 Maven 坐标读取该类源码")
        ZipFile(matchedSourcesJar).use { zip ->
            val classTarget = try {
                resolveClassTarget(zip, matchedClassName)
            } catch (e: IllegalArgumentException) {
                die(e.message ?: "类名解析失败")
            }
            val entry = zip.getEntry(classTarget.javaFilePath)
                ?: die("在 sources jar 中未找到: ${classTarget.javaFilePath}")
            val content = zip.getInputStream(entry).bufferedReader().readText()
            return try {
                resolveOutput(
                    source = content,
                    className = matchedClassName,
                    methodName = command.methodName,
                    ignoreLengthLimit = command.ignoreLengthLimit,
                )
            } catch (e: IllegalArgumentException) {
                die(e.message ?: "源码提取失败")
            }
        }
    }

    val repositoryContext = buildRepositoryContext(command)

    val candidateDirs = repositoryCandidates(command, repositoryContext.home, repositoryContext.gradleHome)
    val sourcesJar: File = candidateDirs
        .filter { it.exists() }
        .flatMap { it.walkTopDown().filter { file -> file.isFile && file.name.endsWith("-sources.jar") } }
        .firstOrNull()
        ?: die(
            "未找到 sources jar，请先确保 sources jar 已下载，已查找路径:\n  " +
                candidateDirs.joinToString("\n  ") { it.absolutePath }
        )

    ZipFile(sourcesJar).use { zip ->
        val classTarget = try {
            resolveClassTarget(zip, command.className)
        } catch (e: IllegalArgumentException) {
            die(e.message ?: "类名解析失败")
        }
        val entry = zip.getEntry(classTarget.javaFilePath)
            ?: die("在 sources jar 中未找到: ${classTarget.javaFilePath}")

        val content = zip.getInputStream(entry).bufferedReader().readText()
        return try {
            resolveOutput(
                source = content,
                className = command.className,
                methodName = command.methodName,
                ignoreLengthLimit = command.ignoreLengthLimit,
            )
        } catch (e: IllegalArgumentException) {
            die(e.message ?: "源码提取失败")
        }
    }
}

/**
 * 打印错误并以退出码 1 终止进程。
 *
 * @param message 错误信息
 * @param err 错误输出流
 * @param exit 退出回调
 * @return 永不返回
 */
fun die(
    message: String,
    err: PrintStream = System.err,
    exit: (Int) -> Nothing = ::exitProcess,
): Nothing {
    err.println(message)
    exit(1)
}

/**
 * 使用 Clikt 解析命令行参数。
 *
 * @param args 原始参数数组
 * @return 解析完成的命令对象
 */
fun parseCommand(args: Array<String>): SourceReadRequestParserCommand {
    val command = SourceReadRequestParserCommand()
    command.parse(args)
    return command
}

/**
 * 获取 fuzzy 模式需要扫描的项目依赖 jar。
 *
 * @return 当前项目依赖 jar 文件列表
 */
fun collectFuzzyDependencyJarFiles(): List<File> {
    val projectDir = File(".")
    val gradlew = resolveGradleWrapperFile(projectDir)
    if (gradlew.isFile) {
        return collectGradleDependencyJarFiles(gradlew)
    }

    if (File(projectDir, "pom.xml").isFile) {
        return collectMavenDependencyJarFiles(projectDir)
    }

    die("当前目录未找到 Gradle Wrapper 或 pom.xml，无法使用 fuzzy 模式")
}

/**
 * 获取当前系统对应的 Gradle Wrapper 文件。
 *
 * @param projectDir 项目目录
 * @param osName 操作系统名称
 * @return 当前系统应使用的 Gradle Wrapper 文件
 */
fun resolveGradleWrapperFile(
    projectDir: File,
    osName: String = System.getProperty("os.name"),
): File {
    return if (isWindowsOs(osName)) {
        File(projectDir, "gradlew.bat")
    } else {
        File(projectDir, "gradlew")
    }
}

/**
 * 通过 Gradle init script 获取当前项目所有依赖 jar。
 *
 * @param gradlew Gradle Wrapper 文件
 * @return 当前项目依赖 jar 文件列表
 */
fun collectGradleDependencyJarFiles(gradlew: File): List<File> {
    val location = SourceReadRequestParserCommand::class.java.protectionDomain.codeSource.location
        ?: die("无法获取 jar-source-reader 所在路径")
    val currentFile = File(location.toURI())
    val toolDir = currentFile.parentFile ?: die("无法获取 jar-source-reader 所在目录")
    val initScript = File(toolDir, "print-all-jar.gradle")
    if (!initScript.isFile) {
        die("未找到 Gradle init script: ${initScript.absolutePath}")
    }

    val process = ProcessBuilder(
        gradlew.path,
        "-q",
        "--init-script",
        initScript.absolutePath,
        "printAllJar",
    )
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        die("执行 printAllJar 失败，退出码: $exitCode\n$output")
    }

    return output.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { File(it) }
        .filter { it.isFile }
        .toList()
}

/**
 * 通过 Maven Dependency Plugin 获取当前项目所有依赖 jar。
 *
 * @param projectDir 项目目录
 * @return 当前项目依赖 jar 文件列表
 */
fun collectMavenDependencyJarFiles(projectDir: File): List<File> {
    val outputFile = Files.createTempFile("jar-source-reader-classpath-", ".txt").toFile()
    try {
        val process = ProcessBuilder(
            resolveMavenCommand(projectDir),
            "-q",
            "dependency:build-classpath",
            "-Dmdep.outputFile=${outputFile.absolutePath}",
        )
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            die("执行 dependency:build-classpath 失败，退出码: $exitCode\n$output")
        }

        val jarFiles = parseClasspathJarFiles(outputFile.readText())
        if (jarFiles.isEmpty()) {
            die("dependency:build-classpath 未输出任何依赖 jar")
        }

        return jarFiles
    } finally {
        outputFile.delete()
    }
}

/**
 * 获取 Maven 命令，优先使用项目内 Maven Wrapper。
 *
 * @param projectDir 项目目录
 * @param osName 操作系统名称
 * @return Maven 命令路径或系统 mvn 命令
 */
fun resolveMavenCommand(
    projectDir: File,
    osName: String = System.getProperty("os.name"),
): String {
    val isWindows = isWindowsOs(osName)
    val wrapper = if (isWindows) {
        File(projectDir, "mvnw.cmd")
    } else {
        File(projectDir, "mvnw")
    }

    return if (wrapper.isFile) {
        wrapper.path
    } else if (isWindows) {
        "mvn.cmd"
    } else {
        "mvn"
    }
}

/**
 * 解析 Maven classpath 输出中的 jar 文件。
 *
 * @param classpath classpath 文本
 * @param pathSeparator classpath 路径分隔符
 * @return 已存在的 jar 文件列表
 */
fun parseClasspathJarFiles(
    classpath: String,
    pathSeparator: String = File.pathSeparator,
): List<File> {
    return classpath.split(pathSeparator)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { File(it) }
        .filter { it.isFile && it.name.endsWith(".jar") }
}

/**
 * 判断当前运行环境是否为 Windows。
 *
 * @param osName 操作系统名称
 * @return Windows 系统返回 true
 */
fun isWindowsOs(osName: String = System.getProperty("os.name")): Boolean {
    return osName.lowercase().contains("windows")
}

/**
 * 负责将命令行参数解析为业务参数对象。
 */
class SourceReadRequestParserCommand : CliktCommand() {

    /** 空参数时不自动打印帮助。 */
    override val printHelpOnEmptyArgs: Boolean = false

    /** 允许无子命令直接执行当前命令。 */
    override val invokeWithoutSubcommand: Boolean = true

    /** Maven groupId。 */
    val groupId: String? by option("--group-id")

    /** Maven artifactId。 */
    val artifactId: String? by option("--artifact-id")

    /** 依赖版本号。 */
    val version: String? by option("--version")

    /** 类搜索模式。 */
    val mode: String by option("--mode").default(MODE_FUZZY)

    /** 类名或完全限定类名。 */
    val className: String by option("--class-name").required()

    /** 可选的方法名。 */
    val methodName: String? by option("--method-name")

    /** 可选的 Maven 仓库根目录。 */
    val mavenRepo: String? by option("--maven-repo")

    /** 可选的 Gradle 仓库根目录。 */
    val gradleRepo: String? by option("--gradle-repo")

    /** 是否忽略源码长度限制。 */
    val ignoreLengthLimit: Boolean by option("--ignore-length-limit").flag(default = false)

    /**
     * 这里只做参数解析，不承载业务执行。
     */
    override fun run() {
    }
}

/**
 * 解析本地仓库路径上下文。
 *
 * @param command 已解析的命令对象
 * @return 本地仓库路径上下文
 */
fun buildRepositoryContext(command: SourceReadRequestParserCommand): RepositoryContext {
    val home = System.getenv("USERPROFILE")
        ?: System.getenv("HOME")
        ?: die("无法获取用户主目录")
    val gradleHome = System.getenv("GRADLE_USER_HOME") ?: "$home/.gradle"
    val mavenRepoBase = File(command.mavenRepo ?: "$home/.m2/repository").canonicalFile
    val gradleRepoBase = File(
        command.gradleRepo ?: Paths.get(gradleHome, "caches", "modules-2", "files-2.1").toString()
    ).canonicalFile

    return RepositoryContext(
        home = home,
        gradleHome = gradleHome,
        mavenRepoBase = mavenRepoBase,
        gradleRepoBase = gradleRepoBase,
    )
}

/**
 * 生成 Maven 和 Gradle 的候选仓库目录。
 *
 * @param command 已解析的命令对象
 * @param userHome 用户主目录
 * @param gradleUserHome Gradle 用户目录
 * @return 按顺序返回 Maven 与 Gradle 的候选目录
 */
fun repositoryCandidates(
    command: SourceReadRequestParserCommand,
    userHome: String,
    gradleUserHome: String,
): List<File> {
    val groupId = command.groupId ?: die("exact 模式需要传入 --group-id，或改用 Gradle classpath 搜索")
    val artifactId = command.artifactId ?: die("exact 模式需要传入 --artifact-id，或改用 Gradle classpath 搜索")
    val version = command.version ?: die("exact 模式需要传入 --version，或改用 Gradle classpath 搜索")
    val groupPathMaven = groupId.replace('.', '/')
    val mavenRepoBase = command.mavenRepo ?: "$userHome/.m2/repository"
    val gradleRepoBase = command.gradleRepo
        ?: Paths.get(gradleUserHome, "caches", "modules-2", "files-2.1").toString()

    val mavenDir = Paths.get(mavenRepoBase, groupPathMaven, artifactId, version).toFile()
    val gradleDir = Paths.get(gradleRepoBase, groupId, artifactId, version).toFile()

    return listOf(mavenDir, gradleDir)
}

/**
 * 将类名解析成源码文件路径与嵌套类路径。
 *
 * @param className 类名或完全限定类名
 * @return 目标类解析结果
 */
fun parseClassTarget(className: String): ClassTarget {
    if (className.isBlank()) {
        throw IllegalArgumentException("类名不能为空")
    }

    val outerQualifiedName = className.substringBefore('$')
    val outerSimpleName = outerQualifiedName.substringAfterLast('.')
    val nestedSimpleNames = className.substringAfter('$', "")
        .split('$')
        .filter { it.isNotBlank() }

    return ClassTarget(
        javaFilePath = outerQualifiedName.replace('.', '/') + ".java",
        outerSimpleName = outerSimpleName,
        nestedSimpleNames = nestedSimpleNames,
    )
}

/**
 * 根据用户传入的类名，在 sources jar 中解析实际源码路径。
 *
 * @param zip sources jar 文件
 * @param className 类名或完全限定类名
 * @return 可用于读取源码的目标类信息
 */
fun resolveClassTarget(
    zip: ZipFile,
    className: String,
): ClassTarget {
    val classTarget = parseClassTarget(className)
    if (isFullyQualifiedClassName(className)) {
        return classTarget
    }

    val matchedJavaFilePaths = findJavaFilePathsBySimpleClassName(zip, classTarget.outerSimpleName)
    if (matchedJavaFilePaths.isEmpty()) {
        throw IllegalArgumentException("在 sources jar 中未找到类名: $className")
    }

    if (matchedJavaFilePaths.size > 1) {
        throw IllegalArgumentException(
            buildString {
                appendLine("在 sources jar 中找到了多个同名类: $className")
                appendLine("请改用完整类名。候选路径如下:")
                matchedJavaFilePaths.forEach { javaFilePath ->
                    appendLine("  $javaFilePath")
                }
            }.trimEnd()
        )
    }

    return classTarget.copy(javaFilePath = matchedJavaFilePaths.single())
}

/**
 * 判断传入的类名是否包含完整包路径。
 *
 * @param className 类名或完全限定类名
 * @return 包含包路径时返回 true
 */
fun isFullyQualifiedClassName(className: String): Boolean {
    return className.substringBefore('$').contains('.')
}

/**
 * 在 sources jar 中按顶层类名查找匹配的 Java 源文件。
 *
 * @param zip sources jar 文件
 * @param simpleClassName 顶层类简单名
 * @return 匹配到的 Java 源文件路径列表
 */
fun findJavaFilePathsBySimpleClassName(
    zip: ZipFile,
    simpleClassName: String,
): List<String> {
    val matchedJavaFilePaths = mutableListOf<String>()
    val entries = zip.entries()
    while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        val isMatchedJavaFile = entry.name == "$simpleClassName.java" || entry.name.endsWith("/$simpleClassName.java")
        if (!entry.isDirectory && isMatchedJavaFile) {
            matchedJavaFilePaths += entry.name
        }
    }

    return matchedJavaFilePaths
}

/**
 * 根据是否传入方法名，返回目标类源码、重载方法源码或超长源码的骨架结构。
 *
 * @param source Java 源码文本
 * @param className 类名或完全限定类名
 * @param methodName 可选的方法名
 * @param ignoreLengthLimit 是否忽略长度限制
 * @param lineLimit 最大行数限制
 * @return 需要输出的源码内容
 */
fun resolveOutput(
    source: String,
    className: String,
    methodName: String?,
    ignoreLengthLimit: Boolean = false,
    lineLimit: Int = DEFAULT_LINE_LIMIT,
): String {
    val classTarget = parseClassTarget(className)
    val targetType = findTargetType(source, classTarget)

    if (methodName != null) {
        return renderMethods(targetType, methodName)
    }

    val targetSource = normalizeNewlines(targetType.toString()).trim()
    val lineCount = targetSource.lineSequence().count()
    if (lineCount > lineLimit && !ignoreLengthLimit) {
        return renderSkeleton(targetType, lineLimit)
    }

    return targetSource
}

/**
 * 在源码中定位目标类或内部类。
 *
 * @param source Java 源码文本
 * @param classTarget 目标类解析结果
 * @return 目标类型声明
 */
fun findTargetType(
    source: String,
    classTarget: ClassTarget,
): TypeDeclaration<*> {
    val compilationUnit = StaticJavaParser.parse(source)
    var currentType = findTopLevelType(compilationUnit, classTarget.outerSimpleName)
        ?: throw IllegalArgumentException("未找到顶层类型: ${classTarget.outerSimpleName}")

    for (nestedName in classTarget.nestedSimpleNames) {
        currentType = findNestedType(currentType, nestedName)
            ?: throw IllegalArgumentException("未找到内部类: $nestedName")
    }

    return currentType
}

/**
 * 在编译单元中查找顶层类型。
 *
 * @param compilationUnit 编译单元
 * @param typeName 顶层类型名
 * @return 匹配到的类型声明，未找到时返回 null
 */
fun findTopLevelType(
    compilationUnit: CompilationUnit,
    typeName: String,
): TypeDeclaration<*>? {
    return compilationUnit.types.firstOrNull { it.nameAsString == typeName }
}

/**
 * 在当前类型中查找直接嵌套的内部类型。
 *
 * @param type 当前类型
 * @param nestedName 目标内部类名
 * @return 匹配到的内部类型，未找到时返回 null
 */
fun findNestedType(
    type: TypeDeclaration<*>,
    nestedName: String,
): TypeDeclaration<*>? {
    return type.members
        .filterIsInstance<TypeDeclaration<*>>()
        .firstOrNull { it.nameAsString == nestedName }
}

/**
 * 提取目标类中指定名称的全部方法源码。
 *
 * @param targetType 目标类型
 * @param methodName 目标方法名
 * @return 单个方法源码或多个重载方法源码
 */
fun renderMethods(
    targetType: TypeDeclaration<*>,
    methodName: String,
): String {
    val matchedMethods = targetType.members
        .filterIsInstance<MethodDeclaration>()
        .filter { it.nameAsString == methodName }

    if (matchedMethods.isEmpty()) {
        throw IllegalArgumentException("未找到方法: $methodName")
    }

    if (matchedMethods.size == 1) {
        return normalizeNewlines(matchedMethods.first().toString()).trim()
    }

    return matchedMethods
        .joinToString("\n\n") { method -> normalizeNewlines(method.toString()).trim() }
}

/**
 * 生成超长类的骨架输出，保留字段声明、公开方法签名和文档注释。
 *
 * @param targetType 目标类型
 * @param lineLimit 行数限制
 * @return 类骨架文本
 */
fun renderSkeleton(
    targetType: TypeDeclaration<*>,
    lineLimit: Int,
): String {
    val builder = StringBuilder()
    val typeJavadoc = targetType.javadocComment.orElse(null)
    if (typeJavadoc != null) {
        builder.append(normalizeNewlines(typeJavadoc.toString()).trim())
        builder.append('\n')
    }

    builder.append(renderTypeHeader(targetType))
    builder.append('\n')

    val fields = targetType.members.filterIsInstance<FieldDeclaration>()
    val methods = targetType.members
        .filterIsInstance<MethodDeclaration>()
        .filter { it.isPublic || it.isProtected }

    for (field in fields) {
        builder.append('\n')
        builder.append(normalizeNewlines(field.toString()).trim())
        builder.append('\n')
    }

    for (method in methods) {
        builder.append('\n')
        builder.append(renderMethodSignature(method))
        builder.append('\n')
    }

    builder.append("}\n")
    builder.append('\n')
    builder.append("提示：该类源码超过 ")
    builder.append(lineLimit)
    builder.append(" 行，已自动降级为类结构展示。如需查看完整源码，请传递 --ignore-length-limit 参数")
    return builder.toString().trim()
}

/**
 * 从词法范围中提取类型头部，保留源码里的原始换行与空格。
 *
 * @param targetType 目标类型
 * @return 包含左花括号的类型声明
 */
fun renderTypeHeader(targetType: TypeDeclaration<*>): String {
    val builder = StringBuilder()
    for (token in targetType.tokenRange.orElseThrow { IllegalArgumentException("未找到类型的 tokenRange") }) {
        builder.append(token.text)
        if (token.text == "{") {
            break
        }
    }
    return normalizeNewlines(builder.toString()).trimEnd()
}

/**
 * 生成方法签名文本，不包含方法体。
 *
 * @param method 方法声明
 * @return 方法签名
 */
fun renderMethodSignature(method: MethodDeclaration): String {
    val signatureSource = method.clone().apply {
        setBody(null)
    }
    return normalizeNewlines(signatureSource.toString()).trim()
}

/**
 * 统一换行符，避免不同平台下的断言差异。
 *
 * @param text 原始文本
 * @return 归一化后的文本
 */
fun normalizeNewlines(text: String): String {
    return text.replace("\r\n", "\n")
}
