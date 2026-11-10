package com.xnotes.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.settings.SwatchRole
import com.xnotes.core.shapes.ConnectorPage
import com.xnotes.core.shapes.ConnectorPreset
import com.xnotes.core.shapes.ConnectorPresets
import com.xnotes.core.shapes.Head
import com.xnotes.core.shapes.ShapePage
import com.xnotes.core.shapes.ShapePreset
import com.xnotes.core.shapes.ShapePresets
import com.xnotes.core.tools.ShapeConfig
import com.xnotes.core.tools.ToolConversions
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.launch

/**
 * The shape tool's popup: its chips page by page, swiped sideways, the connectors below them the
 * same way, then the settings the chosen chip takes. A closed chip offers a fill with its own
 * colour, an open one or a connector a head at either end, and a star or polygon a minus/plus for
 * its count.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ShapeConfigPopup(editor: ToolPopupHost, onDismiss: () -> Unit) {
    var config by remember { mutableStateOf(editor.hostShapeConfig) }
    var colorPickerOpen by remember { mutableStateOf(false) }
    var editingPaletteColor by remember { mutableStateOf<Int?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }

    fun emit(next: ShapeConfig) {
        config = next
        editor.updateShapeConfig(next)
    }

    // Cache the flattened preset list against the actual preference inputs. Building a fresh
    // list on every recomposition made remember(shapes) ineffective and needlessly rebuilt pages.
    val shapes = remember(editor.hostHiddenShapes, editor.hostShapeOrder) {
        ShapePresets.shown(editor.hostHiddenShapes, editor.hostShapeOrder).flatMap { it.presets }
    }
    // Show five columns across four rows, using the existing vertical space to expose more
    // presets per page without shrinking the shape artwork. Only the visible page is composed.
    val shapePages = remember(shapes) { shapes.chunked(20) }
    val initialShapePage = config.shapePage.coerceIn(0, (shapePages.lastIndex).coerceAtLeast(0))
    var visibleShapePage by remember(shapes) { mutableStateOf(initialShapePage) }
    val page = visibleShapePage.coerceIn(0, (shapePages.lastIndex).coerceAtLeast(0))

    LaunchedEffect(config.shapePage, shapePages.size) {
        visibleShapePage = config.shapePage.coerceIn(0, (shapePages.lastIndex).coerceAtLeast(0))
    }

    fun changeShapePage(next: Int) {
        val safePage = next.coerceIn(0, (shapePages.lastIndex).coerceAtLeast(0))
        visibleShapePage = safePage
        if (config.shapePage != safePage) emit(config.copy(shapePage = safePage))
    }

    val paletteColors = editor.swatches(SwatchRole.SHAPE)
    val ink = editor.hostToolbarColors.getOrNull(editor.hostActiveColorIndex) ?: Rgba(0, 0, 0)
    val activeShapeColor = if (config.fill) config.fillColor ?: ink else ink
    val selected = ShapePresets.byKey(config.preset)
    val canFill = selected?.kind?.isClosed == true && config.connector == null

    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.clip(RoundedCornerShape(26.dp)),
        frameless = true,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .liquidGlassSurface(
                    RoundedCornerShape(26.dp),
                    LocalPalette.current.panel.toComposeColor(),
                    elevation = 14.dp,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PopupTitle("Shapes")
                Text(
                    "×",
                    color = LocalPalette.current.text.toComposeColor(),
                    fontSize = 20.sp,
                    modifier = Modifier.clip(CircleShape).clickable { onDismiss() }
                        .padding(horizontal = 5.dp),
                )
            }

            // Match the Pen panel's proportions: the shape selector gets the broad
            // left column; colour and the bucket action sit together in the narrow right rail.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Outline size", color = LocalPalette.current.text.toComposeColor(), fontSize = 12.sp)
                            Text("%.1f".format(config.strokeWidth), color = LocalPalette.current.text.toComposeColor(), fontSize = 11.sp)
                        }
                        DesignedSizeSlider(config.strokeWidth.toFloat(), 1f..20f) {
                            emit(config.copy(strokeWidth = it.toDouble()))
                        }
                    }

                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(LocalPalette.current.surface.toComposeColor())
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (shapePages.isNotEmpty()) {
                            // Compose one 5 × 4 grid at a time. Avoids nested pager measurement
                            // inside Material's dropdown popup, a known source of layout crashes.
                            // Horizontal swipes and the arrows both switch the lightweight page state.
                            var horizontalDrag by remember { mutableStateOf(0f) }
                            Box(
                                Modifier.fillMaxWidth().height(194.dp).pointerInput(page, shapePages.size) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (horizontalDrag < -48f) changeShapePage(page + 1)
                                            else if (horizontalDrag > 48f) changeShapePage(page - 1)
                                            horizontalDrag = 0f
                                        },
                                        onHorizontalDrag = { change, amount ->
                                            change.consume()
                                            horizontalDrag += amount
                                        },
                                    )
                                },
                            ) {
                                Column(
                                    modifier = Modifier.width(244.dp).align(Alignment.TopCenter),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    shapePages[page].chunked(5).forEach { gridRow ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            gridRow.forEach { preset ->
                                                Box(
                                                    modifier = Modifier.width(48.dp).height(44.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    KindChip(
                                                        ShapeIcons.of(preset),
                                                        chipLabel(preset),
                                                        selected = preset.key == config.preset,
                                                        latex = chipLatex(preset),
                                                        iconSize = 27.dp,
                                                    ) { emit(config.choose(preset)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "‹",
                                    color = if (page > 0) LocalPalette.current.text.toComposeColor() else LocalPalette.current.textDim.toComposeColor(),
                                    fontSize = 22.sp,
                                    modifier = Modifier.clickable(enabled = page > 0) {
                                        changeShapePage(page - 1)
                                    }.padding(horizontal = 12.dp, vertical = 1.dp),
                                )
                                Text(
                                    if (shapePages.size > 1) {
                                        "${page * 20 + 1}–${minOf((page + 1) * 20, shapes.size)} of ${shapes.size}"
                                    } else "All shapes",
                                    color = LocalPalette.current.textDim.toComposeColor(),
                                    fontSize = 11.sp,
                                )
                                Text(
                                    "›",
                                    color = if (page < shapePages.lastIndex) LocalPalette.current.text.toComposeColor() else LocalPalette.current.textDim.toComposeColor(),
                                    fontSize = 22.sp,
                                    modifier = Modifier.clickable(enabled = page < shapePages.lastIndex) {
                                        changeShapePage(page + 1)
                                    }.padding(horizontal = 12.dp, vertical = 1.dp),
                                )
                            }
                        } else {
                            Text("No shapes available", color = LocalPalette.current.textDim.toComposeColor(), fontSize = 11.sp)
                        }
                    }
                }

                Column(
                    Modifier.width(112.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LocalPalette.current.surface.toComposeColor().copy(alpha = 0.42f))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text("Colour", color = LocalPalette.current.text.toComposeColor(), fontSize = 12.sp)
                    FlowRow(
                        maxItemsInEachRow = 3,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        paletteColors.forEachIndexed { paletteIndex, color ->
                            val swatchColor = color.toComposeColor()
                            val isCurrent = color == activeShapeColor
                            val luminance = swatchColor.red * 0.299f + swatchColor.green * 0.587f + swatchColor.blue * 0.114f
                            Box(
                                Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                                    .background(swatchColor)
                                    .border(
                                        if (isCurrent) 2.5.dp else 1.dp,
                                        if (isCurrent) (if (luminance > 0.55f) Color.Black else Color.White)
                                        else LocalPalette.current.border.toComposeColor(),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            editor.setSwatchColor(editor.hostActiveColorIndex, color)
                                            if (config.fill) emit(config.copy(fillColor = color))
                                        },
                                        onLongClick = { editingPaletteColor = paletteIndex },
                                    ),
                            )
                        }
                    }

                    editingPaletteColor?.let { paletteIndex ->
                        val seed = paletteColors.getOrNull(paletteIndex)
                        if (seed != null) ColorPickerPopup(
                            initial = seed,
                            recents = editor.hostRecentColors,
                            onDismiss = { editingPaletteColor = null },
                            onPick = { picked ->
                                editor.setSwatch(SwatchRole.SHAPE, paletteIndex, picked)
                                if (activeShapeColor == seed) {
                                    if (config.fill) emit(config.copy(fillColor = picked))
                                    else editor.setSwatchColor(editor.hostActiveColorIndex, picked)
                                }
                                editingPaletteColor = null
                            },
                        )
                    }

                    if (activeShapeColor !in paletteColors) {
                        val customColor = activeShapeColor.toComposeColor()
                        val luminance = customColor.red * 0.299f + customColor.green * 0.587f + customColor.blue * 0.114f
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                                    .background(customColor)
                                    .border(
                                        2.5.dp,
                                        if (luminance > 0.55f) Color.Black else Color.White,
                                        RoundedCornerShape(6.dp),
                                    ),
                            )
                            Text("  Current", color = LocalPalette.current.textDim.toComposeColor(), fontSize = 10.sp)
                        }
                    }

                    Text(
                        "Custom colour",
                        color = LocalPalette.current.textDim.toComposeColor(),
                        fontSize = 10.sp,
                        modifier = Modifier.clickable { colorPickerOpen = true }.padding(vertical = 2.dp),
                    )
                    if (colorPickerOpen) {
                        ColorPickerPopup(
                            initial = activeShapeColor,
                            recents = editor.hostRecentColors,
                            onDismiss = { colorPickerOpen = false },
                            onPick = { picked ->
                                editor.setSwatchColor(editor.hostActiveColorIndex, picked)
                                if (config.fill) emit(config.copy(fillColor = picked))
                                colorPickerOpen = false
                            },
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (editor.hostBucketFillArmed) LocalPalette.current.selectionBackground.toComposeColor() else Color.Transparent)
                            .border(
                                1.dp,
                                if (editor.hostBucketFillArmed) LocalPalette.current.accent.toComposeColor() else LocalPalette.current.border.toComposeColor(),
                                RoundedCornerShape(9.dp),
                            )
                            .clickable {
                                val arm = !editor.hostBucketFillArmed
                                editor.setBucketFillArmed(arm)
                                if (arm) onDismiss()
                            }
                            .padding(horizontal = 7.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.FormatColorFill,
                            contentDescription = "Bucket fill",
                            tint = LocalPalette.current.text.toComposeColor(),
                            modifier = Modifier.size(17.dp),
                        )
                        Text("Bucket", color = LocalPalette.current.text.toComposeColor(), fontSize = 10.sp, maxLines = 1)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                    .clickable { advancedExpanded = !advancedExpanded }
                    .padding(horizontal = 2.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Advanced settings",
                    color = LocalPalette.current.text.toComposeColor(),
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                )
                Text(
                    if (advancedExpanded) "⌃" else "⌄",
                    color = LocalPalette.current.textDim.toComposeColor(),
                    fontSize = 16.sp,
                )
            }

            if (advancedExpanded) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPalette.current.border.toComposeColor()))
                if (canFill) {
                    ToggleRow("Fill shape", config.fill) { emit(config.copy(fill = it)) }
                    if (config.fill) {
                        SliderRow("Fill opacity", (config.fillAlpha * 100.0).toFloat(), 0f..100f) {
                            emit(config.copy(fillAlpha = (it / 100.0).coerceIn(0.0, 1.0)))
                        }
                    }
                }
                ToggleRow("Dashed line", config.dashed) { emit(config.copy(dashed = it)) }
            }
        }
    }
}

/**
 * The shape chips, [ShapePresets.COLUMNS] to a row, one family to a page, without the chips and
 * pages Preferences hides, in its order. The remembered page is an index into every page, hidden ones too.
 */
