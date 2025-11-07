//Taken fom https://github.com/kotest/kotest/blob/3f8ee175e12c45eb8a92c32c580e445f40a87864/kotest-common/src/commonMain/kotlin/io/kotest/common/reflection/reflection.kt

package io.kotest.common.reflection

import kotlin.collections.emptyList
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmName


object JvmReflection : Reflection {

    private val fqns = mutableMapOf<KClass<*>, String?>()
    private val annotations = mutableMapOf<Pair<KClass<*>, Set<AnnotationSearchParameter>>, List<Annotation>>()

    override fun fqn(kclass: KClass<*>): String? = fqns.getOrPut(kclass) { kclass.qualifiedName }

    override fun annotations(kclass: KClass<*>, parameters: Set<AnnotationSearchParameter>): List<Annotation> {
        return annotations.getOrPut(kclass to parameters) {
            val includeSuperclasses = parameters.contains(IncludingSuperclasses)
            val includeAnnotations = parameters.contains(IncludingAnnotations)
            annotations(kclass, includeSuperclasses, includeAnnotations)
        }
    }

    private fun annotations(
        kclass: KClass<*>,
        includeSuperclasses: Boolean,
        includeAnnotations: Boolean
    ): List<Annotation> {
        val classes = listOf(kclass) + if (includeSuperclasses) supers(kclass) else emptyList()
        return if (includeAnnotations) {
            classes.flatMap(::composedAnnotations)
        } else {
            classes.flatMap { it.annotationsSafe() }
        }
    }

    private fun supers(kclass: KClass<*>): Iterable<KClass<*>> {
        return kclass.superclasses + kclass.superclasses.flatMap { supers(it) }
    }

    private fun composedAnnotations(kclass: KClass<*>, checked: Set<String> = emptySet()): List<Annotation> {
        val annos = kclass.annotationsSafe()
        return annos + annos.flatMap {
            // we don't want to get into a loop with annotations that annotate themselves
            if (checked.contains(it.annotationClass.jvmName)) emptyList() else {
                composedAnnotations(it.annotationClass, checked + it.annotationClass.jvmName)
            }
        }
    }

    private fun KClass<*>.annotationsSafe(): List<Annotation> = try {
        this.annotations
    } catch (_: Exception) {
        emptyList()
    }

    override fun <T : Any> isDataClass(kclass: KClass<T>): Boolean = try {
        kclass.isData
    } catch (_: Throwable) {
        false
    }

    override fun <T : Any> isEnumClass(kclass: KClass<T>): Boolean = kclass.isSubclassOf(Enum::class)

    override fun <T : Any> primaryConstructorMembers(klass: KClass<T>): List<Property> {
        // gets the parameters for the primary constructor and then associates them with the member callable
        val constructorParams = klass::primaryConstructor.get()?.parameters ?: emptyList()
        val membersByName = getPropertiesByName(klass)
        return constructorParams.mapNotNull { param ->
            membersByName[param.name]?.let { callable ->
                Property(callable.name, param.type) {
                    callable.isAccessible = true
                    callable.call(it)
                }
            }
        }
    }

    internal fun <T : Any> getPropertiesByName(klass: KClass<T>) = klass::members.get()
        .filterIsInstance<KProperty<*>>()
        .associateBy(KCallable<*>::name)
}
@Deprecated("Don't use this")
val reflection: Reflection = JvmReflection

/**
 * Groups together some basic platform agnostic reflection operations.
 */
interface Reflection {

    /**
     * Returns the fully qualified name for the given class or null if the platform
     * does not expose this information.
     */
    fun fqn(kclass: KClass<*>): String?

    /**
     * Returns the annotations on this class or empty list if not supported.
     *
     * @param parameters options of search.
     */
    fun annotations(kclass: KClass<*>, parameters: Set<AnnotationSearchParameter>): List<Annotation>

    /**
     * Returns true if this class is a data class or false if it is not, or the platform does not
     * expose this information.
     */
    fun <T : Any> isDataClass(kclass: KClass<T>): Boolean

    /**
     * Returns a list of the class member properties defined in the primary constructor, if supported on
     * the platform, otherwise returns an empty list.
     */
    fun <T : Any> primaryConstructorMembers(klass: KClass<T>): List<Property>

    fun <T : Any> isEnumClass(kclass: KClass<T>): Boolean
}

/**
 * Parameter that using for annotation search.
 */
sealed interface AnnotationSearchParameter

/**
 * Search should also include composed annotations.
 */
data object IncludingAnnotations : AnnotationSearchParameter

/**
 * Search should include full type hierarchy.
 *
 * If used with [IncludingAnnotations] also include composed annotations of superclasses.
 */
data object IncludingSuperclasses : AnnotationSearchParameter

/**
 * Returns the longest possible name available for this class.
 * That is, in order, the FQN, the simple name, or toString.
 */
fun KClass<*>.bestName(): String = reflection.fqn(this) ?: simpleName ?: this.toString()

/**
 * Finds the first annotation of type T on this class, or returns null if annotations
 * are not supported on this platform or the annotation is missing.
 *
 * This method by default will recursively included composed annotations.
 */
@Deprecated("Don't use this")
inline fun <reified T : Any> KClass<*>.annotation(
    vararg parameters: AnnotationSearchParameter = arrayOf(IncludingAnnotations)
): T? {
    return reflection.annotations(this, parameters.toSet()).filterIsInstance<T>().firstOrNull()
}

/**
 * Returns true if this class has the given annotation.
 *
 * This method by default will recursively check for annotations by looking for annotations on annotations.
 */
@Deprecated("Don't use this")
inline fun <reified T : Any> KClass<*>.hasAnnotation(
    vararg parameters: AnnotationSearchParameter = arrayOf(IncludingAnnotations)
): Boolean {
    return this.annotation<T>(*parameters) != null
}

data class Property(val name: String, val type: KType, val call: (Any) -> Any?)