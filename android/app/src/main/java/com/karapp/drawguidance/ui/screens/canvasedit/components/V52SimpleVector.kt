package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool

/**
 * v52 basit resimde vektör düzenleme ayarları (gelişmiş moddaki Düzenle satırının karşılığı; basit menüde o satır yok):
 * Noktalar / Çizgiler / Alan boya araçları ve "Ayrıntılı noktalar" (kapalıyken parça başına az nokta). Seçili aracın
 * kendi ayarları ([content]: nokta alt araçları, çizgi eğimi, boya rengi) bunun altında gösterilir.
 */
object SimpleVectorTools {
    val tools: List<ShapeTool> = listOf(ShapeTool.NODES, ShapeTool.LINES, ShapeTool.FILL)

    /** Basit modda bu araçlarla düzenlenen şekil mi (grafik ve değerli şekiller kendi ayarlarını gösterir). */
    fun applies(tool: ShapeTool): Boolean = tool.nodeFamily || tool == ShapeTool.LINES || tool == ShapeTool.FILL

    /** Araç düğmesi seçili mi (nokta alt araçları Noktalar düğmesini seçili gösterir). */
    fun selected(button: ShapeTool, current: ShapeTool): Boolean =
        if (button == ShapeTool.NODES) current.nodeFamily else button == current
}

@Composable
fun SimpleVectorControls(
    tool: ShapeTool,
    onTool: (ShapeTool) -> Unit,
    fineNodes: Boolean,
    onFineNodes: (Boolean) -> Unit,
    icon: (ShapeTool) -> androidx.compose.ui.graphics.vector.ImageVector,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = NeuTheme.colors
    Column(Modifier.fillMaxWidth().testTag("simple_vector_controls"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SimpleVectorTools.tools.forEach { t ->
                val label = shapeToolLabel(t)
                NeuChip(
                    label, SimpleVectorTools.selected(t, tool), { if (!SimpleVectorTools.selected(t, tool)) onTool(t) },
                    Modifier.semantics { contentDescription = label }.testTag("simple_vector_" + t.name.lowercase()),
                    icon = icon(t),
                )
            }
            val fineLabel = stringResource(R.string.v52_vector_fine_nodes)
            Row(
                Modifier.padding(start = 4.dp).semantics { contentDescription = fineLabel },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(fineLabel, color = colors.contentMuted, fontSize = 13.sp)
                NeuSwitch(fineNodes, onFineNodes, Modifier.testTag("simple_vector_fine"))
            }
        }
        content?.invoke()
    }
}
