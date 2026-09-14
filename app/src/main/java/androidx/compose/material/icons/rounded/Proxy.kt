package androidx.compose.material.icons.rounded

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path

/** 代理：客户端 → 中转节点 → 服务器。 */
val Icons.Rounded.Proxy: ImageVector
    get() = proxyIcon

private val proxyIcon: ImageVector by lazy {
    materialIcon(name = "Rounded.Proxy") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 左侧客户端节点
            moveTo(5.5f, 9.5f)
            curveTo(6.88f, 9.5f, 8f, 10.62f, 8f, 12f)
            curveTo(8f, 13.38f, 6.88f, 14.5f, 5.5f, 14.5f)
            curveTo(4.12f, 14.5f, 3f, 13.38f, 3f, 12f)
            curveTo(3f, 10.62f, 4.12f, 9.5f, 5.5f, 9.5f)
            close()
            // 右侧服务器节点
            moveTo(18.5f, 9.5f)
            curveTo(19.88f, 9.5f, 21f, 10.62f, 21f, 12f)
            curveTo(21f, 13.38f, 19.88f, 14.5f, 18.5f, 14.5f)
            curveTo(17.12f, 14.5f, 16f, 13.38f, 16f, 12f)
            curveTo(16f, 10.62f, 17.12f, 9.5f, 18.5f, 9.5f)
            close()
            // 中间的中转节点
            moveTo(10f, 9.5f)
            horizontalLineTo(14f)
            curveTo(14.28f, 9.5f, 14.5f, 9.72f, 14.5f, 10f)
            verticalLineTo(14f)
            curveTo(14.5f, 14.28f, 14.28f, 14.5f, 14f, 14.5f)
            horizontalLineTo(10f)
            curveTo(9.72f, 14.5f, 9.5f, 14.28f, 9.5f, 14f)
            verticalLineTo(10f)
            curveTo(9.5f, 9.72f, 9.72f, 9.5f, 10f, 9.5f)
            close()
            // 两段连线
            moveTo(8f, 12f)
            horizontalLineTo(9.5f)
            moveTo(14.5f, 12f)
            horizontalLineTo(16f)
        }
    }
}
