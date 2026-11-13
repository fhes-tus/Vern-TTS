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

public val Icons.Filled.StrikethroughS: ImageVector
    get() {
        if (_strikethroughS != null) {
            return _strikethroughS!!
        }
        _strikethroughS = materialIcon(name = "Filled.StrikethroughS") {
            materialPath {
                moveTo(6.85f, 7.08f)
                curveTo(6.85f, 4.37f, 9.45f, 3.0f, 12.24f, 3.0f)
                curveToRelative(1.64f, 0.0f, 3.0f, 0.49f, 3.9f, 1.28f)
                curveToRelative(0.77f, 0.65f, 1.46f, 1.73f, 1.46f, 3.24f)
                horizontalLineToRelative(-3.01f)
                curveToRelative(0.0f, -0.31f, -0.05f, -0.59f, -0.15f, -0.85f)
                curveToRelative(-0.29f, -0.86f, -1.2f, -1.28f, -2.25f, -1.28f)
                curveToRelative(-1.86f, 0.0f, -2.34f, 1.02f, -2.34f, 1.7f)
                curveToRelative(0.0f, 0.48f, 0.25f, 0.88f, 0.74f, 1.21f)
                curveTo(10.97f, 8.55f, 11.36f, 8.78f, 12.0f, 9.0f)
                horizontalLineTo(7.39f)
                curveTo(7.18f, 8.66f, 6.85f, 8.11f, 6.85f, 7.08f)
                close()
                moveTo(21.0f, 12.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(9.62f)
                curveToRelative(1.15f, 0.45f, 1.96f, 0.75f, 1.96f, 1.97f)
                curveToRelative(0.0f, 1.0f, -0.81f, 1.67f, -2.28f, 1.67f)
                curveToRelative(-1.54f, 0.0f, -2.93f, -0.54f, -2.93f, -2.51f)
                horizontalLineTo(6.4f)
                curveToRelative(0.0f, 0.55f, 0.08f, 1.13f, 0.24f, 1.58f)
                curveToRelative(0.81f, 2.29f, 3.29f, 3.3f, 5.67f, 3.3f)
                curveToRelative(2.27f, 0.0f, 5.3f, -0.89f, 5.3f, -4.05f)
                curveToRelative(0.0f, -0.3f, -0.01f, -1.16f, -0.48f, -1.94f)
                horizontalLineTo(21.0f)
                verticalLineTo(12.0f)
                close()
            }
        }
        return _strikethroughS!!
    }

private var _strikethroughS: ImageVector? = null
