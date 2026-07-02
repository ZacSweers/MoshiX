// Copyright (C) 2026 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
@file:OptIn(org.jetbrains.kotlin.fir.symbols.SymbolInternals::class)

package dev.zacsweers.moshix.ir.compiler.fir

import dev.zacsweers.metro.compiler.compat.CompatContext
import dev.zacsweers.moshix.ir.compiler.MoshiDiagnostics
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.declarations.getSealedClassInheritors
import org.jetbrains.kotlin.fir.declarations.getStringArrayArgument
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.toAnnotationClass
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.declarations.utils.isJava
import org.jetbrains.kotlin.fir.declarations.utils.isSealed
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

internal class MoshiFirExtensionRegistrar(
  private val enableSealed: Boolean,
  private val compatContext: CompatContext,
) : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    +{ session: FirSession -> FirMoshiCheckers(session, enableSealed, compatContext) }
    registerDiagnosticContainers(MoshiDiagnostics)
  }
}

private class FirMoshiCheckers(
  session: FirSession,
  private val enableSealed: Boolean,
  private val compatContext: CompatContext,
) : FirAdditionalCheckersExtension(session) {
  override val declarationCheckers: DeclarationCheckers =
    object : DeclarationCheckers() {
      override val classCheckers: Set<FirClassChecker>
        get() = setOf(FirMoshiDeclarationChecker(enableSealed, compatContext))
    }
}

