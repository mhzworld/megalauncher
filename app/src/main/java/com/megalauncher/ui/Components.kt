package com.megalauncher.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.megalauncher.LauncherViewModel
import com.megalauncher.data.AppInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun FocusRequester.safeRequestFocus() {
    runCatching { requestFocus() }
}

private val SelectKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * For dialogs: swallows the OK key that is still held from the long-press that opened the dialog
 * (its auto-repeats and release), so it doesn't click the first item.
 */
@Composable
fun Modifier.ignoreHeldSelectKey(): Modifier {
    val state = remember { BooleanArray(1) } // [0] = ready: a fresh OK press is allowed through
    return onPreviewKeyEvent { event ->
        if (state[0] || event.key !in SelectKeys) return@onPreviewKeyEvent false
        when {
            event.type == KeyEventType.KeyUp -> {
                state[0] = true
                true
            }
            event.nativeKeyEvent.repeatCount > 0 -> true
            else -> {
                state[0] = true
                false
            }
        }
    }
}

/** Width of a favourite (76dp circle + room for the focus scale). */
private val ROUND_ITEM_WIDTH = 84.dp

// Critically damped and a bit softer than the default, so rapid D-pad presses glide instead of jerk.
private val FocusScrollSpec: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 350f)

/** Vertical lists: keep the focused item near the top (like Google TV) so the page moves steadily. */
val PivotBringIntoViewSpec: BringIntoViewSpec = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = FocusScrollSpec

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - containerSize * 0.15f
}

/** Horizontal rows: scroll only when the focused item (plus room for its focus scale) is near an edge. */
val EdgeBringIntoViewSpec: BringIntoViewSpec = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = FocusScrollSpec

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * 0.06f
        val end = offset + size
        return when {
            size > containerSize - 2 * margin -> offset - margin
            offset < margin -> offset - margin
            end > containerSize - margin -> end - (containerSize - margin)
            else -> 0f
        }
    }
}

/** While [active], D-pad left/right reorders the item and OK/Back finishes. */
@Composable
fun Modifier.movable(active: Boolean, onMove: (Int) -> Unit, onDone: () -> Unit): Modifier {
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(active) {
        if (active) {
            delay(80)
            focusRequester.safeRequestFocus()
        }
    }
    return this
        .focusRequester(focusRequester)
        .bringIntoViewRequester(bringIntoView)
        .onPreviewKeyEvent { event ->
            if (!active) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft, Key.DirectionRight -> {
                    if (event.type == KeyEventType.KeyDown) {
                        onMove(if (event.key == Key.DirectionLeft) -1 else 1)
                        scope.launch {
                            delay(50)
                            bringIntoView.bringIntoView()
                        }
                    }
                    true
                }
                Key.DirectionUp, Key.DirectionDown -> true
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape -> {
                    if (event.type == KeyEventType.KeyUp) onDone()
                    true
                }
                else -> false
            }
        }
}

@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    highlighted: Boolean = false,
    focusedScale: Float = 1.08f,
    showFocusBorder: Boolean = true,
    containerColor: Color = LauncherColors.Card,
    focusedContainerColor: Color = LauncherColors.CardFocused,
    focusedContentColor: Color = Color.White,
    content: @Composable BoxScope.() -> Unit,
) {
    val stroke = Border(BorderStroke(3.dp, if (highlighted) LauncherColors.Moving else Color.White), shape = shape)
    val focusStroke = if (showFocusBorder || highlighted) stroke else Border.None
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = Color.White,
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = focusedContentColor,
            pressedContainerColor = focusedContainerColor,
            pressedContentColor = focusedContentColor,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (highlighted) stroke else Border.None,
            focusedBorder = focusStroke,
            pressedBorder = focusStroke,
        ),
        content = content,
    )
}

@Composable
fun TvIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = LocalContentColor.current,
) {
    Image(icon, contentDescription, modifier, colorFilter = ColorFilter.tint(tint))
}

