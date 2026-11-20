package com.swordfish.lemuroid.app.shared.gamepreferences

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.preferences.GamePreferencesManager
import com.swordfish.lemuroid.lib.preferences.PerformanceProfile
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

class SystemProfilesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = GamePreferencesManager(SharedPreferencesHelper.getSharedPreferences(this))
        val names = arrayOf("Usar configuração geral / automática", "Qualidade", "Equilibrado", "Desempenho")
        val profiles = listOf(null, PerformanceProfile.QUALITY, PerformanceProfile.BALANCED, PerformanceProfile.PERFORMANCE)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        GameSystem.all().forEach { system ->
            column.addView(Button(this).apply {
                isAllCaps = false
                fun refresh() { text = "${getString(system.titleResId)}: ${names[profiles.indexOf(preferences.getSystemProfileOverride(system.id))]}" }
                refresh()
                setOnClickListener {
                    val dialog = AlertDialog.Builder(this@SystemProfilesActivity).setTitle(getString(system.titleResId))
                        .setSingleChoiceItems(names, profiles.indexOf(preferences.getSystemProfileOverride(system.id))) { dialog, which ->
                            if (preferences.setSystemProfileOverride(system.id, profiles[which])) { refresh(); dialog.dismiss() }
                        }.setNegativeButton("Cancelar", null).create()
                    dialog.setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                            if (event.action == KeyEvent.ACTION_UP) dialog.dismiss()
                            true
                        } else false
                    }
                    dialog.show()
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(ScrollView(this).apply { addView(column) })
        column.getChildAt(0)?.requestFocus()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) { finish(); return true }
        return super.onKeyUp(keyCode, event)
    }
}
