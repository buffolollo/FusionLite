package com.fusionlite.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------

/**
 * Standard content card. [onClick] turns it into an interactive surface with
 * the shared press/hover/focus recipe; [accent] tints the border for grouped
 * sections without changing the container colour.
 */
@Composable
fun FusionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    padding: Dp = LocalFusionDesign.current.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val shape = RoundedCornerShape(design.radiusXl)
    val interaction = remember { MutableInteractionSource() }
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(palette.surfaceRaised, shape)
        .border(1.dp, accent?.copy(alpha = 0.32f) ?: palette.border, shape)
    val surface = if (onClick != null) {
        base.fusionClickable(design, shape, interaction, onClick = onClick)
    } else {
        base
    }
    Column(
        modifier = surface.padding(padding),
        verticalArrangement = Arrangement.spacedBy(design.spacingMd),
        content = content,
    )
}

@Composable
fun FusionDivider(modifier: Modifier = Modifier) {
    val palette = LocalFusionPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.border.copy(alpha = 0.7f))
    )
}

// ---------------------------------------------------------------------------
// Headers
// ---------------------------------------------------------------------------

@Composable
fun FusionScreenHeader(
    eyebrow: String,
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                eyebrow.uppercase(),
                style = design.type.labelMicro,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                title,
                style = design.type.title,
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(design.spacingMd))
            trailing()
        }
    }
}

