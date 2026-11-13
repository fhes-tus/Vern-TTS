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

public val Icons.Filled.AutoGraph: ImageVector
    get() {
        if (_autoGraph != null) {
            return _autoGraph!!
        }
        _autoGraph = materialIcon(name = "Filled.AutoGraph") {
            materialPath {
                moveTo(14.06f, 9.94f)
                lineTo(12.0f, 9.0f)
                lineToRelative(2.06f, -0.94f)
                lineTo(15.0f, 6.0f)
                lineToRelative(0.94f, 2.06f)
                lineTo(18.0f, 9.0f)
                lineToRelative(-2.06f, 0.94f)
                lineTo(15.0f, 12.0f)
                lineTo(14.06f, 9.94f)
                close()
                moveTo(4.0f, 14.0f)
                lineToRelative(0.94f, -2.06f)
                lineTo(7.0f, 11.0f)
                lineToRelative(-2.06f, -0.94f)
                lineTo(4.0f, 8.0f)
                lineToRelative(-0.94f, 2.06f)
                lineTo(1.0f, 11.0f)
                lineToRelative(2.06f, 0.94f)
                lineTo(4.0f, 14.0f)
                close()
                moveTo(8.5f, 9.0f)
                lineToRelative(1.09f, -2.41f)
                lineTo(12.0f, 5.5f)
                lineTo(9.59f, 4.41f)
                lineTo(8.5f, 2.0f)
                lineTo(7.41f, 4.41f)
                lineTo(5.0f, 5.5f)
                lineToRelative(2.41f, 1.09f)
                lineTo(8.5f, 9.0f)
                close()
                moveTo(4.5f, 20.5f)
                lineToRelative(6.0f, -6.01f)
                lineToRelative(4.0f, 4.0f)
                lineTo(23.0f, 8.93f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-7.09f, 7.97f)
                lineToRelative(-4.0f, -4.0f)
                lineTo(3.0f, 19.0f)
                lineTo(4.5f, 20.5f)
                close()
            }
        }
        return _autoGraph!!
    }

private var _autoGraph: ImageVector? = null
