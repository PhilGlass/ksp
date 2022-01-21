package com.google.devtools.ksp.test

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import java.io.File

class BrokenIncrementalIT {
    @Rule
    @JvmField
    val project: TemporaryTestProject = TemporaryTestProject("broken-incremental")

    @Test
    fun `first build`() {
        // @AutoInclude module in upstream project.
        createKotlinFile(
            "lib/src/main/kotlin/LibModule.kt",
            """
            package lib
            
            import example.AutoInclude
            import dagger.Module
                
            @AutoInclude @Module class LibModule
            """
        )

        // @AutoInclude module in current compilation unit.
        createKotlinFile(
            "app/src/main/kotlin/AppModule.kt",
            """
            package app

            import example.AutoInclude
            import dagger.Module
                
            @AutoInclude @Module class AppModule
            """
        )

        // @MergedModule in current compilation unit.
        createKotlinFile(
            "app/src/main/kotlin/AppMergedModule.kt",
            """
            package app
            
            import example.MergedModule
            import dagger.Module
            
            @MergedModule
            abstract class AppMergedModule
            """
        )

        val generatedMergeableModule = kt(
            """
            package example.aggregation
            
            import app.AppModule
            import dagger.Module
            import example.Mergeable
            
            @Module(includes = [AppModule::class])
            @Mergeable
            public abstract class AutoIncludeModule_app_AppModule

            """
        )

        val generatedMergedModule = kt(
            """
            package app
            
            import dagger.Module
            import example.aggregation.AutoIncludeModule_app_AppModule
            import example.aggregation.AutoIncludeModule_lib_LibModule
            
            @Module(includes = [
              AutoIncludeModule_app_AppModule::class,
              AutoIncludeModule_lib_LibModule::class
            ])
            public abstract class MergedModule_AppMergedModule
    
            """
        )

        // This works fine.
        runBuild {
            assertThat(generatedKotlinFiles("app")).containsExactlyInAnyOrder(
                generatedMergeableModule,
                generatedMergedModule
            )
        }
    }

    @Test
    fun `rebuild after source file that produces a mergeable module added to current compilation unit`() {
        // First @AutoInclude module in current compilation unit project
        createKotlinFile(
            "app/src/main/kotlin/AppModule1.kt",
            """
            package app

            import example.AutoInclude
            import dagger.Module
                
            @AutoInclude @Module class AppModule1
            """
        )

        // @MergedModule in current compilation unit.
        createKotlinFile(
            "app/src/main/kotlin/AppMergedModule.kt",
            """
            package app
            
            import example.MergedModule
            import dagger.Module
            
            @MergedModule
            abstract class AppMergedModule
            """
        )

        runBuild()

        // Add another @AutoInclude module to the current compilation unit.
        createKotlinFile(
            "app/src/main/kotlin/AppModule2.kt",
            """
            package app

            import example.AutoInclude
            import dagger.Module
                
            @AutoInclude @Module class AppModule2
            """
        )

        val generatedMergeableModule1 = kt(
            """
            package example.aggregation
            
            import app.AppModule1
            import dagger.Module
            import example.Mergeable
            
            @Module(includes = [AppModule1::class])
            @Mergeable
            public abstract class AutoIncludeModule_app_AppModule1

            """
        )

        val generatedMergeableModule2 = kt(
            """
            package example.aggregation
            
            import app.AppModule2
            import dagger.Module
            import example.Mergeable
            
            @Module(includes = [AppModule2::class])
            @Mergeable
            public abstract class AutoIncludeModule_app_AppModule2

            """
        )

        val generatedMergedModule = kt(
            """
            package app
            
            import dagger.Module
            import example.aggregation.AutoIncludeModule_app_AppModule1
            import example.aggregation.AutoIncludeModule_app_AppModule2
            
            @Module(includes = [
              AutoIncludeModule_app_AppModule1::class,
              AutoIncludeModule_app_AppModule2::class
            ])
            public abstract class MergedModule_AppMergedModule
    
            """
        )

        // This will fail - only the new generated module (AutoIncludeModule_app_AppModule2) will be returned by
        // getDeclarationsFromPackage. It seems like sources generated by KSP in previous builds are not discoverable
        // through getDeclarationsFromPackage?
        runBuild {
            assertThat(generatedKotlinFiles("app")).containsExactlyInAnyOrder(
                generatedMergeableModule1,
                generatedMergeableModule2,
                generatedMergedModule
            )
        }
    }

    @Test
    fun `rebuild after new mergeable module added to classpath`() {
        // First @AutoInclude module in upstream project.
        createKotlinFile(
            "lib/src/main/kotlin/LibModule1.kt",
            """
            package lib
            
            import example.AutoInclude
            import dagger.Module
                
            @AutoInclude @Module class LibModule1
            """
        )

        // @MergedModule in current compilation unit.
        createKotlinFile(
            "app/src/main/kotlin/AppMergedModule.kt",
            """
            package app
            
            import example.MergedModule
            import dagger.Module
            
            @MergedModule
            abstract class AppMergedModule
            """
        )

        runBuild()

        // Add another @AutoInclude module to the upstream project.
        createKotlinFile(
            "lib/src/main/kotlin/LibModule2.kt",
            """
            package lib
            
            import example.AutoInclude
            import dagger.Module
                
            @AutoInclude @Module class LibModule2
            """
        )

        val generatedMergedModule = kt(
            """
            package app
            
            import dagger.Module
            import example.aggregation.AutoIncludeModule_lib_LibModule1
            import example.aggregation.AutoIncludeModule_lib_LibModule2
            
            @Module(includes = [
              AutoIncludeModule_lib_LibModule1::class,
              AutoIncludeModule_lib_LibModule2::class
            ])
            public abstract class MergedModule_AppMergedModule
    
            """
        )

        // This will also fail - if you add logs or attach a debugger you'll see that the `@MergedModule` isn't
        // reprocessed at all in the second build. I'm using associateWithClasses to associate all the symbols from
        // getDeclarationsFromPackage with the generated merged module, but I'm not sure if/how I can tell KSP that I
        // also care about _new_ files in the relevant package?
        runBuild {
            assertThat(generatedKotlinFiles("app")).containsExactlyInAnyOrder(
                generatedMergedModule
            )
        }
    }

    private fun createKotlinFile(relativePath: String, @Language("kotlin") contents: String) {
        File(project.root, relativePath).apply { parentFile.mkdirs() }.writeText(contents.trimIndent())
    }

    private fun runBuild(block: (BuildResult) -> Unit = {}): BuildResult {
        return GradleRunner.create()
            .withProjectDir(project.root)
            .withArguments("app:assemble")
            .forwardOutput()
            .build()
            .also(block)
    }

    @Suppress("SameParameterValue")
    private fun generatedKotlinFiles(module: String): List<String> {
        return project.root.resolve("$module/build/generated/ksp/main/kotlin")
            .walkTopDown()
            .filter { it.name.endsWith(".kt") }
            .map { it.readText() }
            .toList()
    }
}

private fun kt(@Language("kotlin") source: String) = source.trimIndent()
