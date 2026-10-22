package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders

/**
 * Inspection helpers shared by the design contract and the five tile contracts.
 *
 * A tile is a builder tree, not a view: the only way to assert anything is to
 * walk it. These helpers walk *both* trees — layout elements and the arc
 * contents hanging off an `Arc` — because a walk that forgets the arcs silently
 * counts zero gauge animators and the budget assertion stops testing anything.
 */
object TileTestKit {

    fun childrenOf(element: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.LayoutElement> =
        when (element) {
            is LayoutElementBuilders.Box -> element.contents
            is LayoutElementBuilders.Row -> element.contents
            is LayoutElementBuilders.Column -> element.contents
            else -> emptyList()
        }

    fun flatten(root: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.LayoutElement> {
        val out = mutableListOf<LayoutElementBuilders.LayoutElement>()
        fun visit(element: LayoutElementBuilders.LayoutElement) {
            out += element
            childrenOf(element).forEach(::visit)
        }
        visit(root)
        return out
    }

    fun texts(root: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.Text> =
        flatten(root).filterIsInstance<LayoutElementBuilders.Text>()

    fun textsMatching(root: LayoutElementBuilders.LayoutElement, needle: String): List<LayoutElementBuilders.Text> =
        texts(root).filter { it.text?.value?.contains(needle) == true }

    fun images(root: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.Image> =
        flatten(root).filterIsInstance<LayoutElementBuilders.Image>()

    fun arcs(root: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.Arc> =
        flatten(root).filterIsInstance<LayoutElementBuilders.Arc>()

    fun arcLines(root: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.ArcLine> =
        arcs(root).flatMap { it.contents }.filterIsInstance<LayoutElementBuilders.ArcLine>()

    fun boxes(root: LayoutElementBuilders.LayoutElement): List<LayoutElementBuilders.Box> =
        flatten(root).filterIsInstance<LayoutElementBuilders.Box>()

    /** Readable dp out of a container dimension; wrap and expand contribute 0. */
    fun dpOf(dimension: Any?): Float = (dimension as? DimensionBuilders.DpProp)?.value ?: 0f

    /**
     * The animation spec only exists on the internal animatable wrapper, so it is
     * read reflectively. Nothing in the public protocol exposes the animator
     * budget, and the budget is what breaks tiles when it is exceeded.
     */
    fun specOf(dynamic: Any?): AnimationParameterBuilders.AnimationSpec? {
        if (dynamic == null) return null
        return runCatching {
            val method = dynamic.javaClass.getMethod("getAnimationSpec")
            method.isAccessible = true
            method.invoke(dynamic) as AnimationParameterBuilders.AnimationSpec
        }.getOrNull()
    }

    fun durationMs(spec: AnimationParameterBuilders.AnimationSpec?): Long =
        spec?.animationParameters?.durationMillis ?: -1L

    fun loopsForever(spec: AnimationParameterBuilders.AnimationSpec?): Boolean =
        runCatching { spec?.repeatable?.hasInfiniteIteration() ?: false }.getOrDefault(false)

    private fun modifierDynamics(modifiers: ModifiersBuilders.Modifiers?): List<Any?> = listOf(
        modifiers?.opacity?.dynamicValue,
        modifiers?.transformation?.rotation?.dynamicValue,
        modifiers?.transformation?.scaleX?.dynamicValue,
        modifiers?.transformation?.scaleY?.dynamicValue,
        modifiers?.transformation?.translationX?.dynamicValue,
        modifiers?.transformation?.translationY?.dynamicValue
    )

    private fun arcDynamics(line: LayoutElementBuilders.ArcLine): List<Any?> = listOf(
        line.length?.dynamicValue,
        line.thickness?.dynamicValue,
        line.color?.dynamicValue,
        line.modifiers?.opacity?.dynamicValue
    )

    /** Every animator the renderer would have to run for this tile. */
    fun animators(root: LayoutElementBuilders.LayoutElement): List<AnimationParameterBuilders.AnimationSpec> {
        val specs = mutableListOf<AnimationParameterBuilders.AnimationSpec>()

        flatten(root).forEach { element ->
            val modifiers = when (element) {
                is LayoutElementBuilders.Box -> element.modifiers
                is LayoutElementBuilders.Row -> element.modifiers
                is LayoutElementBuilders.Column -> element.modifiers
                is LayoutElementBuilders.Text -> element.modifiers
                is LayoutElementBuilders.Image -> element.modifiers
                is LayoutElementBuilders.Arc -> element.modifiers
                else -> null
            }
            modifierDynamics(modifiers).forEach { specOf(it)?.let(specs::add) }

            if (element is LayoutElementBuilders.Arc) {
                element.contents.filterIsInstance<LayoutElementBuilders.ArcLine>().forEach { line ->
                    arcDynamics(line).forEach { specOf(it)?.let(specs::add) }
                }
            }
        }
        return specs
    }

    fun infiniteAnimators(root: LayoutElementBuilders.LayoutElement): List<AnimationParameterBuilders.AnimationSpec> =
        animators(root).filter(::loopsForever)

    /**
     * Conservative width estimate for a text run, so a fit check can never
     * flatter the tile: 0.62 em average advance, the style's own tracking, and
     * 10 % slop on top.
     */
    fun estimatedWidthDp(text: LayoutElementBuilders.Text): Float {
        val size = text.fontStyle?.size?.value ?: 0f
        val chars = (text.text?.value ?: "").length
        val tracking = (text.fontStyle?.letterSpacing?.value ?: 0f) * size * chars
        return (chars * size * 0.62f + tracking) * 1.1f
    }

    fun isUppercase(value: String): Boolean = value == value.uppercase()
}
