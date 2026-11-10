package com.xnotes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.toComposeColor

/** Which tool a toolbar item arms, for the items that are simply a tool button. */
private val CANVAS_TOOL_OF: Map<ToolbarItem, Tool> = mapOf(
    ToolbarItem.PEN to Tool.PEN,
    ToolbarItem.DASHED to Tool.DASHED,
    ToolbarItem.CALLIGRAPHY to Tool.CALLIGRAPHY,
    ToolbarItem.TAPER to Tool.TAPER,
    ToolbarItem.LASER to Tool.LASER,
    ToolbarItem.STAMP to Tool.STAMP,
    ToolbarItem.HIGHLIGHTER to Tool.HIGHLIGHTER,
    ToolbarItem.ERASER to Tool.ERASER,
    ToolbarItem.PAN to Tool.PAN,
    ToolbarItem.SELECT to Tool.SELECT,
    ToolbarItem.SHAPE to Tool.SHAPE,
    ToolbarItem.TAPE to Tool.TAPE,
)

/**
 * The infinite canvas's chrome. A separate bar from the paged [Toolbar] because most of that one
 * addresses pages, viewing modes, pagination and text, none of which exist here; but it is built
 * from the same pieces, so the two look like one app rather than two.
 *
 * It draws from its own [com.xnotes.core.tools.ToolbarLayout], arranged in Preferences exactly as
 * the paged bar is. Its own, not the paged one: page navigation means nothing here and a waypoint
 * means nothing there, so the two layouts hold different items and are stored apart.
 */
