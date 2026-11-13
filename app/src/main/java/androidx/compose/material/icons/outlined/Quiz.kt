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

public val Icons.Outlined.Quiz: ImageVector
    get() {
        if (_quiz != null) {
            return _quiz!!
        }
        _quiz = materialIcon(name = "Outlined.Quiz") {
            materialPath {
                moveTo(4.0f, 6.0f)
                horizontalLineTo(2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(6.0f)
                close()
                moveTo(20.0f, 2.0f)
                horizontalLineTo(8.0f)
                curveTo(6.9f, 2.0f, 6.0f, 2.9f, 6.0f, 4.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(4.0f)
                curveTo(22.0f, 2.9f, 21.1f, 2.0f, 20.0f, 2.0f)
                close()
                moveTo(20.0f, 16.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(12.0f)
                verticalLineTo(16.0f)
                close()
                moveTo(13.51f, 10.16f)
                curveToRelative(0.41f, -0.73f, 1.18f, -1.16f, 1.63f, -1.8f)
                curveToRelative(0.48f, -0.68f, 0.21f, -1.94f, -1.14f, -1.94f)
                curveToRelative(-0.88f, 0.0f, -1.32f, 0.67f, -1.5f, 1.23f)
                lineToRelative(-1.37f, -0.57f)
                curveTo(11.51f, 5.96f, 12.52f, 5.0f, 13.99f, 5.0f)
                curveToRelative(1.23f, 0.0f, 2.08f, 0.56f, 2.51f, 1.26f)
                curveToRelative(0.37f, 0.6f, 0.58f, 1.73f, 0.01f, 2.57f)
                curveToRelative(-0.63f, 0.93f, -1.23f, 1.21f, -1.56f, 1.81f)
                curveToRelative(-0.13f, 0.24f, -0.18f, 0.4f, -0.18f, 1.18f)
                horizontalLineToRelative(-1.52f)
                curveTo(13.26f, 11.41f, 13.19f, 10.74f, 13.51f, 10.16f)
                close()
                moveTo(12.95f, 13.95f)
                curveToRelative(0.0f, -0.59f, 0.47f, -1.04f, 1.05f, -1.04f)
                curveToRelative(0.59f, 0.0f, 1.04f, 0.45f, 1.04f, 1.04f)
                curveToRelative(0.0f, 0.58f, -0.44f, 1.05f, -1.04f, 1.05f)
                curveTo(13.42f, 15.0f, 12.95f, 14.53f, 12.95f, 13.95f)
                close()
            }
        }
        return _quiz!!
    }

private var _quiz: ImageVector? = null
