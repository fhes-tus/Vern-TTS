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

public val Icons.Filled.EditCalendar: ImageVector
    get() {
        if (_editCalendar != null) {
            return _editCalendar!!
        }
        _editCalendar = materialIcon(name = "Filled.EditCalendar") {
            materialPath {
                moveTo(12.0f, 22.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-1.11f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                lineTo(3.01f, 6.0f)
                curveToRelative(0.0f, -1.1f, 0.88f, -2.0f, 1.99f, -2.0f)
                horizontalLineToRelative(1.0f)
                verticalLineTo(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineTo(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(1.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, 0.9f, 2.0f, 2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(10.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(22.0f)
                close()
                moveTo(22.13f, 16.99f)
                lineToRelative(0.71f, -0.71f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineToRelative(-0.71f, -0.71f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-0.71f, 0.71f)
                lineTo(22.13f, 16.99f)
                close()
                moveTo(21.42f, 17.7f)
                lineToRelative(-5.3f, 5.3f)
                horizontalLineTo(14.0f)
                verticalLineToRelative(-2.12f)
                lineToRelative(5.3f, -5.3f)
                lineTo(21.42f, 17.7f)
                close()
            }
        }
        return _editCalendar!!
    }

private var _editCalendar: ImageVector? = null