@Composable
private fun ShapePager(config: ShapeConfig, hidden: Set<String>, order: List<String>, columns: Int, onPage: (Int) -> Unit, onPick: (ShapePreset) -> Unit) {
    val pages = ShapePresets.shown(hidden, order)
    if (pages.isEmpty()) return
    val all = ShapePresets.pages.map { it.key }
    val initial = pages.indexOfFirst { it.key == all.getOrNull(config.shapePage) }.coerceAtLeast(0)
    val rows = pages.maxOf { (it.presets.size + columns - 1) / columns }
    ChipPager(pages.size, initial, { pageLabel(pages[it]) }, rows, { onPage(all.indexOf(pages[it].key)) }) { page ->
        for (row in pages[page].presets.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                for (p in row) KindChip(ShapeIcons.of(p), chipLabel(p), selected = p.key == config.preset, latex = chipLatex(p)) { onPick(p) }
            }
        }
    }
}

/** The connector chips, [ConnectorPresets.COLUMNS] to a row, two rows to a page, without those Preferences hides, in its order. */
@Composable
private fun ConnectorPager(config: ShapeConfig, hidden: Set<String>, order: List<String>, columns: Int, onPage: (Int) -> Unit, onPick: (ConnectorPreset) -> Unit) {
    val pages = ConnectorPresets.shown(hidden, order)
    if (pages.isEmpty()) return
    val all = ConnectorPresets.pages.map { it.key }
    val initial = pages.indexOfFirst { it.key == all.getOrNull(config.connectorPage) }.coerceAtLeast(0)
    ChipPager(pages.size, initial, { connectorPageLabel(pages[it]) }, ConnectorPresets.ROWS, { onPage(all.indexOf(pages[it].key)) }) { page ->
        for (row in pages[page].presets.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                for (p in row) KindChip(ShapeIcons.connector(p), stringResource(connectorLabel(p.key)), selected = p.key == config.preset) { onPick(p) }
            }
        }
    }
}

