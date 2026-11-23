package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.odclcslkrpb5k.drawing.core.BackupBudget
import org.odclcslkrpb5k.drawing.core.BackupState
import org.odclcslkrpb5k.drawing.core.PageId

// The parent screen's views. Unlike the child's screens, it has words: a parent reads them.

internal fun Context.parentHeader(text: Int, back: () -> Unit): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            iconButton(R.drawable.ic_home, "back") { back() },
            LinearLayout.LayoutParams(dp(TARGET_DP), dp(TARGET_DP)),
        )
        addView(parentLabel(text))
    }

internal fun Context.parentLabel(text: Int) =
    TextView(this).apply {
        setText(text)
        textSize = TEXT_SP
        setPadding(dp(PARENT_PAD), dp(PARENT_PAD), dp(PARENT_PAD), dp(PARENT_PAD))
    }

/** Page tiles, each with its [actions] laid along its foot. */
internal fun Context.parentGrid(
    pages: List<Pair<PageId, Bitmap?>>,
    actions: (PageId) -> List<View>,
) =
    GridLayout(this).apply {
        columnCount = PARENT_COLUMNS
        for ((id, thumb) in pages) {
            val cell = FrameLayout(this@parentGrid)
            cell.addView(parentTile(thumb, "drawing"), FrameLayout.LayoutParams(MATCH, MATCH))
            val row = LinearLayout(this@parentGrid)
            for (a in actions(id)) {
                row.addView(a, LinearLayout.LayoutParams(dp(TARGET_DP), dp(TARGET_DP)))
            }
            cell.addView(
                row,
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END),
            )
            addView(cell, parentTileParams())
        }
    }

internal fun Context.parentTile(image: Bitmap?, description: String) =
    ImageView(this).apply {
        background =
            GradientDrawable().apply {
                cornerRadius = dp(CORNER).toFloat()
                setColor(TILE)
            }
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = description
        if (image != null) setImageBitmap(image)
    }

internal fun Context.parentTileParams() =
    GridLayout.LayoutParams().apply {
        width = dp(TILE_W)
        height = dp(TILE_H)
        setMargins(dp(PARENT_PAD), dp(PARENT_PAD), dp(PARENT_PAD), dp(PARENT_PAD))
    }

/** How much of Google's backup allowance the drawings use, in a color that says whether to act. */
internal fun Context.backupLine(bytes: Long): TextView {
    val size = BackupBudget.megabytes(bytes)
    val (text, color) =
        when (BackupBudget.state(bytes)) {
            BackupState.FINE -> getString(R.string.parent_backup_fine, size) to BACKUP_FINE
            BackupState.NEARLY_FULL ->
                getString(R.string.parent_backup_nearly_full, size) to BACKUP_WARN
            BackupState.OVER -> getString(R.string.parent_backup_over, size) to BACKUP_OVER
        }
    return TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = TEXT_SP
        setPadding(dp(PARENT_PAD), 0, dp(PARENT_PAD), dp(PARENT_PAD))
    }
}

internal const val PARENT_COLUMNS = 4
internal const val PARENT_PAD = 12
private const val MATCH = MATCH_PARENT
private const val WRAP = WRAP_CONTENT
private const val TILE_W = 280
private const val TILE_H = 175
private const val CORNER = 24
private const val TEXT_SP = 20f
private const val TILE = 0xFFFFFFFF.toInt()
private const val BACKUP_FINE = 0xFF5D5D5D.toInt()
private const val BACKUP_WARN = 0xFFE65100.toInt()
private const val BACKUP_OVER = 0xFFC62828.toInt()
