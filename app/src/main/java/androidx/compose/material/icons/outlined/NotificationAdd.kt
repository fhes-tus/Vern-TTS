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

public val Icons.Outlined.NotificationAdd: ImageVector
    get() {
        if (_notificationAdd != null) {
            return _notificationAdd!!
        }
        _notificationAdd = materialIcon(name = "Outlined.NotificationAdd") {
            materialPath {
                moveTo(16.0f, 14.0f)
                verticalLineToRelative(3.0f)
                horizontalLineTo(8.0f)
                verticalLineToRelative(-7.0f)
                curveToRelative(0.0f, -2.21f, 1.79f, -4.0f, 4.0f, -4.0f)
                curveToRelative(0.85f, 0.0f, 1.64f, 0.26f, 2.28f, 0.72f)
                lineToRelative(1.43f, -1.43f)
                curveToRelative(-0.64f, -0.51f, -1.39f, -0.88f, -2.21f, -1.09f)
                verticalLineTo(3.5f)
                curveTo(13.5f, 2.67f, 12.83f, 2.0f, 12.0f, 2.0f)
                reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
                verticalLineToRelative(0.7f)
                curveTo(7.91f, 4.86f, 6.0f, 7.21f, 6.0f, 10.0f)
                verticalLineToRelative(7.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineTo(16.0f)
                close()
                moveTo(12.0f, 22.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                horizontalLineToRelative(-4.0f)
                curveTo(10.0f, 21.1f, 10.9f, 22.0f, 12.0f, 22.0f)
                close()
                moveTo(24.0f, 8.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineToRelative(3.0f)
                verticalLineTo(8.0f)
                close()
            }
        }
        return _notificationAdd!!
    }

private var _notificationAdd: ImageVector? = null
