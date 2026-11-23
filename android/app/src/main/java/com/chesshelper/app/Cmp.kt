package com.chesshelper.app

import java.util.Comparator

/**
 * Class-based comparators.
 *
 * The stock `sortedBy`/`sortedByDescending` helpers route through
 * `kotlin.comparisons.ComparisonsKt__ComparisonsKt`, whose factories compile to
 * invokedynamic call sites. Building comparators here (plain classes) keeps the
 * whole dex free of invoke-custom/method-handle items, which older ART builds
 * mis-handle when the dex is produced by dx.
 */
object Cmp {
    fun <T, R : Comparable<R>> by(sel: (T) -> R): Comparator<T> = object : Comparator<T> {
        override fun compare(a: T, b: T): Int = sel(a).compareTo(sel(b))
    }

    fun <T, R : Comparable<R>> byDesc(sel: (T) -> R): Comparator<T> = object : Comparator<T> {
        override fun compare(a: T, b: T): Int = sel(b).compareTo(sel(a))
    }
}
