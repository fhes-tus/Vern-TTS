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

public val Icons.Outlined.Start: ImageVector
    get() {
        if (_start != null) {
            return _start!!
        }
        _start = materialIcon(name = "Outlined.Start") {
            materialPath {
                moveTo(14.59f, 7.41f)
                lineTo(18.17f, 11.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(12.17f)
                lineToRelative(-3.59f, 3.59f)
                lineTo(16.0f, 18.0f)
                lineToRelative(6.0f, -6.0f)
                lineToRelative(-6.0f, -6.0f)
                lineTo(14.59f, 7.41f)
                close()
                moveTo(2.0f, 6.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(6.0f)
                horizontalLineTo(2.0f)
                close()
            }
        }
        return _start!!
    }

private var _start: ImageVector? = null