/**
 * Chips swiped sideways a page at a time: the page's caption above, the dots below, and every page
 * [rows] chips tall so a swipe never makes the popup jump. Remembers its page through [onPage].
 */
@Composable
private fun ChipPager(
    pageCount: Int,
    initialPage: Int,
    caption: (Int) -> Int,
    rows: Int,
    onPage: (Int) -> Unit,
    content: @Composable (Int) -> Unit,
) {
    val state = rememberPagerState(initialPage = initialPage.coerceIn(0, pageCount - 1)) { pageCount }
    LaunchedEffect(state.settledPage) { if (state.settledPage != initialPage) onPage(state.settledPage) }
    val palette = LocalPalette.current
    Text(
        stringResource(caption(state.currentPage)),
        color = palette.textDim.toComposeColor(),
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    HorizontalPager(state = state, modifier = Modifier.fillMaxWidth().height((rows * CHIP + (rows - 1) * GAP).dp)) { page ->
        Column(verticalArrangement = Arrangement.spacedBy(GAP.dp)) { content(page) }
    }
    if (pageCount > 1) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
            for (i in 0 until pageCount) {
                val on = i == state.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape)
                        .background((if (on) palette.accent else palette.border).toComposeColor()),
                )
            }
        }
    }
}

/**
 * The fill's colour: the outline's own, or one of the toolbar's swatches, the same ones in the same
 * order. Tapping the chosen swatch again opens the picker on it, as the toolbar does, and the swatch
 * takes the new colour everywhere.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FillColourRow(chosen: Rgba?, ink: Rgba, host: SwatchHost, onPick: (Rgba?) -> Unit) {
    val palette = LocalPalette.current
    var editing by remember { mutableStateOf<Int?>(null) }
    Text(stringResource(R.string.caption_fill_colour), color = palette.text.toComposeColor(), fontSize = 12.sp)
    FlowRow(
        Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Swatch(ink, selected = chosen == null, label = stringResource(R.string.fill_match_outline), mark = true) { onPick(null) }
        host.shownSwatches.forEachIndexed { i, c ->
            val colour = c.withAlpha(255)
            val on = chosen == colour
            Box {
                Swatch(colour, selected = on, label = null) { if (on) editing = i else onPick(colour) }
                if (editing == i) {
                    ColorPickerPopup(
                        initial = colour,
                        recents = host.hostRecentColors,
                        onDismiss = {
                            host.rememberSwatchColor(i)
                            editing = null
                        },
                        onPick = { picked ->
                            host.recolorSwatch(i, picked)
                            onPick(picked.withAlpha(255))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Rgba, selected: Boolean, label: String?, mark: Boolean = false, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val body = @Composable {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(color.toComposeColor())
                .border(if (selected) 2.dp else 1.dp, (if (selected) palette.accent else palette.border).toComposeColor(), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (mark) {
                Icon(
                    XnotesIcons.link,
                    contentDescription = label,
                    tint = (if (color.luminance() > 0.55) Rgba(0, 0, 0) else Rgba(255, 255, 255)).toComposeColor(),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
    if (label != null) Tooltip(label) { body() } else body()
}

/** One end's head: a chip for each, drawn on a short line. */
@Composable
private fun HeadRow(caption: String, chosen: Head, atStart: Boolean, onPick: (Head) -> Unit) {
    Text(caption, color = LocalPalette.current.text.toComposeColor(), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
        for (h in Head.entries) KindChip(ShapeIcons.head(h, atStart), stringResource(headLabel(h)), selected = h == chosen) { onPick(h) }
    }
}

