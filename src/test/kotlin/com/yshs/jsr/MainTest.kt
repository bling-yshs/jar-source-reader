package com.yshs.jsr

import com.github.javaparser.StaticJavaParser
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainTest {

    private val demoJavaSource = """
        package com.example;

        public class Demo {
            public String hello() {
                return "hello";
            }

            public int sum(int a, int b) {
                return a + b;
            }
        }
    """.trimIndent()

    private val overloadedJavaSource = """
        package com.example;

        public class Demo {
            public String sum(String value) {
                return value;
            }

            public int sum(int a, int b) {
                return a + b;
            }
        }
    """.trimIndent()

    private val nestedJavaSource = """
        package com.example;

        public class Outer {
            public static class Inner {
                public String hello() {
                    return "inner";
                }
            }
        }
    """.trimIndent()

    private val formattedHeaderJavaSource = """
        package com.example;

        public class StyledDemo
            extends BaseDemo
            implements Runnable, AutoCloseable
        {
            public void run() {
            }

            public void close() {
            }
        }
    """.trimIndent()

    private val fieldJavadocJavaSource = """
        package com.example;

        public class FieldDocDemo {
            /**
             * duplicated field doc
             */
            protected final String value = "demo";

            public String hello() {
                return value;
            }
        }
    """.trimIndent()

    private val methodJavadocJavaSource = """
        package com.example;

        public class MethodDocDemo {
            /**
             * duplicated method doc
             *
             * @return demo
             */
            protected String convertValue(Object value) {
                return value.toString();
            }
        }
    """.trimIndent()

    private val longJavaSource = buildString {
        appendLine("package com.example;")
        appendLine()
        appendLine("/**")
        appendLine(" * large demo")
        appendLine(" */")
        appendLine("public class LargeDemo {")
        repeat(520) { index ->
            appendLine("    private String helper$index() {")
            appendLine("        return \"helper$index\";")
            appendLine("    }")
        }
        appendLine()
        appendLine("    /**")
        appendLine("     *   hello summary")
        appendLine("     * use {@code hello}")
        appendLine("     *")
        appendLine("     * @return greeting")
        appendLine("     */")
        appendLine("    public String hello() {")
        appendLine("        return \"hello\";")
        appendLine("    }")
        appendLine("}")
    }.trimEnd()

    /**
     * 验证必填参数可以按原有两种写法正常解析。
     */
    @Test
    fun parseCommandKeepsRequiredOptionParsing() {
        val parsed = parseCommand(
            arrayOf(
                "--group-id=com.example",
                "--artifact-id", "demo-lib",
                "--version", "1.0.0",
                "--class-name", "com.example.Demo",
            )
        )

        assertEquals("com.example", parsed.groupId)
        assertEquals("demo-lib", parsed.artifactId)
        assertEquals("1.0.0", parsed.version)
        assertEquals("com.example.Demo", parsed.className)
        assertNull(parsed.mavenRepo)
        assertNull(parsed.gradleRepo)
        assertNull(parsed.methodName)
    }

    /**
     * 验证可选仓库参数和方法名参数可以被正确解析。
     */
    @Test
    fun parseCommandSupportsOptionalRepositoryOptions() {
        val parsed = parseCommand(
            arrayOf(
                "--group-id", "com.example",
                "--artifact-id", "demo-lib",
                "--version", "1.0.0",
                "--class-name", "com.example.Demo",
                "--method-name", "sum",
                "--maven-repo", "C:/Users/test/.m2/repository",
                "--gradle-repo=C:/Users/test/.gradle/caches/modules-2/files-2.1",
            )
        )

        assertEquals("sum", parsed.methodName)
        assertEquals("C:/Users/test/.m2/repository", parsed.mavenRepo)
        assertEquals("C:/Users/test/.gradle/caches/modules-2/files-2.1", parsed.gradleRepo)
    }

    /**
     * 验证布尔开关参数会通过 Clikt 正确解析。
     */
    @Test
    fun parseCommandSupportsBooleanFlagOption() {
        val parsed = parseCommand(
            arrayOf(
                "--group-id", "com.example",
                "--artifact-id", "demo-lib",
                "--version", "1.0.0",
                "--class-name", "com.example.Demo",
                "--ignore-length-limit",
            )
        )

        assertTrue(parsed.ignoreLengthLimit)
    }

    /**
     * 验证用户显式传入仓库目录时，会优先使用这些目录。
     */
    @Test
    fun repositoryCandidatesPrefersExplicitRepositoryPaths() {
        val parsed = parseCommand(
            arrayOf(
                "--group-id", "com.example",
                "--artifact-id", "demo-lib",
                "--version", "1.0.0",
                "--class-name", "com.example.Demo",
                "--maven-repo", "C:/custom/m2/repository",
                "--gradle-repo", "C:/custom/gradle/files-2.1",
            )
        )

        val candidates = repositoryCandidates(parsed, "C:/Users/test", "C:/Users/test/.gradle")

        assertEquals("C:\\custom\\m2\\repository\\com\\example\\demo-lib\\1.0.0", candidates[0].path)
        assertEquals("C:\\custom\\gradle\\files-2.1\\com.example\\demo-lib\\1.0.0", candidates[1].path)
    }

    /**
     * 验证未传入仓库目录时，会回退到默认本地仓库位置。
     */
    @Test
    fun repositoryCandidatesFallsBackToDefaultRepositoryPaths() {
        val parsed = parseCommand(
            arrayOf(
                "--group-id", "com.example",
                "--artifact-id", "demo-lib",
                "--version", "1.0.0",
                "--class-name", "com.example.Demo",
            )
        )

        val candidates = repositoryCandidates(parsed, "C:/Users/test", "C:/Users/test/.gradle")

        assertEquals("C:\\Users\\test\\.m2\\repository\\com\\example\\demo-lib\\1.0.0", candidates[0].path)
        assertEquals(
            "C:\\Users\\test\\.gradle\\caches\\modules-2\\files-2.1\\com.example\\demo-lib\\1.0.0",
            candidates[1].path,
        )
    }

    /**
     * 验证只传顶层类简单名时，也能在 sources jar 中定位源码文件。
     */
    @Test
    fun resolveClassTargetSupportsSimpleTopLevelClassName() {
        createSourcesJar("com/example/Demo.java").use { zip ->
            val resolved = resolveClassTarget(zip, "Demo")

            assertEquals("com/example/Demo.java", resolved.javaFilePath)
            assertEquals("Demo", resolved.outerSimpleName)
            assertTrue(resolved.nestedSimpleNames.isEmpty())
        }
    }

    /**
     * 验证只传内部类简单名路径时，会先命中外部类源码文件。
     */
    @Test
    fun resolveClassTargetSupportsSimpleNestedClassName() {
        createSourcesJar("com/example/Outer.java").use { zip ->
            val resolved = resolveClassTarget(zip, "Outer\$Inner")

            assertEquals("com/example/Outer.java", resolved.javaFilePath)
            assertEquals("Outer", resolved.outerSimpleName)
            assertEquals(listOf("Inner"), resolved.nestedSimpleNames)
        }
    }

    /**
     * 验证简单类名出现冲突时，会提示用户改用完整类名。
     */
    @Test
    fun resolveClassTargetThrowsWhenSimpleClassNameIsAmbiguous() {
        createSourcesJar(
            "com/example/Demo.java",
            "org/example/Demo.java",
        ).use { zip ->
            val error = assertFailsWith<IllegalArgumentException> {
                resolveClassTarget(zip, "Demo")
            }

            assertTrue(error.message!!.contains("多个同名类"))
            assertTrue(error.message!!.contains("com/example/Demo.java"))
            assertTrue(error.message!!.contains("org/example/Demo.java"))
        }
    }

    /**
     * 验证简单类名未命中时，会抛出明确的异常。
     */
    @Test
    fun resolveClassTargetThrowsWhenSimpleClassNameDoesNotExist() {
        createSourcesJar("com/example/Demo.java").use { zip ->
            val error = assertFailsWith<IllegalArgumentException> {
                resolveClassTarget(zip, "MissingDemo")
            }

            assertTrue(error.message!!.contains("MissingDemo"))
        }
    }

    /**
     * 验证 die 会输出错误信息，并通过退出回调结束流程。
     */
    @Test
    fun dieWritesToStderrAndExitsWithCode1() {
        val err = ByteArrayOutputStream()
        val exit = assertFailsWith<ExitCalledException> {
            die("boom", PrintStream(err)) { code ->
                throw ExitCalledException(code)
            }
        }

        assertEquals(1, exit.code)
        assertEquals("boom", err.toString().trim())
    }

    /**
     * 验证未指定方法名时，直接返回完整类源码。
     */
    @Test
    fun resolveOutputReturnsFullClassSourceWhenMethodNameIsAbsent() {
        val resolved = resolveOutput(demoJavaSource, "com.example.Demo", null)

        assertEquals(
            """
            public class Demo {

                public String hello() {
                    return "hello";
                }

                public int sum(int a, int b) {
                    return a + b;
                }
            }
            """.trimIndent(),
            resolved,
        )
    }

    /**
     * 验证指定方法名时，只返回匹配到的方法源码。
     */
    @Test
    fun resolveOutputReturnsMatchingMethodSourceWhenMethodNameIsProvided() {
        val resolved = resolveOutput(demoJavaSource, "com.example.Demo", "sum")

        assertEquals(
            """
            public int sum(int a, int b) {
                return a + b;
            }
            """.trimIndent(),
            resolved,
        )
    }

    /**
     * 验证指定的方法不存在时，会抛出明确的异常。
     */
    @Test
    fun resolveOutputThrowsWhenMethodDoesNotExist() {
        val error = assertFailsWith<IllegalArgumentException> {
            resolveOutput(demoJavaSource, "com.example.Demo", "missing")
        }

        assertTrue(error.message!!.contains("missing"))
    }

    /**
     * 验证同名重载方法会全部返回，避免只命中第一个实现。
     */
    @Test
    fun resolveOutputReturnsAllOverloadedMethods() {
        val resolved = resolveOutput(overloadedJavaSource, "com.example.Demo", "sum")

        assertTrue(resolved.contains("public String sum(String value)"))
        assertTrue(resolved.contains("public int sum(int a, int b)"))
        assertTrue(resolved.contains("/* overload").not())
    }

    /**
     * 验证内部类可以先定位外部类源码，再提取目标内部类。
     */
    @Test
    fun resolveOutputSupportsNestedClassSource() {
        val resolved = resolveOutput(nestedJavaSource, "com.example.Outer\$Inner", null)

        assertEquals(
            """
            public static class Inner {

                public String hello() {
                    return "inner";
                }
            }
            """.trimIndent(),
            resolved,
        )
    }

    /**
     * 验证内部类的方法读取仍然只输出目标方法源码。
     */
    @Test
    fun resolveOutputSupportsNestedClassMethodExtraction() {
        val resolved = resolveOutput(nestedJavaSource, "com.example.Outer\$Inner", "hello")

        assertEquals(
            """
            public String hello() {
                return "inner";
            }
            """.trimIndent(),
            resolved,
        )
    }

    /**
     * 验证类头会按源码中的原始格式保留下来，而不是重新拼接。
     */
    @Test
    fun renderTypeHeaderPreservesOriginalHeaderFormatting() {
        val targetType = StaticJavaParser.parse(formattedHeaderJavaSource)
            .types
            .first()

        val resolved = renderTypeHeader(targetType)

        assertEquals(
            """
            public class StyledDemo
                extends BaseDemo
                implements Runnable, AutoCloseable
            {
            """.trimIndent(),
            resolved,
        )
    }

    /**
     * 验证在没有 tokenRange 时，类头兜底逻辑也会保留左花括号所在行。
     */
    @Test
    fun renderTypeHeaderKeepsOpeningBracePositionWithoutTokenRange() {
        val targetType = StaticJavaParser.parse(formattedHeaderJavaSource)
            .types
            .first()
            .clone()

        val resolved = renderTypeHeader(targetType)

        assertEquals(
            """
            public class StyledDemo
                extends BaseDemo
                implements Runnable, AutoCloseable
            {
            """.trimIndent(),
            resolved,
        )
    }

    /**
     * 验证字段注释在骨架输出里只出现一次。
     */
    @Test
    fun resolveOutputAvoidsDuplicatedFieldJavadocInSkeletonMode() {
        val resolved = resolveOutput(
            source = fieldJavadocJavaSource,
            className = "com.example.FieldDocDemo",
            methodName = null,
            lineLimit = 3,
        )

        assertEquals(1, resolved.split("duplicated field doc").size - 1)
    }

    /**
     * 验证方法注释在骨架输出里只出现一次。
     */
    @Test
    fun resolveOutputAvoidsDuplicatedMethodJavadocInSkeletonMode() {
        val resolved = resolveOutput(
            source = methodJavadocJavaSource,
            className = "com.example.MethodDocDemo",
            methodName = null,
            lineLimit = 3,
        )

        assertEquals(1, resolved.split("duplicated method doc").size - 1)
    }

    /**
     * 验证骨架输出不会再对成员和注释追加额外缩进。
     */
    @Test
    fun resolveOutputKeepsSkeletonMembersWithoutExtraIndentation() {
        val resolved = resolveOutput(
            source = methodJavadocJavaSource,
            className = "com.example.MethodDocDemo",
            methodName = null,
            lineLimit = 3,
        )

        assertTrue(resolved.contains("\nprotected String convertValue(Object value);"))
        assertTrue(resolved.contains("\n    protected String convertValue(Object value);").not())
        assertTrue(resolved.contains("\n/**"))
        assertTrue(resolved.contains("\n    /**").not())
    }

    /**
     * 验证超长源码在指定方法名时，仍然允许直接返回方法实现。
     */
    @Test
    fun resolveOutputReturnsMethodImplementationForLongSourceWhenMethodIsSpecified() {
        val resolved = resolveOutput(longJavaSource, "com.example.LargeDemo", "hello")

        assertTrue(resolved.contains("public String hello()"))
        assertTrue(resolved.contains("return \"hello\";"))
    }

    /**
     * 验证忽略长度限制后，会直接返回完整目标类源码。
     */
    @Test
    fun resolveOutputReturnsFullClassSourceWhenLengthLimitIsIgnored() {
        val resolved = resolveOutput(
            source = longJavaSource,
            className = "com.example.LargeDemo",
            methodName = null,
            ignoreLengthLimit = true,
        )

        assertTrue(resolved.contains("private String helper0()"))
        assertTrue(resolved.contains("return \"hello\";"))
    }
}

/**
 * 创建只包含指定条目的临时 sources jar，供路径解析测试使用。
 *
 * @param entryNames 需要写入的 zip 条目路径
 * @return 可读取的临时 ZipFile
 */
private fun createSourcesJar(vararg entryNames: String): ZipFile {
    val tempJarFile = Files.createTempFile("jsr-test-", ".jar").toFile()
    tempJarFile.deleteOnExit()

    ZipOutputStream(tempJarFile.outputStream().buffered()).use { output ->
        entryNames.forEach { entryName ->
            output.putNextEntry(ZipEntry(entryName))
            output.write("// test".toByteArray())
            output.closeEntry()
        }
    }

    return ZipFile(tempJarFile)
}

private class ExitCalledException(val code: Int) : RuntimeException()
