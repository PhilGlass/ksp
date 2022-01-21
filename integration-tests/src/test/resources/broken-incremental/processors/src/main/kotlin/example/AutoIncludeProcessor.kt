@file:OptIn(KotlinPoetKspPreview::class)

package example

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
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
import example.AutoInclude
import example.Mergeable

class AutoIncludeProcessor(
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator
) : SymbolProcessor {
    private val nameAllocator = NameAllocator()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(AutoInclude::class.qualifiedName!!)
            .map { it as KSClassDeclaration }
            .forEach { it.generateMergeableModule() }

        return emptyList()
    }

    private fun KSClassDeclaration.generateMergeableModule() {
        val moduleAnnotation = AnnotationSpec.builder(Module::class)
            .addMember("includes = [%T::class]", toClassName())
            .build()

        val moduleName = nameAllocator.newName("AutoIncludeModule_${qualifiedName!!.asString()}")
        val mergeableModule = TypeSpec.classBuilder(moduleName)
            .addOriginatingKSFile(containingFile!!)
            .addAnnotation(moduleAnnotation)
            .addAnnotation(Mergeable::class)
            .addModifiers(KModifier.ABSTRACT)
            .build()

        FileSpec.builder("example.aggregation", moduleName)
            .addType(mergeableModule)
            .build()
            .writeTo(codeGenerator, aggregating = false)
    }
}

class AutoIncludeProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return AutoIncludeProcessor(environment.logger, environment.codeGenerator)
    }
}