@Composable
fun PillButton(
    text: String?,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        focusedScale = 1.05f,
        showFocusBorder = false,
        containerColor = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
        focusedContainerColor = Color.White,
        focusedContentColor = Color.Black,
    ) {
        Row(
            Modifier.padding(horizontal = if (text == null) 12.dp else 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { TvIcon(it, text) }
            if (icon != null && text != null) Spacer(Modifier.width(8.dp))
            text?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
fun MenuButton(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        focusedScale = 1.02f,
        showFocusBorder = false,
        containerColor = Color.White.copy(alpha = 0.05f),
        focusedContainerColor = Color.White,
        focusedContentColor = Color.Black,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (selected) TvIcon(Icons.Default.Check, "Selected", Modifier.size(20.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 8.dp, top = 8.dp),
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = LauncherColors.TextSecondary,
    )
}

@Composable
fun RoundAppIcon(
    app: AppInfo,
    moving: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(ROUND_ITEM_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.size(76.dp).onFocusChanged { focused = it.isFocused },
            shape = CircleShape,
            highlighted = moving,
            focusedScale = 1.14f,
        ) {
            Image(
                app.icon,
                app.label,
                Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            app.label,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (focused || moving) Color.White else Color.Transparent,
        )
    }
}

@Composable
fun RoundAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(ROUND_ITEM_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(
            onClick = onClick,
            modifier = modifier.size(76.dp).onFocusChanged { focused = it.isFocused },
            shape = CircleShape,
            focusedScale = 1.14f,
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ) {
            TvIcon(icon, label, Modifier.size(30.dp).align(Alignment.Center))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = 12.sp, maxLines = 1, color = if (focused) Color.White else Color.Transparent)
    }
}

@Composable
fun BannerCard(
    app: AppInfo,
    banner: ImageBitmap?,
    moving: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(192.dp)) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.size(192.dp, 108.dp).onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(10.dp),
            highlighted = moving,
        ) {
            if (banner != null) {
                Image(banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(app.icon, null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (focused || moving) Color.White else Color.Transparent,
        )
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(108.dp),
        shape = RoundedCornerShape(10.dp),
        containerColor = Color.White.copy(alpha = 0.06f),
        focusedContainerColor = Color.White,
        focusedContentColor = Color.Black,
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TvIcon(icon, null, Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
fun AppTile(
    app: AppInfo,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(140.dp, 120.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(app.icon, null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.height(8.dp))
            Text(app.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        if (checked) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(LauncherColors.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                TvIcon(Icons.Default.Check, "Added", Modifier.size(16.dp), tint = Color.Black)
            }
        }
    }
}

@Composable
fun AppGrid(
    apps: List<AppInfo>,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
    isChecked: (AppInfo) -> Boolean = { false },
    onClick: (AppInfo) -> Unit,
    onLongClick: ((AppInfo) -> Unit)? = null,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides PivotBringIntoViewSpec) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(152.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                AppTile(
                    app = app,
                    modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                    checked = isChecked(app),
                    onClick = { onClick(app) },
                    onLongClick = onLongClick?.let { handler -> { handler(app) } },
                )
            }
        }
    }
}

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    secret: Boolean = false,
    onDone: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
        cursorBrush = SolidColor(Color.White),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(LauncherColors.Card, RoundedCornerShape(10.dp))
            .border(2.dp, if (focused) Color.White else Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = LauncherColors.TextSecondary, fontSize = 18.sp)
                inner()
            }
        },
    )
}

data class MenuAction(val label: String, val onClick: () -> Unit)

@Composable
fun ActionMenu(title: String, icon: ImageBitmap? = null, actions: List<MenuAction>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .ignoreHeldSelectKey()
                .width(400.dp)
                .background(LauncherColors.Panel, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Image(icon, null, Modifier.size(40.dp).clip(CircleShape))
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            actions.forEachIndexed { index, action ->
                MenuButton(
                    action.label,
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                ) {
                    onDismiss()
                    action.onClick()
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        LaunchedEffect(Unit) {
            delay(50)
            first.safeRequestFocus()
        }
    }
}

@Composable
fun InfoDialog(
    title: String,
    message: String,
    code: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val ok = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.ignoreHeldSelectKey().width(560.dp).background(LauncherColors.Panel, RoundedCornerShape(20.dp)).padding(24.dp),
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(message, fontSize = 15.sp, color = LauncherColors.TextSecondary)
            if (code != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    code,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(8.dp)).padding(12.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (actionLabel != null) {
                    PillButton(actionLabel, modifier = Modifier.focusRequester(ok)) {
                        onDismiss()
                        onAction()
                    }
                    PillButton("Close", onClick = onDismiss)
                } else {
                    PillButton("OK", modifier = Modifier.focusRequester(ok), onClick = onDismiss)
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(50)
            ok.safeRequestFocus()
        }
    }
}

@Composable
fun FullDialog(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .ignoreHeldSelectKey()
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f)
                .background(LauncherColors.Panel, RoundedCornerShape(20.dp)),
            content = content,
        )
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String = "",
    secret: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    val submit = {
        onConfirm(text)
        onDismiss()
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.ignoreHeldSelectKey().width(520.dp).background(LauncherColors.Panel, RoundedCornerShape(20.dp)).padding(24.dp),
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            TvTextField(
                text,
                { text = it },
                Modifier.fillMaxWidth().focusRequester(field),
                placeholder = placeholder,
                secret = secret,
                onDone = submit,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Save", onClick = submit)
                PillButton("Cancel", onClick = onDismiss)
            }
        }
        LaunchedEffect(Unit) {
            delay(50)
            field.safeRequestFocus()
        }
    }
}

@Composable
fun AppPickerDialog(
    title: String,
    apps: List<AppInfo>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    FullDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                PillButton("Done", onClick = onDismiss)
            }
            Text("Select apps to add or remove", fontSize = 13.sp, color = LauncherColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            AppGrid(
                apps = apps,
                firstFocus = first,
                isChecked = { it.packageName in selected },
                onClick = { onToggle(it.packageName) },
            )
        }
        LaunchedEffect(Unit) {
            delay(80)
            first.safeRequestFocus()
        }
    }
}

/** Open / store / info / uninstall actions shared by every app context menu. */
fun commonAppActions(vm: LauncherViewModel, app: AppInfo): List<MenuAction> = buildList {
    add(MenuAction("View in Play Store") { vm.openInStore(app.packageName) })
    add(MenuAction("App info") { vm.openAppInfo(app.packageName) })
    if (!app.isSystem) add(MenuAction("Uninstall") { vm.uninstall(app.packageName) })
}
