package com.n8n.mobile.studio.check

import java.io.File

/**
 * Minimal test runner for the offline check harness.
 *
 * Discovers `@org.junit.Test` methods in the compiled output and executes them,
 * honouring `@Before`/`@After`. It exists so the runtime core can be verified
 * without Gradle or Maven; CI runs the same classes through real JUnit4.
 */
object TestRunner {

    @JvmStatic
    fun main(args: Array<String>) {
        val root = File(args.firstOrNull() ?: "build/check-classes")
        val testRoots = args.drop(1).map { File(it) }
        val classNames = discover(root, testRoots).sorted()

        var passed = 0
        val failures = mutableListOf<String>()

        classNames.forEach { className ->
            val clazz = runCatching { Class.forName(className) }.getOrNull() ?: return@forEach
            val methods = clazz.declaredMethods
            val tests = methods.filter { it.annotations.any { annotation -> annotation.annotationClass.simpleName == "Test" } }
            if (tests.isEmpty()) return@forEach
            val beforeEach = methods.filter { method -> method.annotations.any { it.annotationClass.simpleName == "Before" } }
            val afterEach = methods.filter { method -> method.annotations.any { it.annotationClass.simpleName == "After" } }

            tests.sortedBy { it.name }.forEach { test ->
                val label = "${clazz.simpleName}.${test.name}"
                try {
                    val instance = clazz.getDeclaredConstructor().newInstance()
                    test.isAccessible = true
                    beforeEach.forEach { it.isAccessible = true; it.invoke(instance) }
                    test.invoke(instance)
                    afterEach.forEach { it.isAccessible = true; it.invoke(instance) }
                    passed++
                    println("PASS $label")
                } catch (error: Throwable) {
                    val cause = error.cause ?: error
                    failures += label
                    println("FAIL $label: ${cause::class.simpleName}: ${cause.message}")
                    cause.stackTrace.take(6).forEach { line -> println("       at $line") }
                }
            }
        }

        println()
        println("tests: $passed passed, ${failures.size} failed")
        if (failures.isNotEmpty()) {
            failures.forEach { println("  failed: $it") }
            exitProcess(1)
        }
    }

    private fun discover(root: File, testRoots: List<File>): List<String> {
        val classNames = mutableSetOf<String>()
        testRoots.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            collectFiles(dir).forEach { file ->
                val relative = file.relativeTo(dir).path.removeSuffix(".class")
                classNames += relative.replace(File.separatorChar, '.')
            }
        }
        return classNames.toList()
    }

    private fun collectFiles(dir: File): List<File> =
        dir.listFiles()?.flatMap { if (it.isDirectory) collectFiles(it) else listOf(it) } ?: emptyList()

    private fun exitProcess(code: Int) {
        System.out.flush()
        kotlin.system.exitProcess(code)
    }
}
