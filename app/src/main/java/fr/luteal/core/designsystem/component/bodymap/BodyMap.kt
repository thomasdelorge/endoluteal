package fr.luteal.core.designsystem.component.bodymap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.PainScale
import fr.luteal.core.designsystem.component.painIntensityColor
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.BodySide
import fr.luteal.core.model.BodyZone
import fr.luteal.core.model.PainMark
import fr.luteal.core.model.PainType
import java.util.UUID

@Composable
fun BodyMap(
    heat: Map<BodyZone, Int>,
    modifier: Modifier = Modifier
) {
    var side by remember { mutableStateOf(BodySide.FRONT) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        SideToggle(side = side, onSide = { side = it })
        ZoneCanvas(
            side = side,
            zoneHeat = heat.mapKeys { it.key.id },
            pins = emptyList(),
            interaction = null,
            onZoneSelected = {},
            onPinEdit = {},
            onPinDraft = {}
        )
        InternalZoneRow(heat = heat.mapKeys { it.key.id }, onZone = {})
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyMap(
    marks: List<PainMark>,
    onMarksChange: (List<PainMark>) -> Unit,
    modifier: Modifier = Modifier,
    interaction: BodyMapInteraction = BodyMapInteraction.Pins
) {
    var side by remember { mutableStateOf(BodySide.FRONT) }
    var listMode by remember { mutableStateOf(false) }
    var editingZone by remember { mutableStateOf<String?>(null) }
    var editingPin by remember { mutableStateOf<PainMark?>(null) }
    val zoneHeat = remember(marks) { marks.zoneHeat() }
    val pinMode = interaction == BodyMapInteraction.Pins
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        if (!pinMode && listMode) {
            ZoneList(heat = zoneHeat, onZone = { editingZone = it })
        } else {
            SideToggle(side = side, onSide = { side = it })
            ZoneCanvas(
                side = side,
                zoneHeat = if (pinMode) null else zoneHeat,
                pins = if (pinMode) marks else emptyList(),
                interaction = interaction,
                onZoneSelected = { editingZone = it },
                onPinEdit = { editingPin = it },
                onPinDraft = { editingPin = it }
            )
            if (pinMode) {
                Text(
                    text = stringResource(R.string.body_map_pin_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            InternalZoneRow(
                heat = internalHeat(marks),
                onZone = { zone ->
                    if (pinMode) {
                        editingPin = marks.firstOrNull { it.zone == zone.id && !it.isPin }
                            ?: PainMark(
                                id = UUID.randomUUID().toString(),
                                zone = zone.id,
                                intensity = 0,
                                painTypes = emptyList()
                            )
                    } else {
                        editingZone = zone.id
                    }
                }
            )
            if (pinMode) {
                Text(
                    text = stringResource(R.string.choir_bodymap_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!pinMode) {
            TextButton(onClick = { listMode = !listMode }) {
                Text(
                    text = stringResource(
                        if (listMode) R.string.body_map_silhouette_mode else R.string.body_map_list_mode
                    )
                )
            }
        }
    }
    editingZone?.let { zone ->
        val existing = marks.zoneMark(zone)
        PainMarkSheet(
            zoneId = zone,
            existing = existing,
            isDraft = false,
            onDismiss = { editingZone = null },
            onSave = { mark ->
                val withoutZone = marks.filterNot { it.zone == zone && !it.isPin }
                onMarksChange(withoutZone + mark.withoutPin())
                editingZone = null
            },
            onRemove = {
                onMarksChange(marks.filterNot { it.zone == zone && !it.isPin })
                editingZone = null
            }
        )
    }
    editingPin?.let { draft ->
        val existing = marks.firstOrNull { it.id == draft.id }
        PainMarkSheet(
            zoneId = draft.zone,
            existing = existing ?: draft,
            isDraft = marks.none { it.id == draft.id },
            onDismiss = { editingPin = null },
            onSave = { mark ->
                val saved = mark.copy(
                    pinSide = draft.pinSide,
                    pinX = draft.pinX,
                    pinY = draft.pinY
                )
                onMarksChange(marks.filterNot { it.id == saved.id } + saved)
                editingPin = null
            },
            onRemove = {
                onMarksChange(marks.filterNot { it.id == draft.id })
                editingPin = null
            }
        )
    }
}

@Composable
private fun SideToggle(side: BodySide, onSide: (BodySide) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        FilterChip(
            selected = side == BodySide.FRONT,
            onClick = { onSide(BodySide.FRONT) },
            label = { Text(stringResource(R.string.body_side_front)) }
        )
        FilterChip(
            selected = side == BodySide.BACK,
            onClick = { onSide(BodySide.BACK) },
            label = { Text(stringResource(R.string.body_side_back)) }
        )
    }
}

@Composable
private fun ZoneCanvas(
    side: BodySide,
    zoneHeat: Map<String, Int>?,
    pins: List<PainMark>,
    interaction: BodyMapInteraction?,
    onZoneSelected: (String) -> Unit,
    onPinEdit: (PainMark) -> Unit,
    onPinDraft: (PainMark) -> Unit
) {
    val front = side == BodySide.FRONT
    val context = androidx.compose.ui.platform.LocalContext.current
    val hitRadiusPx = with(LocalDensity.current) {
        PainPinGeometry.PIN_EDIT_HIT_RADIUS_DP.dp.toPx()
    }
    val bitmap = remember {
        val source = android.graphics.BitmapFactory.decodeResource(
            context.resources,
            R.drawable.painmap_female
        )
        lineArt(source).asImageBitmap()
    }
    val regions = remember(context) { ChoirPainMap.regions(context) }
    val visible = remember(front, regions) { regions.filter { it.front == front } }
    val ink = MaterialTheme.colorScheme.onSurface
    val halfWidth = bitmap.width / 2
    val sideKey = PainPinGeometry.sideKey(side)
    val pinsOnSide = remember(pins, sideKey) { pins.filter { it.pinSide == sideKey && it.isPin } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(halfWidth.toFloat() / bitmap.height.toFloat())
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (interaction == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(front, pinsOnSide, interaction) {
                            detectTapGestures { offset ->
                                val canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                                when (interaction) {
                                    BodyMapInteraction.Pins -> {
                                        val nearest = PainPinGeometry.nearestPin(
                                            pins,
                                            side,
                                            offset,
                                            canvasSize,
                                            hitRadiusPx
                                        )
                                        if (nearest != null) {
                                            onPinEdit(nearest)
                                        } else {
                                            val srcLeft = if (front) 0f else halfWidth.toFloat()
                                            val imageX = offset.x / canvasSize.width * halfWidth + srcLeft
                                            val imageY = offset.y / canvasSize.height * bitmap.height
                                            val region = ChoirPainMap.hit(imageX, imageY, front, regions)
                                                ?: return@detectTapGestures
                                            val (pinX, pinY) = PainPinGeometry.normalizedPin(offset, canvasSize)
                                            onPinDraft(
                                                PainMark(
                                                    id = UUID.randomUUID().toString(),
                                                    zone = region.key,
                                                    intensity = 0,
                                                    painTypes = emptyList(),
                                                    pinSide = PainPinGeometry.sideKey(side),
                                                    pinX = pinX,
                                                    pinY = pinY
                                                )
                                            )
                                        }
                                    }
                                    BodyMapInteraction.Zones -> {
                                        val srcLeft = if (front) 0f else halfWidth.toFloat()
                                        val imageX = offset.x / canvasSize.width * halfWidth + srcLeft
                                        val imageY = offset.y / canvasSize.height * bitmap.height
                                        ChoirPainMap.hit(imageX, imageY, front, regions)?.key
                                            ?.let(onZoneSelected)
                                    }
                                }
                            }
                        }
                    }
                )
        ) {
            val canvasSize = Size(size.width, size.height)
            val srcLeft = if (front) 0 else halfWidth
            drawImage(
                image = bitmap,
                srcOffset = androidx.compose.ui.unit.IntOffset(srcLeft, 0),
                srcSize = androidx.compose.ui.unit.IntSize(halfWidth, bitmap.height),
                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                colorFilter = ColorFilter.tint(ink)
            )
            val scaleX = size.width / halfWidth
            val scaleY = size.height / bitmap.height
            zoneHeat?.let { heat ->
                visible.forEach { region ->
                    val intensity = heat[region.key] ?: return@forEach
                    val path = androidx.compose.ui.graphics.Path().apply {
                        region.points.forEachIndexed { index, point ->
                            val x = (point.x - srcLeft) * scaleX
                            val y = point.y * scaleY
                            if (index == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(path, painIntensityColor(intensity).copy(alpha = 0.45f))
                }
            }
            val pinRadius = 7.dp.toPx()
            pinsOnSide.forEach { mark ->
                val center = PainPinGeometry.canvasOffset(mark, canvasSize) ?: return@forEach
                drawCircle(
                    color = painIntensityColor(mark.intensity),
                    radius = pinRadius,
                    center = center
                )
                drawCircle(
                    color = ink,
                    radius = pinRadius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InternalZoneRow(
    heat: Map<String, Int>,
    onZone: (BodyZone) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        BodyZone.entries.filter { it.side == BodySide.INTERNAL }.forEach { zone ->
            val label = zoneLabel(zone, heat[zone.id])
            FilterChip(
                selected = heat.containsKey(zone.id),
                onClick = { onZone(zone) },
                label = { Text(label) },
                modifier = Modifier.semantics { contentDescription = label }
            )
        }
    }
}

@Composable
private fun ZoneList(
    heat: Map<String, Int>,
    onZone: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        listOf(true to R.string.body_side_front, false to R.string.body_side_back).forEach { (front, title) ->
            Text(text = stringResource(title), style = MaterialTheme.typography.titleSmall)
            ChoirPainMap.regions(androidx.compose.ui.platform.LocalContext.current)
                .filter { it.front == front }
                .forEach { region ->
                    val intensity = heat[region.key]
                    val label = if (intensity == null) region.title else "${region.title} ($intensity)"
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 48.dp)
                            .semantics { contentDescription = label }
                            .clickable { onZone(region.key) }
                            .padding(vertical = LutealSpacing.xs)
                    )
                }
        }
        BodyZone.entries.filter { it.side == BodySide.INTERNAL }.forEach { zone ->
            val label = zoneLabel(zone, heat[zone.id])
            Text(
                text = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .clickable { onZone(zone.id) }
                    .padding(vertical = LutealSpacing.xs)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PainMarkSheet(
    zoneId: String,
    existing: PainMark?,
    isDraft: Boolean,
    onDismiss: () -> Unit,
    onSave: (PainMark) -> Unit,
    onRemove: () -> Unit
) {
    var intensity by remember(zoneId, existing?.id) {
        mutableStateOf(if (isDraft) null else existing?.intensity)
    }
    var types by remember(zoneId, existing?.id) {
        mutableStateOf(existing?.painTypes?.mapNotNull(PainType::fromId)?.toSet().orEmpty())
    }
    var confirmRemove by remember { mutableStateOf(false) }
    val title = BodyZone.fromId(zoneId)?.let { stringResource(it.labelRes) }
        ?: ChoirPainMap.regions(androidx.compose.ui.platform.LocalContext.current)
            .firstOrNull { it.key == zoneId }?.title
        ?: zoneId
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.body_map_remove_confirm_title)) },
            text = { Text(stringResource(R.string.body_map_remove_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) {
                    Text(stringResource(R.string.body_map_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.intake_cancel))
                }
            }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                PainScale(value = intensity, onValueChange = { intensity = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    PainType.entries.forEach { type ->
                        FilterChip(
                            selected = type in types,
                            onClick = {
                                types = if (type in types) types - type else types + type
                            },
                            label = { Text(stringResource(type.labelRes)) }
                        )
                    }
                }
                val canRemove = existing != null && !isDraft
                Button(
                    onClick = {
                        val level = intensity ?: return@Button
                        onSave(
                            PainMark(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                zone = zoneId,
                                intensity = level,
                                painTypes = types.map { it.id },
                                pinSide = existing?.pinSide,
                                pinX = existing?.pinX,
                                pinY = existing?.pinY
                            )
                        )
                    },
                    enabled = intensity != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.meds_save))
                }
                if (canRemove) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.intake_cancel))
                        }
                        OutlinedButton(
                            onClick = { confirmRemove = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.body_map_remove))
                        }
                    }
                } else {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.intake_cancel))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun zoneLabel(zone: BodyZone, intensity: Int?): String {
    val name = stringResource(zone.labelRes)
    return if (intensity == null) name else "$name ($intensity)"
}

private fun List<PainMark>.zoneHeat(): Map<String, Int> = PainHeatmap.zoneIntensityMax(this)

private fun List<PainMark>.zoneMark(zone: String): PainMark? =
    firstOrNull { it.zone == zone && !it.isPin }

private fun PainMark.withoutPin(): PainMark =
    copy(pinSide = null, pinX = null, pinY = null)

private fun internalHeat(marks: List<PainMark>): Map<String, Int> =
    marks.filter { !it.isPin && BodyZone.fromId(it.zone)?.side == BodySide.INTERNAL }
        .groupBy { it.zone }
        .mapValues { (_, list) -> list.maxOf { it.intensity } }

/** Keeps the region strokes and drops the paper fill plus the footer stamp. */
private fun lineArt(source: android.graphics.Bitmap): android.graphics.Bitmap {
    val copy = source.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    val width = copy.width
    val height = copy.height
    val pixels = IntArray(width * height)
    copy.getPixels(pixels, 0, width, 0, 0, width, height)
    for (index in pixels.indices) {
        val x = index % width
        val y = index / width
        val color = pixels[index]
        val alpha = color ushr 24 and 0xFF
        val red = color shr 16 and 0xFF
        val stamp = x in 190..340 && y in 526..562
        val ink = if (stamp || alpha == 0) 0 else (255 - red) * alpha / 255
        pixels[index] = ink shl 24
    }
    copy.setPixels(pixels, 0, width, 0, 0, width, height)
    return copy
}