/** A star's points or a polygon's sides, a step at a time within what the kind can draw. */
@Composable
private fun CountRow(config: ShapeConfig, onChange: (Int) -> Unit) {
    val range = config.chip.kind.nRange ?: return
    val palette = LocalPalette.current
    val count = config.count.coerceIn(range.first, range.last)
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(countLabel(config.chip.kind.countKind)),
            color = palette.text.toComposeColor(), fontSize = 12.sp, modifier = Modifier.width(120.dp),
        )
        StepButton("−", stringResource(R.string.count_fewer), enabled = count > range.first) { onChange(count - 1) }
        Text("$count", color = palette.text.toComposeColor(), fontSize = 14.sp, modifier = Modifier.width(36.dp).padding(horizontal = 8.dp))
        StepButton("+", stringResource(R.string.count_more), enabled = count < range.last) { onChange(count + 1) }
    }
}

@Composable
private fun StepButton(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Tooltip(label) {
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .border(1.dp, palette.border.toComposeColor(), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = (if (enabled) palette.text else palette.textDim).toComposeColor(), fontSize = 16.sp)
        }
    }
}

@StringRes
internal fun connectorPageLabel(page: ConnectorPage): Int =
    if (page.key == "uml") R.string.conn_page_uml else R.string.conn_page_connectors