@Composable
fun FusionSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (accent != null) {
                    Box(
                        Modifier
                            .size(width = 3.dp, height = 16.dp)
                            .background(accent, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(design.spacingSm))
                }
                Text(
                    title,
                    style = design.type.heading,
                    color = palette.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = design.type.bodySmall,
                    color = palette.textSecondary,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

enum class FusionButtonVariant { PRIMARY, SECONDARY, GHOST, DANGER }

@Composable
fun FusionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FusionButtonVariant = FusionButtonVariant.PRIMARY,
    icon: FusionIcon? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillWidth: Boolean = false,
    accent: Color? = null,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(design.radiusMd)
    val tone = accent ?: palette.cyan
    val container = when (variant) {
        FusionButtonVariant.PRIMARY -> scheme.primary
        FusionButtonVariant.SECONDARY -> palette.glassStrong
        FusionButtonVariant.GHOST -> Color.Transparent
        FusionButtonVariant.DANGER -> palette.danger.copy(alpha = 0.14f)
    }
    val contentColor = when (variant) {
        FusionButtonVariant.PRIMARY -> scheme.onPrimary
        FusionButtonVariant.SECONDARY -> palette.textPrimary
        FusionButtonVariant.GHOST -> tone
        FusionButtonVariant.DANGER -> palette.danger
    }
    val borderColor = when (variant) {
        FusionButtonVariant.PRIMARY -> Color.Transparent
        FusionButtonVariant.SECONDARY -> palette.borderStrong
        FusionButtonVariant.GHOST -> Color.Transparent
        FusionButtonVariant.DANGER -> palette.danger.copy(alpha = 0.42f)
    }
    val effectiveContent = if (enabled && !loading) contentColor else design.states.disabledContent
    val effectiveContainer = if (enabled && !loading) container else design.states.disabledContainer
    Row(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = design.minTouchTarget)
            .clip(shape)
            .background(effectiveContainer, shape)
            .then(
                if (borderColor != Color.Transparent) Modifier.border(1.dp, borderColor, shape) else Modifier
            )
            .fusionClickable(
                design = design,
                shape = shape,
                interactionSource = interaction,
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .semantics {
                if (loading) stateDescription = "Working"
            }
            .padding(horizontal = design.spacingXl, vertical = design.spacingMd),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = effectiveContent,
            )
            Spacer(Modifier.width(design.spacingSm))
        } else if (icon != null) {
            FusionIcon(icon, size = 18.dp, tint = effectiveContent)
            Spacer(Modifier.width(design.spacingSm))
        }
        Text(
            text,
            style = design.type.subheading,
            color = effectiveContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Text-only action used for secondary navigation ("Jump to today"). It keeps a
 * 48dp target and the shared focus ring without a filled container.
 */
@Composable
fun FusionTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: FusionIcon? = null,
    accent: Color = LocalFusionPalette.current.cyan,
    enabled: Boolean = true,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(design.radiusPill)
    Row(
        modifier
            .heightIn(min = design.minTouchTarget)
            .clip(shape)
            .fusionClickable(
                design = design,
                shape = shape,
                interactionSource = interaction,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = design.spacingSm, vertical = design.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            FusionIcon(
                icon,
                size = 15.dp,
                tint = if (enabled) accent else design.states.disabledContent,
            )
        }
        Text(
            text,
            style = design.type.label,
            fontWeight = FontWeight.Bold,
            color = if (enabled) accent else design.states.disabledContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FusionIconButton(
    icon: FusionIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalFusionPalette.current.textPrimary,
    container: Color = LocalFusionPalette.current.glass,
    borderColor: Color? = null,
    iconSize: Dp = 20.dp,
    spinning: Boolean = false,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val reduced = LocalFusionReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val shape = CircleShape
    val angle = if (spinning && !reduced) {
        val transition = rememberInfiniteTransition(label = "icon-spin")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "icon-angle",
        )
        value
    } else {
        0f
    }
    Box(
        modifier
            .size(design.minTouchTarget)
            .clip(shape)
            .background(if (enabled) container else design.states.disabledContainer, shape)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier
            )
            .fusionClickable(
                design = design,
                shape = shape,
                interactionSource = interaction,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        FusionIcon(
            icon = icon,
            size = iconSize,
            tint = if (enabled) tint else design.states.disabledContent,
            modifier = Modifier.rotate(angle),
        )
    }
}

// ---------------------------------------------------------------------------
// Chips and segmented controls
// ---------------------------------------------------------------------------

@Composable
fun FusionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalFusionPalette.current.cyan,
    enabled: Boolean = true,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(design.radiusPill)
    val background by animateColorAsState(
        if (selected) accent.copy(alpha = 0.10f) else palette.glass,
        animationSpec = tween(fusionDuration(design.motion.baseMs)),
        label = "chip-bg",
    )
    val content by animateColorAsState(
        if (selected) accent else palette.textSecondary,
        animationSpec = tween(fusionDuration(design.motion.baseMs)),
        label = "chip-fg",
    )
    Box(
        modifier
            .heightIn(min = 40.dp)
            .minimumTouchTarget()
            .clip(shape)
            .background(background, shape)
            .border(
                1.dp,
                if (selected) accent else palette.borderStrong,
                shape,
            )
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .fusionInteractive(design, shape, interaction)
            .padding(horizontal = design.spacingLg, vertical = design.spacingSm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = design.type.label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Animated segmented control with radio-group semantics. The moving indicator
 * is decorative; the selected state is exposed by [selectable].
 */
@Composable
fun <T> FusionSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalFusionPalette.current.cyan,
) {
    if (options.isEmpty()) return
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val shape = RoundedCornerShape(design.radiusPill)
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.glass, shape)
            .border(1.dp, palette.borderStrong, shape)
            .padding(4.dp),
    ) {
        val itemWidth = maxWidth / options.size
        val indicator by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = tween(fusionDuration(design.motion.slowMs)),
            label = "segmented-indicator",
        )
        Box(
            Modifier
                .offset(x = indicator)
                .width(itemWidth)
                .height(48.dp)
                .clip(shape)
                .background(accent.copy(alpha = 0.18f), shape)
                .border(1.dp, accent, shape),
        )
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { option ->
                val active = option == selected
                val interaction = remember(option) { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .selectable(
                            selected = active,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        )
                        .fusionInteractive(design, shape, interaction),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = design.type.label,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) palette.textPrimary else palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Status, banners, empty states
// ---------------------------------------------------------------------------

@Composable
fun FusionStatusPill(
    label: String,
    tone: FusionTone,
    modifier: Modifier = Modifier,
    icon: FusionIcon? = null,
    pulsing: Boolean = false,
    colorOverride: Color? = null,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val color = colorOverride ?: tone.color(palette)
    val shape = RoundedCornerShape(design.radiusPill)
    val reduced = LocalFusionReducedMotion.current
    val pulse = if (pulsing && !reduced) {
        val transition = rememberInfiniteTransition(label = "status-pulse")
        val value by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "status-alpha",
        )
        value
    } else {
        1f
    }
    Row(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.10f), shape)
            .border(1.dp, color.copy(alpha = 0.40f), shape)
            .padding(horizontal = design.spacingMd, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            FusionIcon(icon, size = 13.dp, tint = color)
        } else if (pulsing) {
            Box(
                Modifier
                    .size(7.dp)
                    .alpha(pulse)
                    .background(color, CircleShape)
            )
        }
        Text(
            label.uppercase(),
            style = design.type.labelMicro,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun FusionBanner(
    tone: FusionTone,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    announce: Boolean = true,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val color = tone.color(palette)
    val icon = when (tone) {
        FusionTone.SUCCESS -> FusionIcon.CHECK
        FusionTone.DANGER, FusionTone.WARNING -> FusionIcon.ALERT
        else -> FusionIcon.INFO
    }
    val shape = RoundedCornerShape(design.radiusLg)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.12f), shape)
            .border(1.dp, color.copy(alpha = 0.34f), shape)
            .then(
                if (announce) {
                    Modifier.semantics {
                        liveRegion = if (tone == FusionTone.DANGER) {
                            LiveRegionMode.Assertive
                        } else {
                            LiveRegionMode.Polite
                        }
                    }
                } else {
                    Modifier
                }
            )
            .padding(design.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(design.spacingMd),
        verticalAlignment = Alignment.Top,
    ) {
        FusionIcon(icon, size = 20.dp, tint = color, modifier = Modifier.padding(top = 1.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = design.type.subheading, color = palette.textPrimary)
            if (message != null) {
                Text(message, style = design.type.bodySmall, color = palette.textSecondary)
            }
        }
        if (actionLabel != null && onAction != null) {
            FusionButton(
                text = actionLabel,
                onClick = onAction,
                variant = FusionButtonVariant.GHOST,
                accent = color,
            )
        }
    }
}

@Composable
fun FusionEmptyState(
    icon: FusionIcon,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = design.spacingXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(design.spacingSm),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(palette.glass, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FusionIcon(icon, size = 22.dp, tint = palette.textTertiary)
        }
        Text(title, style = design.type.subheading, color = palette.textPrimary)
        Text(
            message,
            style = design.type.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Form fields
// ---------------------------------------------------------------------------

@Composable
fun FusionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: String? = null,
    leadingIcon: FusionIcon? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 4,
    error: String? = null,
    success: String? = null,
    supportingText: String? = null,
    accent: Color = LocalFusionPalette.current.cyan,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val helper = error ?: success ?: supportingText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (error != null) this.error(error)
                if (success != null && error == null) stateDescription = success
            },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = design.type.body,
        label = { Text(label, style = design.type.label) },
        placeholder = placeholder?.let { hint ->
            { Text(hint, style = design.type.body, color = palette.textTertiary) }
        },
        leadingIcon = leadingIcon?.let { icon ->
            {
                FusionIcon(
                    icon,
                    size = 18.dp,
                    tint = when {
                        error != null -> palette.danger
                        success != null -> palette.success
                        else -> palette.textTertiary
                    },
                )
            }
        },
        trailingIcon = trailingIcon,
        isError = error != null,
        supportingText = helper?.let { text ->
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (error != null) {
                        FusionIcon(
                            FusionIcon.ALERT,
                            size = 13.dp,
                            tint = palette.danger,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    } else if (success != null) {
                        FusionIcon(
                            FusionIcon.CHECK,
                            size = 13.dp,
                            tint = palette.success,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(
                        text,
                        style = design.type.bodySmall,
                        color = when {
                            error != null -> palette.danger
                            success != null -> palette.success
                            else -> palette.textSecondary
                        },
                    )
                }
            }
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(design.radiusMd),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = palette.borderStrong,
            disabledBorderColor = palette.border,
            errorBorderColor = palette.danger,
            focusedLabelColor = accent,
            unfocusedLabelColor = palette.textSecondary,
            errorLabelColor = palette.danger,
            cursorColor = accent,
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary,
            disabledTextColor = design.states.disabledContent,
            errorTextColor = palette.textPrimary,
            focusedContainerColor = palette.surfaceSunken.copy(alpha = 0.55f),
            unfocusedContainerColor = palette.surfaceSunken.copy(alpha = 0.35f),
            errorContainerColor = palette.surfaceSunken.copy(alpha = 0.55f),
            disabledContainerColor = palette.surfaceSunken.copy(alpha = 0.25f),
        ),
    )
}

// ---------------------------------------------------------------------------
// Data display
// ---------------------------------------------------------------------------

@Composable
fun FusionKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalFusionPalette.current.textPrimary,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = design.type.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(design.spacingMd))
        Text(
            value,
            style = design.type.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
fun FusionStatTile(
    label: String,
    value: String,
    unit: String,
    icon: FusionIcon,
    tone: FusionTone,
    modifier: Modifier = Modifier,
    caption: String? = null,
    loading: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    val color = tone.color(palette)
    val shape = RoundedCornerShape(design.radiusLg)
    val interaction = remember { MutableInteractionSource() }
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(palette.surfaceRaised, shape)
        .border(1.dp, palette.border, shape)
    val surface = if (onClick != null) {
        base.fusionClickable(design, shape, interaction, onClick = onClick)
    } else {
        base
    }
    Column(
        surface.padding(design.spacingLg),
        verticalArrangement = Arrangement.spacedBy(design.spacingSm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(color.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                FusionIcon(icon, size = 15.dp, tint = color)
            }
            Spacer(Modifier.width(design.spacingSm))
            Text(
                label.uppercase(),
                style = design.type.labelMicro,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (loading) {
            FusionSkeleton(Modifier.width(86.dp).height(30.dp))
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = design.type.numeric,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    " $unit",
                    style = design.type.label,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(bottom = 3.dp, start = 2.dp),
                )
            }
        }
        if (caption != null) {
            Text(
                caption,
                style = design.type.bodySmall,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FusionSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val palette = LocalFusionPalette.current
    val reduced = LocalFusionReducedMotion.current
    val pulse = if (reduced) {
        0.8f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val value by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "skeleton-alpha",
        )
        value
    }
    Box(
        modifier
            .clip(shape)
            .alpha(pulse)
            .background(palette.skeleton, shape)
    )
}

@Composable
fun FusionSettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = design.type.subheading, color = palette.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = design.type.bodySmall, color = palette.textSecondary)
            }
        }
        Spacer(Modifier.width(design.spacingMd))
        trailing()
    }
}

/** Small inline metric used in the highlights strip. */
@Composable
fun FusionInlineMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = LocalFusionPalette.current.textPrimary,
) {
    val design = LocalFusionDesign.current
    val palette = LocalFusionPalette.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = design.type.numericSmall, color = color, maxLines = 1)
        Text(
            label,
            style = design.type.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
