package com.nicolasguillen.taletime.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nicolasguillen.taletime.i18n.LocalStrings
import com.nicolasguillen.taletime.resources.Res

/** The three kinds of story picture, with the static placeholder each shows until its image is delivered. */
enum class ImageKind(
    val placeholder: String,
    val aspect: Float,
) {
    PAGE("files/placeholders/page.svg", 4f / 3f),
    CARD("files/placeholders/card.svg", 1f),
    COVER("files/placeholders/cover.svg", 3f / 4f),
}

/**
 * A story picture. [uri] null means not delivered yet: the static placeholder for [kind] (content/placeholders/)
 * shows instead, and also if the file can't be read. [onLoaded] is called once there's something to show (the
 * picture or its placeholder).
 */
@Composable
fun StoryImage(
    uri: String?,
    kind: ImageKind,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onLoaded: () -> Unit = {},
) {
    var failed by remember(uri) { mutableStateOf(false) }
    val model = if (uri == null || failed) Res.getUri(kind.placeholder) else uri
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.background(TaleColors.PictureMat),
        contentScale = contentScale,
        onSuccess = { onLoaded() },
        onError = { if (uri != null) failed = true },
    )
}

/**
 * Squishes a little while it's pressed, so a small finger feels the tap land. Pass the same [interaction] to the
 * clickable.
 */
@Composable
fun Modifier.squish(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "squish")
    return scale(scale)
}

/** A big round icon button: one finger is enough. */
@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    container: Color = TaleColors.NightBlue,
    content: Color = TaleColors.Cream,
    pulse: Boolean = false,
    shadow: Dp = 4.dp,
) {
    val scale =
        if (pulse) {
            val pulsing = rememberInfiniteTransition(label = "pulse")
            pulsing
                .animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "pulse",
                ).value
        } else {
            1f
        }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .scale(scale)
            .squish(interaction)
            .size(size)
            .shadow(shadow, CircleShape)
            .clip(CircleShape)
            .background(container)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * Back to the shelf: the same sunny house button in the top corner of every screen. A [quiet] one is small and
 * see-through, for the reader, where it sits on the picture (docs/page-turn.md §7).
 */
@Composable
fun HomeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    quiet: Boolean = false,
) {
    RoundButton(
        TaleIcons.Home,
        contentDescription = LocalStrings.current.backToShelf,
        onClick = onClick,
        modifier = modifier,
        // 44 dp: the smallest comfortable touch target on both platforms.
        size = if (quiet) 44.dp else 60.dp,
        container = if (quiet) TaleColors.Cream.copy(alpha = 0.78f) else TaleColors.LanternGold,
        content = if (quiet) TaleColors.InkSoft else TaleColors.Ink,
        shadow = if (quiet) 1.dp else 4.dp,
    )
}

/**
 * A labeled button, mostly for grown-ups (the cover, the parents area, the ending screen). A [large] one is a screen's
 * main action, at its bottom.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val container = if (primary) TaleColors.NightBlue else TaleColors.Cream
    val content = if (primary) TaleColors.Cream else TaleColors.Ink
    Row(
        modifier
            .heightIn(min = if (large) 68.dp else 56.dp)
            .shadow(if (primary && enabled) 3.dp else 0.dp, shape)
            .clip(shape)
            .background(if (enabled) container else TaleColors.Line)
            .border(if (primary) 0.dp else 2.dp, TaleColors.Line, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(if (large) 28.dp else 24.dp))
        Text(
            text,
            style = if (large) Tale.type.label.copy(fontSize = 22.sp) else Tale.type.label,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}

/** One tab of [PillTabs]: a word and a picture, so a child who can't read it yet still knows it. */
data class PillTab(
    val label: String,
    val icon: ImageVector,
)

/** Big tabs side by side in a pill, the [selected] one filled in (-1: none yet). */
@Composable
fun PillTabs(
    tabs: List<PillTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(TaleColors.Cream)
            .border(2.dp, TaleColors.Line, shape)
            .padding(5.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val picked = index == selected
            val container by animateColorAsState(if (picked) TaleColors.NightBlue else TaleColors.Cream, label = "tab")
            val content by animateColorAsState(if (picked) TaleColors.Cream else TaleColors.Ink, label = "tab")
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(shape)
                    .background(container)
                    .selectable(selected = picked, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon(tab.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
                Text(tab.label, style = Tale.type.label, color = content, maxLines = 1)
            }
        }
    }
}

/**
 * An ending's badge: its illustration cropped to a circle once found, a "?" until then, so a child can see how many
 * are left to find.
 */
@Composable
fun EndingBadge(
    imageUri: String?,
    found: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (found) TaleColors.PictureMat else TaleColors.Cream)
            .border(3.dp, if (found) TaleColors.LanternGold else TaleColors.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (found) {
            StoryImage(imageUri, ImageKind.PAGE, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Text("?", style = Tale.type.heading.copy(fontSize = (size.value * 0.45f).sp), color = TaleColors.InkSoft)
        }
    }
}
