import { chmod, cp, mkdir, mkdtemp, rm, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const sourceReturnStatement = "return UUID.fastUUID().toString(true);";

interface CommandResult {
    exitCode: number;
    stderr: string;
    stdout: string;
}

interface CommandOptions {
    cwd: string;
    env?: Record<string, string | undefined>;
}

/**
 * 执行外部命令并收集完整输出。
 *
 * @param command 可执行命令
 * @param args 命令行参数
 * @param options 工作目录和附加环境变量
 * @return 命令执行结果
 */
async function runCommand(
    command: string,
    args: string[],
    options: CommandOptions,
): Promise<CommandResult> {
    const process = Bun.spawn([command, ...args], {
        cwd: options.cwd,
        env: {
            ...Bun.env,
            ...options.env,
        },
        stderr: "pipe",
        stdout: "pipe",
    });
    const [stdout, stderr, exitCode] = await Promise.all([
        new Response(process.stdout).text(),
        new Response(process.stderr).text(),
        process.exited,
    ]);

    return {
        exitCode,
        stderr,
        stdout,
    };
}

/**
 * 执行必须成功的外部命令。
 *
 * @param command 可执行命令
 * @param args 命令行参数
 * @param options 工作目录和附加环境变量
 * @return 命令标准输出
 */
async function runSuccessfulCommand(
    command: string,
    args: string[],
    options: CommandOptions,
): Promise<string> {
    const result = await runCommand(command, args, options);
    if (result.exitCode !== 0) {
        throw new Error(
            `命令执行失败：${command} ${args.join(" ")}\n` +
                `退出码：${result.exitCode}\n标准输出：\n${result.stdout}\n标准错误：\n${result.stderr}`,
        );
    }

    return result.stdout;
}

/**
 * 验证文本包含预期内容。
 *
 * @param output 待验证输出
 * @param expected 预期文本
 * @param description 验证说明
 */
function assertContains(output: string, expected: string, description: string): void {
    if (!output.includes(expected)) {
        throw new Error(`验证失败：${description}\n预期包含：${expected}\n实际输出：\n${output}`);
    }
}

/**
 * 验证命令使用预期退出码结束。
 *
 * @param result 命令执行结果
 * @param expectedExitCode 预期退出码
 * @param description 验证说明
 * @return 合并后的命令输出
 */
function assertExitCode(
    result: CommandResult,
    expectedExitCode: number,
    description: string,
): string {
    if (result.exitCode !== expectedExitCode) {
        throw new Error(
            `验证失败：${description}\n预期退出码：${expectedExitCode}\n实际退出码：${result.exitCode}\n` +
                `标准输出：\n${result.stdout}\n标准错误：\n${result.stderr}`,
        );
    }

    return `${result.stdout}${result.stderr}`;
}

/**
 * 调用最终打包的 jar-source-reader。
 *
 * @param projectDir 测试项目目录
 * @param toolJar 工具 jar 路径
 * @param args 工具参数
 * @param env 附加环境变量
 * @return 命令执行结果
 */
async function runTool(
    projectDir: string,
    toolJar: string,
    args: string[],
    env: Record<string, string | undefined>,
): Promise<CommandResult> {
    return runCommand("java", ["-Dfile.encoding=UTF-8", "-jar", toolJar, ...args], {
        cwd: projectDir,
        env,
    });
}

/**
 * 生成各模式共用的仓库参数。
 *
 * @param mavenRepo Maven 仓库根目录
 * @param gradleRepo Gradle 仓库根目录
 * @return 工具仓库参数
 */
function repositoryArguments(mavenRepo: string, gradleRepo: string): string[] {
    return [`--maven-repo=${mavenRepo}`, `--gradle-repo=${gradleRepo}`];
}

/**
 * 验证单一依赖场景下的 fuzzy、exact 与 search 模式。
 *
 * @param projectDir 测试项目目录
 * @param toolJar 工具 jar 路径
 * @param mavenRepo Maven 仓库根目录
 * @param gradleRepo Gradle 仓库根目录
 * @param label 构建工具名称
 * @param env 附加环境变量
 */
async function verifySuccessCases(
    projectDir: string,
    toolJar: string,
    mavenRepo: string,
    gradleRepo: string,
    label: string,
    env: Record<string, string | undefined>,
): Promise<void> {
    const repositories = repositoryArguments(mavenRepo, gradleRepo);
    const fuzzyResult = await runTool(
        projectDir,
        toolJar,
        [
            "--mode=fuzzy",
            "--class-name=cn.hutool.core.util.IdUtil",
            "--method-name=fastSimpleUUID",
            ...repositories,
        ],
        env,
    );
    const fuzzyOutput = assertExitCode(fuzzyResult, 0, `${label} fuzzy 唯一命中`);
    assertContains(fuzzyOutput, "fuzzy 模式命中类: cn.hutool.core.util.IdUtil", `${label} fuzzy 类名`);
    assertContains(fuzzyOutput, sourceReturnStatement, `${label} fuzzy 真实源码`);

    const exactResult = await runTool(
        projectDir,
        toolJar,
        [
            "--mode=exact",
            "--group-id=cn.hutool",
            "--artifact-id=hutool-core",
            "--version=5.8.36",
            "--class-name=cn.hutool.core.util.IdUtil",
            "--method-name=fastSimpleUUID",
            ...repositories,
        ],
        env,
    );
    const exactOutput = assertExitCode(exactResult, 0, `${label} exact 读取源码`);
    assertContains(exactOutput, sourceReturnStatement, `${label} exact 真实源码`);

    const searchResult = await runTool(
        projectDir,
        toolJar,
        [
            "--mode=search",
            "--group-id=cn.hutool",
            "--artifact-id=hutool-core",
            "--version=5.8.36",
            "--pattern=return UUID\\.fastUUID\\(\\)\\.toString\\(true\\);",
            "--max-count=1",
            ...repositories,
        ],
        env,
    );
    const searchOutput = assertExitCode(searchResult, 0, `${label} search 搜索源码`);
    assertContains(searchOutput, "IdUtil.java:", `${label} search 源码路径与行号`);
    assertContains(searchOutput, sourceReturnStatement, `${label} search 真实源码`);
}

/**
 * 验证多候选错误和 exact 恢复流程。
 *
 * @param projectDir 测试项目目录
 * @param toolJar 工具 jar 路径
 * @param mavenRepo Maven 仓库根目录
 * @param gradleRepo Gradle 仓库根目录
 * @param label 构建工具名称
 * @param env 附加环境变量
 */
async function verifyAmbiguousCase(
    projectDir: string,
    toolJar: string,
    mavenRepo: string,
    gradleRepo: string,
    label: string,
    env: Record<string, string | undefined>,
): Promise<void> {
    const repositories = repositoryArguments(mavenRepo, gradleRepo);
    const fuzzyResult = await runTool(
        projectDir,
        toolJar,
        ["--mode=fuzzy", "--class-name=cn.hutool.core.util.IdUtil", ...repositories],
        env,
    );
    const fuzzyOutput = assertExitCode(fuzzyResult, 1, `${label} fuzzy 多候选`);
    assertContains(fuzzyOutput, "找到多个可能的 sources jar", `${label} 多候选提示`);
    assertContains(fuzzyOutput, "请改用 exact 模式", `${label} exact 恢复提示`);
    assertContains(fuzzyOutput, "hutool-core-5.8.36-sources.jar", `${label} hutool-core 候选`);
    assertContains(fuzzyOutput, "hutool-all-5.8.36-sources.jar", `${label} hutool-all 候选`);

    const exactResult = await runTool(
        projectDir,
        toolJar,
        [
            "--mode=exact",
            "--group-id=cn.hutool",
            "--artifact-id=hutool-core",
            "--version=5.8.36",
            "--class-name=cn.hutool.core.util.IdUtil",
            "--method-name=fastSimpleUUID",
            ...repositories,
        ],
        env,
    );
    const exactOutput = assertExitCode(exactResult, 0, `${label} exact 恢复`);
    assertContains(exactOutput, sourceReturnStatement, `${label} exact 恢复真实源码`);
}

/**
 * 验证 fuzzy 模式会拒绝不存在的类。
 *
 * @param projectDir 测试项目目录
 * @param toolJar 工具 jar 路径
 * @param mavenRepo Maven 仓库根目录
 * @param gradleRepo Gradle 仓库根目录
 * @param label 构建工具名称
 * @param env 附加环境变量
 */
async function verifyMissingClassCase(
    projectDir: string,
    toolJar: string,
    mavenRepo: string,
    gradleRepo: string,
    label: string,
    env: Record<string, string | undefined>,
): Promise<void> {
    const fuzzyResult = await runTool(
        projectDir,
        toolJar,
        [
            "--mode=fuzzy",
            "--class-name=cn.hutool.core.util.MissingIdUtil",
            ...repositoryArguments(mavenRepo, gradleRepo),
        ],
        env,
    );
    const fuzzyOutput = assertExitCode(fuzzyResult, 1, `${label} fuzzy 不存在类`);
    assertContains(fuzzyOutput, "未找到任何包含目标类的 sources jar", `${label} 不存在类提示`);
}

/**
 * 验证指定路径是完整的 Skill 发布目录。
 *
 * @param packageDir Skill 发布目录
 * @return 工具 jar 路径
 */
async function validateSkillPackage(packageDir: string): Promise<string> {
    const toolJar = join(packageDir, "tool", "jar-source-reader.jar");
    const gradleInitScript = join(packageDir, "tool", "print-all-jar.gradle");

    try {
        await Promise.all([stat(toolJar), stat(gradleInitScript)]);
    } catch {
        throw new Error(`Skill 发布目录不完整：${packageDir}`);
    }

    return toolJar;
}

/**
 * 执行 Gradle 与 Maven 的真实项目测试。
 */
async function main(): Promise<void> {
    const packageArgument = Bun.argv[2];
    if (packageArgument === undefined || Bun.argv.length !== 3) {
        throw new Error(`用法：${Bun.argv[1]} <skill-package-directory>`);
    }

    const scriptDir = dirname(fileURLToPath(import.meta.url));
    const workspaceDir = resolve(scriptDir, "..", "..");
    const packageDir = resolve(packageArgument);
    const toolJar = await validateSkillPackage(packageDir);
    const tempDir = await mkdtemp(join(tmpdir(), "jar-source-reader-"));
    const gradleProject = join(tempDir, "gradle-project");
    const mavenProject = join(tempDir, "maven-project");
    const gradleUserHome = join(tempDir, "gradle-user-home");
    const mavenRepo = join(tempDir, "maven-user-home", "repository");
    const emptyMavenRepo = join(tempDir, "empty-maven-repository");
    const emptyGradleRepo = join(tempDir, "empty-gradle-repository");
    const gradleRepo = join(gradleUserHome, "caches", "modules-2", "files-2.1");
    const baseEnvironment = {
        GRADLE_USER_HOME: gradleUserHome,
        MAVEN_OPTS: `-Dmaven.repo.local=${mavenRepo}`,
    };

    try {
        await cp(join(workspaceDir, "integration-tests", "gradle"), gradleProject, { recursive: true });
        await Bun.write(join(gradleProject, "gradlew"), Bun.file(join(workspaceDir, "gradlew")));
        await cp(join(workspaceDir, "gradle"), join(gradleProject, "gradle"), { recursive: true });
        await chmod(join(gradleProject, "gradlew"), 0o755);
        await cp(join(workspaceDir, "integration-tests", "maven"), mavenProject, { recursive: true });
        await Promise.all([
            mkdir(gradleUserHome, { recursive: true }),
            mkdir(mavenRepo, { recursive: true }),
            mkdir(emptyMavenRepo, { recursive: true }),
            mkdir(emptyGradleRepo, { recursive: true }),
        ]);

        const gradleRunOutput = await runSuccessfulCommand(
            "./gradlew",
            ["--no-daemon", "run", "downloadSources"],
            { cwd: gradleProject, env: baseEnvironment },
        );
        assertContains(gradleRunOutput, "UUID=", "Gradle 测试项目运行");
        await verifySuccessCases(
            gradleProject,
            toolJar,
            emptyMavenRepo,
            gradleRepo,
            "Gradle",
            baseEnvironment,
        );

        const ambiguousEnvironment = {
            ...baseEnvironment,
            JSR_ENABLE_AMBIGUOUS_HUTOOL: "true",
        };
        const gradleAmbiguousRunOutput = await runSuccessfulCommand(
            "./gradlew",
            ["--no-daemon", "run", "downloadSources"],
            { cwd: gradleProject, env: ambiguousEnvironment },
        );
        assertContains(gradleAmbiguousRunOutput, "UUID=", "Gradle 冲突测试项目运行");
        await verifyAmbiguousCase(
            gradleProject,
            toolJar,
            emptyMavenRepo,
            gradleRepo,
            "Gradle",
            ambiguousEnvironment,
        );
        await verifyMissingClassCase(
            gradleProject,
            toolJar,
            emptyMavenRepo,
            gradleRepo,
            "Gradle",
            ambiguousEnvironment,
        );

        const mavenRunOutput = await runSuccessfulCommand(
            "mvn",
            ["--batch-mode", "--quiet", "verify", "dependency:resolve-sources", "exec:java"],
            { cwd: mavenProject, env: baseEnvironment },
        );
        assertContains(mavenRunOutput, "UUID=", "Maven 测试项目运行");
        await verifySuccessCases(
            mavenProject,
            toolJar,
            mavenRepo,
            emptyGradleRepo,
            "Maven",
            baseEnvironment,
        );

        const mavenAmbiguousRunOutput = await runSuccessfulCommand(
            "mvn",
            ["--batch-mode", "--quiet", "verify", "dependency:resolve-sources", "exec:java"],
            { cwd: mavenProject, env: ambiguousEnvironment },
        );
        assertContains(mavenAmbiguousRunOutput, "UUID=", "Maven 冲突测试项目运行");
        await verifyAmbiguousCase(
            mavenProject,
            toolJar,
            mavenRepo,
            emptyGradleRepo,
            "Maven",
            ambiguousEnvironment,
        );
        await verifyMissingClassCase(
            mavenProject,
            toolJar,
            mavenRepo,
            emptyGradleRepo,
            "Maven",
            ambiguousEnvironment,
        );

        console.log("真实 Java 项目测试通过");
    } finally {
        await rm(tempDir, { force: true, recursive: true });
    }
}

await main();
