package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.content.SharedPreferences
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.lib.preferences.PerformanceProfile
import com.swordfish.lemuroid.lib.preferences.PerformanceProfileManager
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

@Composable
fun rememberFrontend3DProfile(): PerformanceProfile {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { SharedPreferencesHelper.getSharedPreferences(context) }
    var profile by remember(context) { mutableStateOf(PerformanceProfileManager.getActiveProfile(context)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PerformanceProfileManager.PREF_KEY_PERFORMANCE_PROFILE) {
                profile = PerformanceProfileManager.getActiveProfile(context)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return profile
}

/** Captures this surface so disposing an earlier effect cannot pause its replacement. */
@Composable
fun Bind3DSurfaceLifecycle(view: GLSurfaceView?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view?.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            view?.onPause()
        }
    }
}

/** Requests frames only while resumed; rendering remains owned by the GLSurfaceView's GL thread. */
@Composable
fun Bind3DFrameClock(view: GLSurfaceView?, invalidationKey: Any? = Unit, animateInPerformance: Boolean = false) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val profile = rememberFrontend3DProfile()
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view, invalidationKey, profile, resumed, animateInPerformance) {
        val surface = view
        if (surface == null || !resumed) {
            onDispose { }
        } else {
            surface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            val choreographer = Choreographer.getInstance()
            val activeUntil = SystemClock.uptimeMillis() + 900L
            var lastFrameNanos = 0L
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    val fps = when (profile) {
                        PerformanceProfile.QUALITY -> 60
                        PerformanceProfile.BALANCED -> 30
                        PerformanceProfile.PERFORMANCE ->
                            if (animateInPerformance) 20 else if (SystemClock.uptimeMillis() < activeUntil) 30 else 4
                    }
                    // Native vsync does not invalidate Compose or redraw the Android view tree.
                    if (fps == 60 || frameTimeNanos - lastFrameNanos >= 1_000_000_000L / fps - 1_000_000L) {
                        surface.requestRender()
                        lastFrameNanos = frameTimeNanos
                    }
                    choreographer.postFrameCallback(this)
                }
            }
            surface.requestRender()
            choreographer.postFrameCallback(callback)
            onDispose { choreographer.removeFrameCallback(callback) }
        }
    }
}
