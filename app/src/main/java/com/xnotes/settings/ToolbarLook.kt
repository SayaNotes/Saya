package com.xnotes.settings

/** How the editor toolbar is drawn; one setting for both the paged and the canvas bar. */
data class ToolbarLook(
    /** The toolbar is intentionally fixed to the bottom floating position. */
    val position: ToolbarPosition = ToolbarPosition.BOTTOM,
    val size: ToolbarSize = ToolbarSize.REGULAR,
    /** The editor uses floating toolbar surfaces only; docked mode is not offered. */
    val floating: Boolean = true,
)

/** Which edge of its pane the toolbar runs along. */
enum class ToolbarPosition(val id: String) {
    TOP("top"),
    BOTTOM("bottom"),
    LEFT("left"),
    RIGHT("right");

    val vertical: Boolean get() = this == LEFT || this == RIGHT

    companion object {
        fun fromId(id: String): ToolbarPosition = entries.find { it.id == id } ?: TOP
    }
}

/** How big the toolbar's buttons are. */
enum class ToolbarSize(val id: String) {
    COMPACT("compact"),
    REGULAR("regular"),
    COMFORTABLE("comfortable");

    companion object {
        fun fromId(id: String): ToolbarSize = entries.find { it.id == id } ?: REGULAR
    }
}