@StringRes
internal fun connectorLabel(key: String): Int = when (key) {
    "conn_straight" -> R.string.conn_straight
    "conn_straight_arrow" -> R.string.conn_straight_arrow
    "conn_straight_both" -> R.string.conn_straight_both
    "conn_elbow" -> R.string.conn_elbow
    "conn_elbow_arrow" -> R.string.conn_elbow_arrow
    "conn_elbow_both" -> R.string.conn_elbow_both
    "conn_curved" -> R.string.conn_curved
    "conn_curved_arrow" -> R.string.conn_curved_arrow
    "conn_curved_both" -> R.string.conn_curved_both
    "conn_rounded" -> R.string.conn_rounded
    "conn_rounded_arrow" -> R.string.conn_rounded_arrow
    "conn_rounded_both" -> R.string.conn_rounded_both
    "uml_association" -> R.string.uml_association
    "uml_inheritance" -> R.string.uml_inheritance
    "uml_realization" -> R.string.uml_realization
    "uml_dependency" -> R.string.uml_dependency
    "uml_aggregation" -> R.string.uml_aggregation
    "uml_composition" -> R.string.uml_composition
    "uml_association_elbow" -> R.string.uml_association_elbow
    "uml_inheritance_elbow" -> R.string.uml_inheritance_elbow
    "uml_realization_elbow" -> R.string.uml_realization_elbow
    "uml_dependency_elbow" -> R.string.uml_dependency_elbow
    "uml_aggregation_elbow" -> R.string.uml_aggregation_elbow
    else -> R.string.uml_composition_elbow
}

