@file:OptIn(KotlinPoetKspPreview::class, KspExperimental::class)

package example

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import example.AutoInclude
import example.Mergeable
import example.MergedModule
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.Module

private val producesNewMergeableModule = listOf(
    AutoInclude::class
).map { it.qualifiedName!! }

class MergedModuleProcessor(
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator
) : SymbolProcessor {
    private val nameAllocator = NameAllocator()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedWithMergedModule = resolver.getSymbolsWithAnnotation(MergedModule::class.qualifiedName!!)
            .map { it as KSClassDeclaration }
            .toList()

        if (annotatedWithMergedModule.isEmpty()) {
            return emptyList()
        }

        val roundWillProduceNewMergeableModules = producesNewMergeableModule.any { annotation ->
            resolver.getSymbolsWithAnnotation(annotation).firstOrNull() != null
        }

        if (roundWillProduceNewMergeableModules) {
            return annotatedWithMergedModule.toList()
        }

        val mergeableModules = resolver.getDeclarationsFromPackage("example.aggregation")
            .filter { it.isAnnotationPresent(Mergeable::class) }
            .map { it as KSClassDeclaration }
            .sortedBy { it.qualifiedName!!.asString() }
            .toList()

        annotatedWithMergedModule.forEach { it.generateMergedModule(mergeableModules) }

        return emptyList()
    }

    private fun KSClassDeclaration.generateMergedModule(mergeableModules: List<KSClassDeclaration>) {
        val moduleAnnotation = AnnotationSpec.builder(Module::class)
            .addArrayMember("includes", mergeableModules)
            .build()

        val moduleName = nameAllocator.newName("MergedModule_${simpleName.asString()}")
        val mergedModule = TypeSpec.classBuilder(moduleName)
            .addOriginatingKSFile(containingFile!!)
            .addAnnotation(moduleAnnotation)
            .addModifiers(KModifier.ABSTRACT)
            .build()

        FileSpec.builder(packageName.asString(), moduleName)
            .addType(mergedModule)
            .build()
            .writeTo(codeGenerator, aggregating = true)

        codeGenerator.associateWithClasses(mergeableModules, packageName.asString(), moduleName)
    }
}

private fun AnnotationSpec.Builder.addArrayMember(name: String, classes: List<KSClassDeclaration>) = apply {
    val pattern = List(classes.size) { "%T::class" }.joinToString(separator = ",\n")
    addMember("$name = [\n⇥$pattern⇤\n]", *classes.map { it.toClassName() }.toTypedArray())
}

private fun TypeSpec.Builder.addOriginatingKSFiles(files: List<KSFile>) = apply {
    files.forEach { addOriginatingKSFile(it) }
}

class MergedModuleProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return MergedModuleProcessor(environment.logger, environment.codeGenerator)
    }
}
