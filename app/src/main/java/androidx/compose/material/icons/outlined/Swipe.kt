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

package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.Swipe: ImageVector
    get() {
        if (_swipe != null) {
            return _swipe!!
        }
        _swipe = materialIcon(name = "Outlined.Swipe") {
            materialPath {
                moveTo(20.5f, 2.0f)
                verticalLineToRelative(2.02f)
                curveTo(18.18f, 2.13f, 15.22f, 1.0f, 12.0f, 1.0f)
                reflectiveCurveTo(5.82f, 2.13f, 3.5f, 4.02f)
                verticalLineTo(2.0f)
                horizontalLineTo(2.0f)
                verticalLineToRelative(3.5f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(1.5f)
                horizontalLineTo(7.0f)
                verticalLineTo(5.5f)
                horizontalLineTo(4.09f)
                curveToRelative(2.11f, -1.86f, 4.88f, -3.0f, 7.91f, -3.0f)
                reflectiveCurveToRelative(5.79f, 1.14f, 7.91f, 3.0f)
                horizontalLineTo(17.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(3.5f)
                horizontalLineTo(22.0f)
                verticalLineTo(5.5f)
                verticalLineTo(2.0f)
                horizontalLineTo(20.5f)
                close()
            }
            materialPath {
                moveTo(18.89f, 13.77f)
                lineToRelative(-3.8f, -1.67f)
                curveTo(14.96f, 12.04f, 14.81f, 12.0f, 14.65f, 12.0f)
                horizontalLineTo(14.0f)
                lineToRelative(0.0f, -4.37f)
                curveToRelative(0.0f, -1.32f, -0.96f, -2.5f, -2.27f, -2.62f)
                curveTo(10.25f, 4.88f, 9.0f, 6.05f, 9.0f, 7.5f)
                verticalLineToRelative(8.15f)
                lineToRelative(-1.87f, -0.4f)
                curveToRelative(-0.19f, -0.03f, -1.02f, -0.15f, -1.73f, 0.56f)
                lineTo(4.0f, 17.22f)
                lineToRelative(5.12f, 5.19f)
                curveTo(9.49f, 22.79f, 10.0f, 23.0f, 10.53f, 23.0f)
                horizontalLineToRelative(6.55f)
                curveToRelative(0.98f, 0.0f, 1.81f, -0.7f, 1.97f, -1.67f)
                lineToRelative(0.92f, -5.44f)
                curveTo(20.12f, 15.03f, 19.68f, 14.17f, 18.89f, 13.77f)
                close()
                moveTo(18.0f, 15.56f)
                lineTo(17.08f, 21.0f)
                horizontalLineToRelative(-6.55f)
                lineToRelative(-3.7f, -3.78f)
                lineTo(11.0f, 18.11f)
                verticalLineTo(7.5f)
                curveTo(11.0f, 7.22f, 11.22f, 7.0f, 11.5f, 7.0f)
                reflectiveCurveTo(12.0f, 7.22f, 12.0f, 7.5f)
                verticalLineToRelative(6.18f)
                horizontalLineToRelative(1.76f)
                lineTo(18.0f, 15.56f)
                close()
            }
        }
        return _swipe!!
    }

private var _swipe: ImageVector? = null
