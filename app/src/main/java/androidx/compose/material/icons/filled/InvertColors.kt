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

public val Icons.Filled.InvertColors: ImageVector
    get() {
        if (_invertColors != null) {
            return _invertColors!!
        }
        _invertColors = materialIcon(name = "Filled.InvertColors") {
            materialPath {
                moveTo(12.0f, 4.81f)
                lineTo(12.0f, 19.0f)
                curveToRelative(-3.31f, 0.0f, -6.0f, -2.63f, -6.0f, -5.87f)
                curveToRelative(0.0f, -1.56f, 0.62f, -3.03f, 1.75f, -4.14f)
                lineTo(12.0f, 4.81f)
                moveTo(6.35f, 7.56f)
                lineTo(6.35f, 7.56f)
                curveTo(4.9f, 8.99f, 4.0f, 10.96f, 4.0f, 13.13f)
                curveTo(4.0f, 17.48f, 7.58f, 21.0f, 12.0f, 21.0f)
                curveToRelative(4.42f, 0.0f, 8.0f, -3.52f, 8.0f, -7.87f)
                curveToRelative(0.0f, -2.17f, -0.9f, -4.14f, -2.35f, -5.57f)
                lineToRelative(0.0f, 0.0f)
                lineTo(12.0f, 2.0f)
                lineTo(6.35f, 7.56f)
                close()
            }
        }
        return _invertColors!!
    }

private var _invertColors: ImageVector? = null