@StringRes
internal fun pageLabel(page: ShapePage): Int = when (page.key) {
    "flowchart" -> R.string.shape_page_flowchart
    "arrows" -> R.string.shape_page_arrows
    "solids_math" -> R.string.shape_page_solids_math
    "functions" -> R.string.shape_page_functions
    else -> R.string.shape_page_basic
}

/** A chip's name: a function chip names what it plots, every other chip its kind. */
@Composable
internal fun chipLabel(p: ShapePreset): String =
    if (p.key.startsWith(ShapePresets.FUNCTION_PREFIX)) functionLabel(p.key.removePrefix(ShapePresets.FUNCTION_PREFIX))
    else stringResource(presetLabel(p.key))

@StringRes
internal fun presetLabel(key: String): Int = when (key) {
    "line" -> R.string.shape_line
    "arrow" -> R.string.shape_arrow
    "spline" -> R.string.shape_spline
    "rectangle" -> R.string.shape_rectangle
    "rounded_rect" -> R.string.shape_rounded_rect
    "square" -> R.string.shape_square
    "capsule" -> R.string.shape_capsule
    "ellipse" -> R.string.shape_ellipse
    "circle" -> R.string.shape_circle
    "triangle" -> R.string.shape_triangle
    "right_triangle" -> R.string.shape_right_triangle
    "diamond" -> R.string.shape_diamond
    "parallelogram" -> R.string.shape_parallelogram
    "trapezoid" -> R.string.shape_trapezoid
    "pentagon" -> R.string.shape_pentagon
    "hexagon" -> R.string.shape_hexagon
    "octagon" -> R.string.shape_octagon
    "star" -> R.string.shape_star
    "cross" -> R.string.shape_cross
    "heart" -> R.string.shape_heart
    "cloud" -> R.string.shape_cloud
    "ring" -> R.string.shape_ring
    "pie" -> R.string.shape_pie
    "arc" -> R.string.shape_arc
    ShapePresets.FREE_POLYGON -> R.string.shape_free_polygon
    "flow_process" -> R.string.shape_flow_process
    "flow_alt_process" -> R.string.shape_flow_alt_process
    "flow_decision" -> R.string.shape_flow_decision
    "flow_data" -> R.string.shape_flow_data
    "flow_terminator" -> R.string.shape_flow_terminator
    "flow_preparation" -> R.string.shape_flow_preparation
    "flow_predefined" -> R.string.shape_flow_predefined
    "flow_internal_storage" -> R.string.shape_flow_internal_storage
    "flow_document" -> R.string.shape_flow_document
    "flow_multidocument" -> R.string.shape_flow_multidocument
    "flow_manual_input" -> R.string.shape_flow_manual_input
    "flow_manual_operation" -> R.string.shape_flow_manual_operation
    "flow_connector" -> R.string.shape_flow_connector
    "flow_off_page" -> R.string.shape_flow_off_page
    "flow_database" -> R.string.shape_flow_database
    "flow_direct_access" -> R.string.shape_flow_direct_access
    "flow_display" -> R.string.shape_flow_display
    "flow_delay" -> R.string.shape_flow_delay
    "flow_stored_data" -> R.string.shape_flow_stored_data
    "flow_sort" -> R.string.shape_flow_sort
    "flow_merge" -> R.string.shape_flow_merge
    "flow_extract" -> R.string.shape_flow_extract
    "flow_summing" -> R.string.shape_flow_summing
    "flow_or" -> R.string.shape_flow_or
    "arrow_right" -> R.string.shape_arrow_right
    "arrow_left" -> R.string.shape_arrow_left
    "arrow_up" -> R.string.shape_arrow_up
    "arrow_down" -> R.string.shape_arrow_down
    "arrow_left_right" -> R.string.shape_arrow_left_right
    "arrow_up_down" -> R.string.shape_arrow_up_down
    "arrow_quad" -> R.string.shape_arrow_quad
    "arrow_bent" -> R.string.shape_arrow_bent
    "arrow_u_turn" -> R.string.shape_arrow_u_turn
    "arrow_chevron" -> R.string.shape_arrow_chevron
    "arrow_pentagon" -> R.string.shape_arrow_pentagon
    "arrow_notched" -> R.string.shape_arrow_notched
    "arrow_curved_right" -> R.string.shape_arrow_curved_right
    "arrow_curved_left" -> R.string.shape_arrow_curved_left
    "arrow_circular" -> R.string.shape_arrow_circular
    "arrow_striped" -> R.string.shape_arrow_striped
    "arrow_bent_up" -> R.string.shape_arrow_bent_up
    "arrow_left_up" -> R.string.shape_arrow_left_up
    "callout_rect" -> R.string.shape_callout_rect
    "callout_round" -> R.string.shape_callout_round
    "callout_oval" -> R.string.shape_callout_oval
    "callout_thought" -> R.string.shape_callout_thought
    "callout_line" -> R.string.shape_callout_line
    "burst" -> R.string.shape_burst
    "solid_cube" -> R.string.shape_solid_cube
    "solid_cylinder" -> R.string.shape_solid_cylinder
    "solid_cone" -> R.string.shape_solid_cone
    "solid_sphere" -> R.string.shape_solid_sphere
    "solid_pyramid" -> R.string.shape_solid_pyramid
    "solid_tetrahedron" -> R.string.shape_solid_tetrahedron
    "solid_tri_prism" -> R.string.shape_solid_tri_prism
    "solid_hex_prism" -> R.string.shape_solid_hex_prism
    "solid_frustum" -> R.string.shape_solid_frustum
    "solid_hemisphere" -> R.string.shape_solid_hemisphere
    "math_axes_2d" -> R.string.shape_math_axes_2d
    "math_axes_3d" -> R.string.shape_math_axes_3d
    "math_number_line" -> R.string.shape_math_number_line
    "math_grid" -> R.string.shape_math_grid
    "math_angle" -> R.string.shape_math_angle
    "math_right_angle" -> R.string.shape_math_right_angle
    "math_curly_brace" -> R.string.shape_math_curly_brace
    "math_square_bracket" -> R.string.shape_math_square_bracket
    "math_round_bracket" -> R.string.shape_math_round_bracket
    else -> R.string.shape_polygon
}

@StringRes
internal fun countLabel(kind: com.xnotes.core.shapes.CountKind): Int = when (kind) {
    com.xnotes.core.shapes.CountKind.SIDES -> R.string.caption_sides
    com.xnotes.core.shapes.CountKind.POINTS -> R.string.caption_points
    com.xnotes.core.shapes.CountKind.TICKS -> R.string.caption_ticks
    com.xnotes.core.shapes.CountKind.CELLS -> R.string.caption_cells
}

@StringRes
private fun headLabel(head: Head): Int = when (head) {
    Head.NONE -> R.string.head_none
    Head.OPEN -> R.string.head_open
    Head.FILLED -> R.string.head_filled
    Head.HOLLOW_TRIANGLE -> R.string.head_hollow_triangle
    Head.FILLED_DIAMOND -> R.string.head_filled_diamond
    Head.HOLLOW_DIAMOND -> R.string.head_hollow_diamond
}

/** A chip is 38 dp across; six of them and their gaps fill the popup's 256 dp. */
private const val CHIP = 38
private const val GAP = 5