private class FirMoshiDeclarationChecker(
  private val enableSealed: Boolean,
  private val compatContext: CompatContext,
) : FirClassChecker(MppCheckerKind.Common), CompatContext by compatContext {

  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirClass) {
    if (declaration !is FirRegularClass) return
    val jsonClass = declaration.getAnnotationByClassId(JSON_CLASS, context.session) ?: return
    if (jsonClass.getBooleanArgumentCompat(GENERATE_ADAPTER, context.session) != true) return

    val generator = jsonClass.getStringArgumentCompat(GENERATOR, context.session).orEmpty()
    val labelKey = generator.labelKey()
    when {
      generator.isBlank() -> checkJsonClassTarget(declaration)
      enableSealed && labelKey != null -> checkSealedTarget(declaration, labelKey)
    }
  }

  context(context: CheckerContext, reporter: DiagnosticReporter)
  private fun checkJsonClassTarget(declaration: FirRegularClass) {
    if (declaration.isEnumClass) {
      declaration.report(
        "@JsonClass with 'generateAdapter = \"true\"' can't be applied to ${declaration.nameForMessage}: code gen for enums is not supported or necessary"
      )
      return
    }
    if (declaration.classKind != ClassKind.CLASS) {
      declaration.report(
        "@JsonClass can't be applied to ${declaration.nameForMessage}: must be a Kotlin class"
      )
      return
    }
    if (declaration.isInner) {
      declaration.report(
        "@JsonClass can't be applied to ${declaration.nameForMessage}: must not be an inner class"
      )
      return
    }
    if (declaration.isSealed) {
      declaration.report(
        "@JsonClass can't be applied to ${declaration.nameForMessage}: must not be sealed"
      )
      return
    }
    if (declaration.isAbstract) {
      declaration.report(
        "@JsonClass can't be applied to ${declaration.nameForMessage}: must not be abstract"
      )
      return
    }
    if (declaration.isLocal) {
      declaration.report(
        "@JsonClass can't be applied to ${declaration.nameForMessage}: must not be local"
      )
      return
    }
    if (!declaration.visibility.isPublicOrInternal) {
      declaration.report(
        "@JsonClass can't be applied to ${declaration.nameForMessage}: must be internal or public"
      )
      return
    }

    val constructor =
      declaration.primaryConstructorIfAny(context.session)
        ?: run {
          declaration.report("No primary constructor found on ${declaration.nameForMessage}")
          return
        }
    if (!constructor.visibility.isPublicOrInternal) {
      constructor.report(
        "@JsonClass can't be applied to ${declaration.nameForMessage}: primary constructor is not internal or public"
      )
      return
    }

    val constructorParameters =
      constructor.valueParameterSymbols.associateByTo(LinkedHashMap()) { it.name.asString() }
    val properties = LinkedHashMap<String, FirTargetProperty>()
    for (appliedClass in declaration.appliedClasses(context.session)) {
      if (appliedClass !== declaration && appliedClass.isJava) {
        declaration.report(
          "@JsonClass can't be applied to ${declaration.nameForMessage}: supertype ${appliedClass.nameForMessage} is not a Kotlin type"
        )
        return
      }
      for (property in
        appliedClass.declaredProperties(
          context.session,
          constructorParameters,
          skipPrivateLibraryProperties =
            appliedClass !== declaration && appliedClass.origin == FirDeclarationOrigin.Library,
        )) {
        properties.putIfAbsent(property.name, property)
      }
    }

    for (property in properties.values) {
      if (property.jsonIgnore) {
        if (!property.hasDefault) {
          declaration.report("No default value for transient/ignored property ${property.name}")
          return
        }
        continue
      }
      if (!property.visibility.isVisibleProperty) {
        declaration.report("property ${property.name} is not visible")
        return
      }
      if (!property.isSettable) continue

      val qualifiers =
        property.parameter?.jsonQualifiers(context.session).orEmpty() +
          property.symbol.jsonQualifiers(context.session)
      for (qualifier in qualifiers) {
        val qualifierClass = qualifier.toAnnotationClass(context.session) ?: continue
        val retention =
          qualifierClass
            .getAnnotationByClassId(StandardClassIds.Annotations.Retention, context.session)
            ?.extractRetentionName() ?: continue
        if (retention != "RUNTIME") {
          reporter.reportOn(
            qualifier.source ?: declaration.source,
            MoshiDiagnostics.MOSHI_ERROR,
            "JsonQualifier @${qualifierClass.symbol.classId.shortClassName.asString()} must have RUNTIME retention",
          )
          return
        }
      }
    }

    for ((name, parameter) in constructorParameters) {
      if (properties[name] == null && !parameter.hasDefaultValue) {
        declaration.report("No property for required constructor parameter $name")
        return
      }
    }
  }

  context(context: CheckerContext, reporter: DiagnosticReporter)
  private fun checkSealedTarget(declaration: FirRegularClass, labelKey: String) {
    if (declaration.classKind != ClassKind.CLASS && declaration.classKind != ClassKind.INTERFACE) {
      declaration.report(
        "@JsonClass can't be applied to ${declaration.nameForMessage}: must be a Kotlin class"
      )
      return
    }
    if (!declaration.isSealed) {
      declaration.report("Must be a sealed class!")
      return
    }

    if (declaration.hasAnnotation(NESTED_SEALED, context.session)) {
      val parentLabelKey =
        declaration.superTypeRefs.firstNotNullOfOrNull { superTypeRef ->
          superTypeRef.coneTypeOrNull
            ?.fullyExpandedType()
            ?.toRegularClassSymbol(context.session)
            ?.getAnnotationByClassId(JSON_CLASS, context.session)
            ?.labelKey(context.session, checkGenerateAdapter = false)
        }
      if (parentLabelKey == null) {
        declaration.report(
          "No JsonClass-annotated sealed supertype found for ${declaration.nameForMessage}"
        )
        return
      }
      if (parentLabelKey == labelKey) {
        declaration.report(
          "@NestedSealed-annotated subtype ${declaration.nameForMessage} is inappropriately annotated with @JsonClass(generator = \"sealed:$labelKey\")."
        )
        return
      }
    }

    val useDefaultNull = declaration.hasAnnotation(DEFAULT_NULL, context.session)
    val fallbackAdapterAnnotation =
      declaration.getAnnotationByClassId(FALLBACK_JSON_ADAPTER, context.session)
    if (useDefaultNull && fallbackAdapterAnnotation != null) {
      declaration.report("Only one of @DefaultNull or @FallbackJsonAdapter can be used at a time")
      return
    }
    if (fallbackAdapterAnnotation != null) {
      if (checkFallbackAdapter(declaration, fallbackAdapterAnnotation)) return
    }

    val seenLabels = LinkedHashMap<String, FirRegularClass>()
    val state = SealedState()
    for (subtype in declaration.sealedSubclasses(context.session)) {
      if (
        subtype.classKind == ClassKind.OBJECT &&
          subtype.hasAnnotation(DEFAULT_OBJECT, context.session)
      ) {
        state.hasDefaultObject = true
        if (useDefaultNull) {
          subtype.report(
            """
              Cannot have both @DefaultNull and @DefaultObject. @DefaultObject type: ${declaration.nameForMessage}
              Cannot have both @DefaultNull and @DefaultObject. @DefaultNull type: ${subtype.nameForMessage}
            """
              .trimIndent()
          )
          return
        }
      } else {
        walkTypeLabels(
          root = declaration,
          subtype = subtype,
          labelKey = labelKey,
          seenLabels = seenLabels,
          state = state,
        )
      }
    }

    if (state.hasDefaultObject && fallbackAdapterAnnotation != null) {
      declaration.report(
        "Only one of @DefaultObject, @DefaultNull, or @FallbackJsonAdapter can be used at a time."
      )
    }
  }

  context(context: CheckerContext, reporter: DiagnosticReporter)
  private fun checkFallbackAdapter(
    declaration: FirRegularClass,
    fallbackAdapterAnnotation: FirAnnotation,
  ): Boolean {
    val adapterClass =
      fallbackAdapterAnnotation
        .getKClassArgument(VALUE)
        ?.fullyExpandedType()
        ?.toRegularClassSymbol(context.session)
        ?.fir ?: return false
    val constructor =
      adapterClass.primaryConstructorIfAny(context.session)
        ?: run {
          adapterClass.report(
            "No primary constructor found for fallback adapter ${adapterClass.nameForMessage}"
          )
          return true
        }
    if (!constructor.visibility.isPublicOrInternal) {
      declaration.report(
        "Visibility must be one of public or internal. Is ${constructor.visibility.name}"
      )
      return true
    }
    when (constructor.valueParameterSymbols.size) {
      0 -> return false
      1 -> {
        val parameterType = constructor.valueParameterSymbols.single().resolvedReturnType
        if (parameterType.classId != MOSHI) {
          declaration.report(
            "Fallback adapter type's primary constructor can only have a Moshi parameter"
          )
          return true
        }
      }
      else -> {
        declaration.report(
          "Fallback adapter type's primary constructor can only have a Moshi parameter"
        )
        return true
      }
    }
    return false
  }

  context(context: CheckerContext, reporter: DiagnosticReporter)
  private fun walkTypeLabels(
    root: FirRegularClass,
    subtype: FirRegularClass,
    labelKey: String,
    seenLabels: MutableMap<String, FirRegularClass>,
    state: SealedState,
  ) {
    if (subtype.isSealed) {
      val nestedLabelKey =
        subtype
          .getAnnotationByClassId(JSON_CLASS, context.session)
          ?.labelKey(context.session, checkGenerateAdapter = false)
      if (nestedLabelKey == labelKey) {
        subtype.report(
          "Sealed subtype ${subtype.nameForMessage} is redundantly annotated with @JsonClass(generator = \"sealed:$nestedLabelKey\")."
        )
        return
      }

      if (subtype.hasAnnotation(TYPE_LABEL, context.session)) {
        addLabelKeyForType(root, subtype, seenLabels, state, skipJsonClassCheck = true)
      } else {
        for (nestedSubtype in subtype.sealedSubclasses(context.session)) {
          walkTypeLabels(root, nestedSubtype, labelKey, seenLabels, state)
        }
      }
      return
    }

    addLabelKeyForType(root, subtype, seenLabels, state)
  }

  context(context: CheckerContext, reporter: DiagnosticReporter)
  private fun addLabelKeyForType(
    root: FirRegularClass,
    subtype: FirRegularClass,
    seenLabels: MutableMap<String, FirRegularClass>,
    state: SealedState,
    skipJsonClassCheck: Boolean = false,
  ) {
    val labelAnnotation =
      subtype.getAnnotationByClassId(TYPE_LABEL, context.session)
        ?: run {
          subtype.report("Missing @TypeLabel")
          return
        }

    if (subtype.symbol.typeParameterSymbols.isNotEmpty()) {
      subtype.report("Moshi-sealed subtypes cannot be generic.")
      return
    }

    val mainLabel =
      labelAnnotation.getStringArgumentCompat(LABEL, context.session)
        ?: run {
          subtype.report("No label member for TypeLabel annotation!")
          return
        }

    seenLabels.put(mainLabel, subtype)?.let { previous ->
      if (previous != subtype) {
        root.report(
          "Duplicate label '$mainLabel' defined for ${subtype.nameForMessage} and ${previous.nameForMessage}."
        )
      }
      return
    }

    if (!skipJsonClassCheck) {
      val subtypeLabelKey =
        subtype.getAnnotationByClassId(JSON_CLASS, context.session)?.labelKey(context.session)
      if (subtypeLabelKey != null) {
        subtype.report(
          "Sealed subtype ${subtype.nameForMessage} is annotated with @JsonClass(generator = \"sealed:$subtypeLabelKey\") and @TypeLabel."
        )
        return
      }
    }

    for (alternate in labelAnnotation.getStringArrayArgument(ALTERNATE_LABELS).orEmpty()) {
      seenLabels.put(alternate, subtype)?.let { previous ->
        root.report(
          "Duplicate alternate label '$alternate' defined for ${subtype.nameForMessage} and ${previous.nameForMessage}."
        )
        return
      }
    }

    if (
      subtype.classKind == ClassKind.OBJECT &&
        subtype.hasAnnotation(DEFAULT_OBJECT, context.session)
    ) {
      state.hasDefaultObject = true
    }
  }

  context(context: CheckerContext, reporter: DiagnosticReporter)
  private fun FirBasedSymbol<*>.report(message: String) {
    reporter.reportOn(source, MoshiDiagnostics.MOSHI_ERROR, message)
  }

  context(context: CheckerContext, reporter: DiagnosticReporter)
  private fun FirDeclaration.report(message: String) {
    reporter.reportOn(source, MoshiDiagnostics.MOSHI_ERROR, message)
  }
}

