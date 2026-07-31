package kz.maestrosultan.fitjournal.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS entry point for the P1 infra proof: wraps [HelloCompose] in a
 * ComposeUIViewController the Swift side can host inside a native VC.
 * Swift call site: `HelloComposeControllerKt.HelloComposeController()`.
 */
fun HelloComposeController(): UIViewController = ComposeUIViewController { HelloCompose() }
