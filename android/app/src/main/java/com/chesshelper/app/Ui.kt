package com.chesshelper.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Small programmatic-UI helpers shared by all screens (dark theme, iOS-style). */

fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

object Ui {

    // Palette (matches colors.xml).
    const val GREEN = 0xFF00C853.toInt()
    const val YELLOW = 0xFFFFEB3B.toInt()
    const val ORANGE = 0xFFFF9100.toInt()
    const val RED = 0xFFF44336.toInt()
    const val CYAN = 0xFF00E5FF.toInt()
    const val SURFACE = 0xFF1A1A1A.toInt()
    const val SURFACE_ALT = 0xFF252525.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val TEXT_DIM = 0xFFB0B0B0.toInt()

    fun roundedBg(color: Int, radiusDp: Float, strokeColor: Int = 0, strokeWidthDp: Int = 0, ctx: Context? = null): GradientDrawable {
        val d = GradientDrawable()
        d.setShape(GradientDrawable.RECTANGLE)
        d.setColor(color)
        d.setCornerRadius(radiusDp)
        if (strokeColor != 0 && ctx != null) {
            d.setStroke(ctx.dp(strokeWidthDp), strokeColor)
        }
        return d
    }

    fun circleBg(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setShape(GradientDrawable.OVAL)
        d.setColor(color)
        return d
    }

    fun text(ctx: Context, value: CharSequence, sizeSp: Float = 13f, color: Int = TEXT,
             bold: Boolean = false, mono: Boolean = false, maxLines: Int = Int.MAX_VALUE): TextView {
        val t = TextView(ctx)
        t.text = value
        t.textSize = sizeSp
        t.setTextColor(color)
        if (bold || mono) {
            val style = when {
                bold && mono -> Typeface.BOLD
                bold -> Typeface.BOLD
                else -> Typeface.NORMAL
            }
            t.setTypeface(if (mono) Typeface.MONOSPACE else Typeface.DEFAULT, style)
        }
        t.maxLines = maxLines
        return t
    }

    fun sectionTitle(ctx: Context, value: CharSequence): TextView {
        val t = text(ctx, value, 16f, GREEN, bold = true)
        t.setPadding(0, ctx.dp(16), 0, ctx.dp(8))
        return t
    }

    /** A pill/chip button used for filters and color overrides. */
    fun chip(ctx: Context, label: CharSequence, active: Boolean, onClick: () -> Unit): TextView {
        val t = text(ctx, label, 12f, if (active) GREEN else TEXT, bold = active)
        val bg = roundedBg(
            if (active) 0x5900C853 else 0x1AFFFFFF,
            16f
        )
        t.background = bg
        t.setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
        t.setGravity(Gravity.CENTER)
        t.setOnClickListener { onClick() }
        return t
    }

    fun row(ctx: Context, vararg children: View): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.HORIZONTAL
        l.setGravity(Gravity.CENTER_VERTICAL)
        for (c in children) l.addView(c)
        return l
    }

    fun column(ctx: Context, vararg children: View): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        for (c in children) l.addView(c)
        return l
    }

    /** Weighted horizontal row: [left] expands, [right] hugs. */
    fun rowSpread(ctx: Context, left: View, right: View): LinearLayout {
        val l = row(ctx)
        l.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        l.addView(right)
        return l
    }

    fun spacer(ctx: Context): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        return v
    }

    fun vSpace(ctx: Context, dp: Int): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ctx.dp(dp))
        return v
    }

    fun divider(ctx: Context): View {
        val v = View(ctx)
        v.setBackgroundColor(0x1FFFFFFF)
        v.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ctx.dp(1)
        )
        return v
    }
}