private class SealedState {
  var hasDefaultObject: Boolean = false
}

private data class FirTargetProperty(
  val symbol: FirPropertySymbol,
  val parameter: FirValueParameterSymbol?,
  val jsonIgnore: Boolean,
) {
  val name: String
    get() = symbol.name.asString()

  val hasDefault: Boolean
    get() = parameter?.hasDefaultValue ?: true

  val isSettable: Boolean
    get() = symbol.isVar || parameter != null

  val visibility: Visibility
    get() = symbol.resolvedStatus.visibility
}

context(compatContext: CompatContext)
private fun FirRegularClass.declaredProperties(
  session: FirSession,
  constructorParameters: Map<String, FirValueParameterSymbol>,
  skipPrivateLibraryProperties: Boolean,
): List<FirTargetProperty> {
  val properties = mutableListOf<FirTargetProperty>()
  processAllDeclarations(session) { symbol ->
    if (symbol is FirPropertySymbol) {
      // JVM transient is a field flag, not a metadata annotation, for compiled dependencies.
      if (
        skipPrivateLibraryProperties && symbol.resolvedStatus.visibility == Visibilities.Private
      ) {
        return@processAllDeclarations
      }
      val parameter = constructorParameters[symbol.name.asString()]
      properties +=
        FirTargetProperty(
          symbol = symbol,
          parameter = parameter,
          jsonIgnore =
            symbol.isTransient(session) ||
              parameter?.jsonIgnore(session) == true ||
              symbol.jsonIgnoreFromAnywhere(session),
        )
    }
  }
  return properties
}

