package com.crazestudio.stone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazestudio.stone.ui.theme.LocalStoneColors
import com.crazestudio.stone.ui.theme.PlusJakartaSansFontFamily
import com.crazestudio.stone.ui.theme.SpaceGroteskFontFamily
import com.crazestudio.stone.ui.theme.StoneOrange

@Composable
fun StoneMark(size: Dp, modifier: Modifier = Modifier) {
    val colors = LocalStoneColors.current
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val left = Path().apply {
            moveTo(w * .04f, h * .25f)
            lineTo(w * .32f, h * .05f)
            lineTo(w * .57f, h * .05f)
            lineTo(w * .29f, h * .95f)
            lineTo(w * .04f, h * .95f)
            close()
        }
        val right = Path().apply {
            moveTo(w * .43f, h * .05f)
            lineTo(w * .72f, h * .05f)
            lineTo(w * .98f, h * .25f)
            lineTo(w * .70f, h * .95f)
            lineTo(w * .45f, h * .95f)
            lineTo(w * .72f, h * .35f)
            close()
        }
        drawPath(left, if (colors.isLight) Color(0xFF1A1A1A) else Color(0xFFF1F1F1))
        drawPath(right, if (colors.isLight) Color(0xFF6B6B6B) else Color(0xFFB6B6B6))
    }
}

@Composable
fun StoneBrand(compact: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalStoneColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StoneMark(if (compact) 24.dp else 34.dp)
        Text(
            text = "STONE",
            color = colors.text,
            fontFamily = SpaceGroteskFontFamily,
            fontSize = if (compact) 17.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalStoneColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconAction(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    description = "Back",
                    onClick = onBack,
                    testTag = "header_back_button"
                )
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = title,
                    color = colors.text,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = colors.muted,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
            actions()
        }
        HorizontalDivider(color = colors.line)
    }
}

@Composable
fun StoneCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalStoneColors.current
    val borderColor = if (highlighted) StoneOrange else colors.line
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.panel2),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(0.dp),
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.panel2),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(0.dp),
            content = content
        )
    }
}

@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    iconSize: Dp = 20.dp,
    testTag: String? = null
) {
    val colors = LocalStoneColors.current
    val resolvedTint = if (tint == Color.Unspecified) colors.text else tint
    val finalModifier = modifier
        .minimumInteractiveComponentSize()
        .let { if (testTag != null) it.testTag(testTag) else it }
    IconButton(onClick = onClick, modifier = finalModifier) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = resolvedTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun ActionTile(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val colors = LocalStoneColors.current
    val tileMod = modifier
        .height(82.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }
    StoneCard(tileMod, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = StoneOrange, modifier = Modifier.size(18.dp))
                Text(
                    text = title,
                    color = colors.text,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1
                )
            }
            Text(
                text = detail,
                color = colors.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 26.dp, top = 4.dp),
                maxLines = 1
            )
        }
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val colors = LocalStoneColors.current
    val btnMod = modifier
        .height(48.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }
    Button(
        onClick = onClick,
        modifier = btnMod,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.panel2,
            contentColor = colors.text
        ),
        border = BorderStroke(1.dp, colors.line),
        shape = RoundedCornerShape(11.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(
    title: String,
    detail: String,
    icon: ImageVector = Icons.Default.FolderOpen,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalStoneColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(icon, null, tint = colors.muted, modifier = Modifier.size(36.dp))
        Text(
            text = title,
            color = colors.text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = detail,
            color = colors.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun StoneInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    testTag: String? = null
) {
    val colors = LocalStoneColors.current
    val inputMod = modifier
        .fillMaxWidth()
        .let { if (testTag != null) it.testTag(testTag) else it }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = inputMod,
        singleLine = singleLine,
        placeholder = { Text(placeholder, color = colors.muted, fontSize = 13.sp) },
        textStyle = TextStyle(
            color = colors.text,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 13.sp
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
            cursorColor = StoneOrange,
            focusedBorderColor = StoneOrange,
            unfocusedBorderColor = colors.line,
            focusedContainerColor = colors.panel,
            unfocusedContainerColor = colors.panel
        ),
        shape = RoundedCornerShape(11.dp)
    )
}

@Composable
fun StoneDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmTestTag: String = "dialog_confirm_button",
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalStoneColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = colors.text,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(confirmTestTag)
            ) {
                Text(confirmLabel, color = StoneOrange, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Cancel", color = colors.muted)
            }
        },
        containerColor = colors.panel,
        titleContentColor = colors.text,
        textContentColor = colors.text
    )
}
