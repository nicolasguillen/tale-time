package com.nicolasguillen.taletime.designsystem

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
 * shows instead, and also if the file can't be read.
 */
@Composable
fun StoryImage(
    uri: String?,
    kind: ImageKind,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var failed by remember(uri) { mutableStateOf(false) }
    val model = if (uri == null || failed) Res.getUri(kind.placeholder) else uri
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.background(TaleColors.PictureMat),
        contentScale = contentScale,
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
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(container)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

/** Back to the shelf: the same sunny house button in the top corner of every screen. */
@Composable
fun HomeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoundButton(
        TaleIcons.Home,
        contentDescription = LocalStrings.current.backToShelf,
        onClick = onClick,
        modifier = modifier,
        size = 60.dp,
        container = TaleColors.LanternGold,
        content = TaleColors.Ink,
    )
}

/** A labeled button, mostly for grown-ups (the cover, the parents area, the ending screen). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    val container = if (primary) TaleColors.NightBlue else TaleColors.Cream
    val content = if (primary) TaleColors.Cream else TaleColors.Ink
    Row(
        modifier
            .heightIn(min = 56.dp)
            .shadow(if (primary && enabled) 3.dp else 0.dp, shape)
            .clip(shape)
            .background(if (enabled) container else TaleColors.Line)
            .border(if (primary) 0.dp else 2.dp, TaleColors.Line, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Text(text, style = Tale.type.label, color = content, textAlign = TextAlign.Center)
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
