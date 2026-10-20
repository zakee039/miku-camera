package com.example.mikucamera.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

/** Shared palette and compact controls for camera, editors, sheets and task UI. */
object MikuUi {
    val accent = Color.rgb(57, 197, 187)
    val ink = Color.rgb(27, 49, 46)
    val muted = Color.rgb(99, 119, 115)
    val background = Color.rgb(246, 249, 248)
    val surface = Color.WHITE
    val soft = Color.rgb(232, 242, 239)
    val border = Color.rgb(210, 228, 223)
    val accentInk = Color.rgb(20, 110, 103)
    val dangerBg = Color.rgb(253, 232, 232)
    val dangerInk = Color.rgb(198, 40, 40)

    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value).toInt()

    fun rounded(context: Context, color: Int = surface, radius: Int = 16) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, radius).toFloat()
    }

    fun bordered(
        context: Context,
        fillColor: Int = surface,
        strokeColor: Int = border,
        radius: Int = 12,
        strokeDp: Int = 1
    ) = GradientDrawable().apply {
        setColor(fillColor)
        cornerRadius = dp(context, radius).toFloat()
        setStroke(dp(context, strokeDp).coerceAtLeast(1), strokeColor)
    }

    fun toolbarIcon(view: View, active: Boolean = false) {
        view.backgroundTintList = null
        view.background = if (active) {
            bordered(view.context, Color.argb(55, 57, 197, 187), accent, radius = 14, strokeDp = 1)
        } else {
            rounded(view.context, soft, radius = 14)
        }
    }

    fun button(button: MaterialButton, selected: Boolean = false) {
        button.cornerRadius = dp(button.context, 10)
        button.isAllCaps = false
        button.textSize = 13f
        button.minimumWidth = 0
        button.minWidth = 0
        button.minimumHeight = dp(button.context, 42)
        button.insetTop = 0
        button.insetBottom = 0
        button.isSingleLine = true
        button.maxLines = 1
        button.backgroundTintList = ColorStateList.valueOf(if (selected) accent else soft)
        button.setTextColor(if (selected) ink else accentInk)
        button.strokeWidth = 0
        button.elevation = 0f
    }

    fun dangerButton(button: MaterialButton) {
        button.cornerRadius = dp(button.context, 10)
        button.isAllCaps = false
        button.textSize = 13f
        button.minimumWidth = 0
        button.minWidth = 0
        button.minimumHeight = dp(button.context, 40)
        button.insetTop = 0
        button.insetBottom = 0
        button.backgroundTintList = ColorStateList.valueOf(dangerBg)
        button.setTextColor(dangerInk)
        button.strokeWidth = 0
        button.elevation = 0f
    }

    fun switch(switch: SwitchMaterial) {
        switch.setTextColor(ink)
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        switch.isUseMaterialThemeColors = false
        switch.thumbTintList = ColorStateList(
            states,
            intArrayOf(Color.WHITE, muted)
        )
        switch.trackTintList = ColorStateList(
            states,
            intArrayOf(accent, border)
        )
    }

    fun seekBar(seekBar: SeekBar) {
        seekBar.thumbTintList = ColorStateList.valueOf(accent)
        seekBar.progressTintList = ColorStateList.valueOf(accent)
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(border)
    }

    fun editText(view: EditText, radius: Int = 10) {
        view.backgroundTintList = null
        view.setTextColor(ink)
        view.setHintTextColor(muted)
        view.textSize = 14f
        view.background = bordered(view.context, surface, border, radius)
        val padH = dp(view.context, 12)
        val padV = dp(view.context, 8)
        view.setPadding(padH, padV, padH, padV)
    }

    fun styleTree(view: View) {
        when (view) {
            is MaterialButton -> button(view)
            is SwitchMaterial -> switch(view)
            is EditText -> editText(view, 12)
            is SeekBar -> seekBar(view)
            is TextView -> view.setTextColor(ink)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                styleTree(view.getChildAt(i))
            }
        }
    }
}
