// Vendored from AndroidX Material Icons 1.7.8; original Apache-2.0 notice below.
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

package androidx.compose.material.icons.rounded

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Rounded.Download: ImageVector
    get() {
        if (_download != null) {
            return _download!!
        }
        _download = materialIcon(name = "Rounded.Download") {
            materialPath {
                moveTo(19.0f, 9.0f)
                lineTo(15.0f, 9.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(9.0f)
                verticalLineTo(9.0f)
                lineTo(5.0f, 9.0f)
                lineTo(12.0f, 16.0f)
                lineTo(19.0f, 9.0f)
                close()
                moveTo(5.0f, 18.0f)
                verticalLineTo(20.0f)
                horizontalLineTo(19.0f)
                verticalLineTo(18.0f)
                close()
            }
        }
        return _download!!
    }

private var _download: ImageVector? = null