private fun FirRegularClass.appliedClasses(session: FirSession): LinkedHashSet<FirRegularClass> {
  val result = LinkedHashSet<FirRegularClass>()
  fun visit(type: FirRegularClass) {
    if (!result.add(type)) return
    for (superTypeRef in type.superTypeRefs) {
      val superType = superTypeRef.coneTypeOrNull ?: continue
      val symbol =
        (superType.fullyExpandedType(session) as? ConeClassLikeType)?.toRegularClassSymbol(session)
          ?: continue
      if (symbol.classKind != ClassKind.CLASS) continue
      if (symbol.classId == StandardClassIds.Any) continue
      visit(symbol.fir)
    }
  }
  visit(this)
  return result
}

private fun FirRegularClass.sealedSubclasses(session: FirSession): List<FirRegularClass> {
  return getSealedClassInheritors(session).mapNotNull { classId ->
    classId.toLookupTag().toRegularClassSymbol(session)?.fir
  }
}

context(compatContext: CompatContext)
private fun FirAnnotation.labelKey(
  session: FirSession,
  checkGenerateAdapter: Boolean = true,
): String? {
  if (
    checkGenerateAdapter &&
      with(compatContext) {
        this@labelKey.getBooleanArgumentCompat(GENERATE_ADAPTER, session)
      } != true
  ) {
    return null
  }
  return with(compatContext) {
      this@labelKey.getStringArgumentCompat(GENERATOR, session)
    }
    .orEmpty()
    .labelKey()
}

private fun String.labelKey(): String? {
  return takeIf { it.startsWith("sealed:") }?.removePrefix("sealed:")
}

private fun FirAnnotation.extractRetentionName(): String? {
  return findArgumentByName(StandardClassIds.Annotations.ParameterNames.retentionValue)
    ?.extractEnumValueArgumentInfo()
    ?.enumEntryName
    ?.asString()
}

private val FirRegularClass.nameForMessage: String
  get() = symbol.classId.asFqNameString()

private val Visibility.isPublicOrInternal: Boolean
  get() = this == Visibilities.Public || this == Visibilities.Internal

private val Visibility.isVisibleProperty: Boolean
  get() = isPublicOrInternal || this == Visibilities.Protected

