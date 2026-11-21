// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/ui/content/m3/DropDownMenuWidget.kt
// MindLink adaptations (2026-11-14): retain original GPL-3.0 InstallerX Revived expressive option dropdown used by WeKit image badge; omit unrelated BaseWidget
// Original component geometry/typography/icons are retained; host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

// Copyright (C) 2023-2026 iamr0s, InstallerX Revived contributors
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

data class DropdownOption<T>(val value: T, val label: String)

@Composable
fun <T> ExpressiveOptionDropdown(
    expanded: Boolean,
    value: T,
    options: List<DropdownOption<T>>,
    onDismissRequest: () -> Unit,
    onValueChange: (T) -> Unit,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
            options.forEachIndexed { index, option ->
                SelectableDropdownMenuItem(
                    selected = option.value == value,
                    onClick = { onValueChange(option.value) },
                    text = { Text(option.label) },
                    shapes = MenuDefaults.itemShape(index, options.size),
                )
            }
        }
    }
}
