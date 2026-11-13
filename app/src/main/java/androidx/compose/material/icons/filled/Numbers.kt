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

public val Icons.Filled.Numbers: ImageVector
    get() {
        if (_numbers != null) {
            return _numbers!!
        }
        _numbers = materialIcon(name = "Filled.Numbers") {
            materialPath {
                moveTo(20.5f, 10.0f)
                lineTo(21.0f, 8.0f)
                horizontalLineToRelative(-4.0f)
                lineToRelative(1.0f, -4.0f)
                horizontalLineToRelative(-2.0f)
                lineToRelative(-1.0f, 4.0f)
                horizontalLineToRelative(-4.0f)
                lineToRelative(1.0f, -4.0f)
                horizontalLineToRelative(-2.0f)
                lineTo(9.0f, 8.0f)
                horizontalLineTo(5.0f)
                lineToRelative(-0.5f, 2.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(-1.0f, 4.0f)
                horizontalLineToRelative(-4.0f)
                lineTo(3.0f, 16.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(-1.0f, 4.0f)
                horizontalLineToRelative(2.0f)
                lineToRelative(1.0f, -4.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(-1.0f, 4.0f)
                horizontalLineToRelative(2.0f)
                lineToRelative(1.0f, -4.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(0.5f, -2.0f)
                horizontalLineToRelative(-4.0f)
                lineToRelative(1.0f, -4.0f)
                horizontalLineTo(20.5f)
                close()
                moveTo(13.5f, 14.0f)
                horizontalLineToRelative(-4.0f)
                lineToRelative(1.0f, -4.0f)
                horizontalLineToRelative(4.0f)
                lineTo(13.5f, 14.0f)
                close()
            }
        }
        return _numbers!!
    }

private var _numbers: ImageVector? = null