private fun FirValueParameterSymbol.jsonQualifiers(session: FirSession): List<FirAnnotation> {
  return resolvedAnnotationsWithClassIds.filter { it.isJsonQualifier(session) }
}

private fun FirPropertySymbol.jsonQualifiers(session: FirSession): List<FirAnnotation> {
  return buildList {
    addAll(resolvedAnnotationsWithClassIds.filter { it.isJsonQualifier(session) })
    backingFieldSymbol?.resolvedAnnotationsWithClassIds?.filterTo(this) {
      it.isJsonQualifier(session)
    }
    getterSymbol?.jsonQualifiersTo(this, session)
    setterSymbol?.jsonQualifiersTo(this, session)
  }
}

private fun FirPropertyAccessorSymbol.jsonQualifiersTo(
  result: MutableList<FirAnnotation>,
  session: FirSession,
) {
  resolvedAnnotationsWithClassIds.filterTo(result) { it.isJsonQualifier(session) }
}

private fun FirAnnotation.isJsonQualifier(session: FirSession): Boolean {
  return toAnnotationClass(session)?.hasAnnotation(JSON_QUALIFIER, session) == true
}

context(compatContext: CompatContext)
private fun FirValueParameterSymbol.jsonIgnore(session: FirSession): Boolean {
  return resolvedAnnotationsWithClassIds
    .getAnnotationByClassId(JSON, session)
    ?.jsonIgnore(session) == true
}

private fun FirPropertySymbol.isTransient(session: FirSession): Boolean {
  return hasAnnotation(TRANSIENT, session) ||
    backingFieldSymbol?.hasAnnotation(TRANSIENT, session) == true ||
    hasAnnotation(StandardClassIds.Annotations.Transient, session) ||
    backingFieldSymbol?.hasAnnotation(StandardClassIds.Annotations.Transient, session) == true
}

context(compatContext: CompatContext)
private fun FirPropertySymbol.jsonIgnoreFromAnywhere(session: FirSession): Boolean {
  return resolvedAnnotationsWithClassIds
    .getAnnotationByClassId(JSON, session)
    ?.jsonIgnore(session) == true ||
    backingFieldSymbol
      ?.resolvedAnnotationsWithClassIds
      ?.getAnnotationByClassId(JSON, session)
      ?.jsonIgnore(session) == true ||
    getterSymbol
      ?.resolvedAnnotationsWithClassIds
      ?.getAnnotationByClassId(JSON, session)
      ?.jsonIgnore(session) == true ||
    setterSymbol
      ?.resolvedAnnotationsWithClassIds
      ?.getAnnotationByClassId(JSON, session)
      ?.jsonIgnore(session) == true
}

context(compatContext: CompatContext)
private fun FirAnnotation.jsonIgnore(session: FirSession): Boolean {
  return with(compatContext) { this@jsonIgnore.getBooleanArgumentCompat(IGNORE, session) } == true
}

private val JSON = ClassId.topLevel(FqName("com.squareup.moshi.Json"))
private val JSON_CLASS = ClassId.topLevel(FqName("com.squareup.moshi.JsonClass"))
private val JSON_QUALIFIER = ClassId.topLevel(FqName("com.squareup.moshi.JsonQualifier"))
private val TRANSIENT = ClassId.topLevel(FqName("kotlin.jvm.Transient"))
private val MOSHI = ClassId.topLevel(FqName("com.squareup.moshi.Moshi"))
private val DEFAULT_NULL =
  ClassId.topLevel(FqName("dev.zacsweers.moshix.sealed.annotations.DefaultNull"))
private val DEFAULT_OBJECT =
  ClassId.topLevel(FqName("dev.zacsweers.moshix.sealed.annotations.DefaultObject"))
private val FALLBACK_JSON_ADAPTER =
  ClassId.topLevel(FqName("dev.zacsweers.moshix.sealed.annotations.FallbackJsonAdapter"))
private val NESTED_SEALED =
  ClassId.topLevel(FqName("dev.zacsweers.moshix.sealed.annotations.NestedSealed"))
private val TYPE_LABEL =
  ClassId.topLevel(FqName("dev.zacsweers.moshix.sealed.annotations.TypeLabel"))

private val GENERATE_ADAPTER = Name.identifier("generateAdapter")
private val GENERATOR = Name.identifier("generator")
private val IGNORE = Name.identifier("ignore")
private val VALUE = Name.identifier("value")
private val LABEL = Name.identifier("label")
private val ALTERNATE_LABELS = Name.identifier("alternateLabels")
