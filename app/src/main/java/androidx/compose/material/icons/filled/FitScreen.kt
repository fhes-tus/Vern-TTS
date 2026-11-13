/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Filled.FitScreen: ImageVector
    get() {
        if (_fitScreen != null) {
            return _fitScreen!!
        }
        _fitScreen = materialIcon(name = "Filled.FitScreen") {
            materialPath {
                moveTo(17.0f, 4.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, 0.9f, 2.0f, 2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                lineTo(20.0f, 6.0f)
                horizontalLineToRelative(-3.0f)
                lineTo(17.0f, 4.0f)
                close()
                moveTo(4.0f, 8.0f)
                lineTo(4.0f, 6.0f)
                horizontalLineToRelative(3.0f)
                lineTo(7.0f, 4.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                close()
                moveTo(20.0f, 16.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(7.0f, 18.0f)
                lineTo(4.0f, 18.0f)
                verticalLineToRelative(-2.0f)
                lineTo(2.0f, 16.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(18.0f, 8.0f)
                lineTo(6.0f, 8.0f)
                verticalLineToRelative(8.0f)
                horizontalLineToRelative(12.0f)
                lineTo(18.0f, 8.0f)
                close()
            }
        }
        return _fitScreen!!
    }

private var _fitScreen: ImageVector? = null