@Composable
fun InfiniteToolbar(
    editor: InfiniteEditor,
    onOpenBackstage: () -> Unit,
    onInsertImage: () -> Unit = {},
    onClosePane: (() -> Unit)? = null,
    onInsertAudio: () -> Unit = {},
    onRecordAudio: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
) {
    // Retain the resource-backed icon lookup across canvas toolbar recompositions.
    val penIcon = ImageVector.vectorResource(R.drawable.ic_stroke_regular)
    val dashedIcon = ImageVector.vectorResource(R.drawable.ic_stroke_dashed)
    val calligraphyIcon = ImageVector.vectorResource(R.drawable.ic_stroke_calligraphy)
    val taperIcon = ImageVector.vectorResource(R.drawable.ic_stroke_taper)
    val laserIcon = ImageVector.vectorResource(R.drawable.ic_laser_pen)
    val stampIcon = ImageVector.vectorResource(R.drawable.ic_stamp_pen)
    val highlighterIcon = ImageVector.vectorResource(R.drawable.ic_stroke_highlighter)
    val toolIcons: Map<Tool, ImageVector> = remember(
        penIcon, dashedIcon, calligraphyIcon, taperIcon, laserIcon, stampIcon, highlighterIcon,
    ) {
        mapOf(
            Tool.PEN to penIcon,
            Tool.DASHED to dashedIcon,
            Tool.CALLIGRAPHY to calligraphyIcon,
            Tool.TAPER to taperIcon,
            Tool.LASER to laserIcon,
            Tool.STAMP to stampIcon,
            Tool.HIGHLIGHTER to highlighterIcon,
            Tool.PAN to XnotesIcons.pan,
            Tool.ERASER to XnotesIcons.eraser,
            Tool.SHAPE to XnotesIcons.shape,
            Tool.SELECT to XnotesIcons.select,
            Tool.LASSO to XnotesIcons.lasso,
            Tool.TAPE to XnotesIcons.tape,
        )
    }
    var stylesOpen by remember { mutableStateOf(false) }
    var eraserOpen by remember { mutableStateOf(false) }
    var shapeOpen by remember { mutableStateOf(false) }
    var waypointsOpen by remember { mutableStateOf(false) }
    var penOpen by remember { mutableStateOf<Tool?>(null) }
    var selectOpen by remember { mutableStateOf<Tool?>(null) }
    var tapeOpen by remember { mutableStateOf(false) }
    // Which swatch has the colour picker open, as on the paged bar: tapping the armed swatch again
    // opens it, so the five colours are both the ink and the way to change it.
    var switcherIndex by remember { mutableStateOf<Int?>(null) }

    // Pinned outside the scrolling strip so closing a split pane is always one tap away.
    ToolbarFrame(armed = if (editor.tool == Tool.LASSO) Tool.SELECT else editor.tool, trailing = onClosePane?.let { { ClosePaneButton(it) } }) {
        val sections = editor.toolbarLayout.visibleSections
        sections.forEachIndexed { index, section ->
            if (index > 0) Separator()
            for (entry in section.visibleEntries) {
                when (val item = entry.item) {
                    ToolbarItem.HOME -> ToolbarIcon(XnotesIcons.prev, stringResource(R.string.toolbar_home)) { onOpenBackstage() }
                    // A name has no room down a side rail.
                    ToolbarItem.TITLE -> if (!LocalBar.current.vertical) Label(if (editor.document.displayName == null && editor.document.path == null) stringResource(R.string.untitled) else editor.title, Modifier.padding(end = 4.dp))

                    in CANVAS_TOOL_OF -> {
                        val tool = CANVAS_TOOL_OF.getValue(item)
                        val icon = toolIcons[tool]
                        if (icon != null) {
                            Box {
                                // Taper is the active-pen slot on the canvas toolbar; its
                                // illustration follows the selected pen style.
                                val displayTool = if (tool == Tool.TAPER && editor.tool.isPenPanelStyle) editor.tool else tool
                                val selectionModeActive = tool == Tool.SELECT && editor.tool == Tool.LASSO
                                val armed = if (editor.tool.isPenPanelStyle && tool.isStroke) tool == Tool.TAPER else editor.tool == tool || selectionModeActive
                                val configurable = tool.isStroke || tool == Tool.ERASER || tool == Tool.SHAPE || tool == Tool.SELECT || tool == Tool.TAPE
                                val imageRes = when (displayTool) {
                                    Tool.PEN -> R.drawable.pen_tool_pen
                                    Tool.CALLIGRAPHY -> R.drawable.pen_tool_calligraphy
                                    Tool.HIGHLIGHTER -> R.drawable.pen_tool_highlighter
                                    Tool.TAPER -> R.drawable.pen_tool_taper
                                    Tool.LASER -> R.drawable.pen_tool_laser
                                    Tool.STAMP -> R.drawable.pen_tool_stamp
                                    else -> null
                                }
                                val nibMaskRes = when (displayTool) {
                                    Tool.PEN -> R.drawable.pen_nib_mask
                                    Tool.CALLIGRAPHY -> R.drawable.calligraphy_nib_mask
                                    Tool.HIGHLIGHTER -> R.drawable.highlighter_nib_mask
                                    Tool.TAPER -> R.drawable.taper_nib_mask
                                    else -> null
                                }
                                val image = imageRes?.let { painterResource(it) }
                                val nibMask = nibMaskRes?.let { bitmapPainterResource(it) ?: painterResource(it) }
                                val nibColor = editor.toolConfig(displayTool).colorOverride?.toComposeColor()
                                    ?: editor.toolbarColors.getOrNull(editor.activeColorIndex)?.toComposeColor()
                                    ?: androidx.compose.ui.graphics.Color.Black
                                val stampGlyph = if (displayTool == Tool.STAMP) editor.toolConfig(Tool.STAMP).stampGlyph else null
                                ToolbarIcon(
                                    icon,
                                    stringResource(displayTool.labelRes),
                                    active = armed,
                                    glideKey = displayTool,
                                    hint = if ((armed || editor.tool == tool) && configurable) stringResource(R.string.tip_tap_for_settings) else null,
                                    image = image,
                                    nibMask = nibMask,
                                    nibColor = nibColor,
                                    stampGlyph = stampGlyph,
                                ) {
                                    when {
                                        tool == Tool.ERASER && editor.tool == tool -> eraserOpen = true
                                        tool == Tool.SHAPE && editor.tool == tool -> shapeOpen = true
                                        tool == Tool.SELECT && (editor.tool == Tool.SELECT || editor.tool == Tool.LASSO) -> selectOpen = editor.tool
                                        tool == Tool.TAPE && editor.tool == tool -> tapeOpen = true
                                        tool.isStroke && (armed || editor.tool == tool) -> penOpen = tool
                                        else -> editor.armTool(tool)
                                    }
                                }
                                // The very popups the paged toolbar opens, not lookalikes: same
                                // controls, same wording, same ranges, and they cannot drift apart.
                                if (tool == Tool.ERASER && eraserOpen) EraserConfigPopup(editor) { eraserOpen = false }
                                if (tool == Tool.SHAPE && shapeOpen) ShapeConfigPopup(editor) { shapeOpen = false }
                                if (tool == Tool.SELECT && selectOpen != null) {
                                    SelectConfigPopup(
                                        editor,
                                        selectOpen ?: Tool.SELECT,
                                        onSelectTool = { editor.armTool(it); selectOpen = null },
                                    ) { selectOpen = null }
                                }
                                if (tool == Tool.TAPE && tapeOpen) TapeConfigPopup(editor) { tapeOpen = false }
                                if (penOpen == tool) {
                                    ToolConfigPopup(editor, displayTool, onSelectTool = { editor.armTool(it) }) { penOpen = null }
                                }
                            }
                        }
                    }

                    ToolbarItem.WAND -> ToolbarIcon(
                        XnotesIcons.magicWand,
                        stringResource(R.string.tool_wand),
                        active = editor.wandEnabled,
                    ) { editor.toggleWand() }

                    ToolbarItem.IMAGE -> InsertButton(
                        owner = editor,
                        imageRows = listOf(
                            InsertRow(XnotesIcons.image, stringResource(R.string.insert_image_ellipsis)) { onInsertImage() },
                            InsertRow(XnotesIcons.camera, stringResource(R.string.take_photo)) { onTakePhoto() },
                        ),
                        onAudioFromFile = onInsertAudio,
                        onRecord = onRecordAudio,
                    )

                    ToolbarItem.COLORS ->
                        editor.toolbarColors.take(editor.toolbarColorCount).forEachIndexed { i, color ->
                            Box {
                                Swatch(color.toComposeColor(), active = editor.activeColorIndex == i, drag = editor, ink = color) {
                                    if (editor.activeColorIndex == i) switcherIndex = i else editor.pickColor(i)
                                }
                                if (switcherIndex == i) ColorSwitcherPopup(editor, i) { switcherIndex = null }
                            }
                        }

                    ToolbarItem.UNDO ->
                        ToolbarIcon(XnotesIcons.undo, stringResource(R.string.undo), enabled = editor.canUndo, shortcut = Keys.UNDO) { editor.undo() }
                    ToolbarItem.REDO ->
                        ToolbarIcon(XnotesIcons.redo, stringResource(R.string.redo), enabled = editor.canRedo, shortcut = Keys.REDO) { editor.redo() }

                    // Where the paged bar keeps its page, styles and view menus. Waypoints take the
                    // place of pagination: on an unbounded canvas, a saved view is what a page
                    // number was.
                    ToolbarItem.STYLES -> Box {
                        ToolbarIcon(XnotesIcons.sliders, stringResource(R.string.toolbar_styles), active = stylesOpen) { stylesOpen = true }
                        if (stylesOpen) CanvasStylesPopup(editor) { stylesOpen = false }
                    }
                    ToolbarItem.WAYPOINTS -> Box {
                        ToolbarIcon(XnotesIcons.bookmark, stringResource(R.string.toolbar_waypoints), active = waypointsOpen) { waypointsOpen = true }
                        if (waypointsOpen) CanvasWaypointsPopup(editor) { waypointsOpen = false }
                    }
                    ToolbarItem.MINIMAP ->
                        ToolbarIcon(XnotesIcons.map, stringResource(R.string.toolbar_minimap), active = editor.minimapVisible) {
                            editor.toggleMinimap()
                        }

                    ToolbarItem.ZOOM -> {
                        ToolbarIcon(XnotesIcons.zoomOut, stringResource(R.string.zoom_out), enabled = !editor.zoomLocked, shortcut = Keys.ZOOM_OUT) {
                            editor.zoomBy(1.0 / InfiniteEditor.ZOOM_STEP)
                        }
                        Label("${editor.zoomPercent}%")
                        ToolbarIcon(XnotesIcons.zoomIn, stringResource(R.string.zoom_in), enabled = !editor.zoomLocked, shortcut = Keys.ZOOM_IN) {
                            editor.zoomBy(InfiniteEditor.ZOOM_STEP)
                        }
                    }
                    ToolbarItem.FIT ->
                        ToolbarIcon(XnotesIcons.fit, stringResource(R.string.fit_all), enabled = !editor.zoomLocked, shortcut = Keys.FIT_ALL) { editor.zoomToFit() }

                    ToolbarItem.ZOOM_LOCK -> ToolbarIcon(
                        if (editor.zoomLocked) XnotesIcons.lock else XnotesIcons.unlock,
                        stringResource(if (editor.zoomLocked) R.string.unlock_zoom else R.string.lock_zoom),
                        active = editor.zoomLocked,
                    ) { editor.toggleZoomLock() }

                    // Everything else belongs to the paged bar; a canvas layout never holds one.
                    else -> Unit
                }
            }
        }

        if (!LocalBar.current.vertical) editor.renderFailure?.let {
            Separator()
            Label(stringResource(R.string.gl_unavailable))
        }
        if (!LocalBar.current.vertical && editor.outOfMemory) {
            Separator()
            Label(stringResource(R.string.canvas_out_of_memory))
        }
        Spacer(Modifier.padding(horizontal = 2.dp))
    }
}
