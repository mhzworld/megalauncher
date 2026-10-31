package com.megalauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.megalauncher.LauncherViewModel
import com.megalauncher.R
import com.megalauncher.data.Avatar
import com.megalauncher.data.AvatarKind
import com.megalauncher.data.Profile
import kotlinx.coroutines.delay

val AvatarPresets = listOf(
    R.drawable.avatar_smile,
    R.drawable.avatar_cat,
    R.drawable.avatar_robot,
    R.drawable.avatar_alien,
    R.drawable.avatar_panda,
    R.drawable.avatar_ghost,
    R.drawable.avatar_rocket,
    R.drawable.avatar_moon,
)

private val DefaultAvatarColor = Color(0xFF3B4A63)

/** Round profile picture: custom image, preset icon, or the default initial / person icon. */
@Composable
fun AvatarImage(profile: Profile, vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val avatar = profile.avatar
    val image by produceState<ImageBitmap?>(null, profile.id, avatar.kind, avatar.version) {
        value = if (avatar.kind == AvatarKind.IMAGE) vm.loadAvatar(profile.id) else null
    }
    val preset = AvatarPresets.getOrNull(avatar.preset)
    Box(modifier.clip(CircleShape), contentAlignment = Alignment.Center) {
        when {
            avatar.kind == AvatarKind.IMAGE && image != null ->
                Image(image!!, profile.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            avatar.kind == AvatarKind.PRESET && preset != null ->
                Image(painterResource(preset), profile.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> DefaultAvatar(profile.name)
        }
    }
}

@Composable
private fun DefaultAvatar(name: String) {
    BoxWithConstraints(Modifier.fillMaxSize().background(DefaultAvatarColor), contentAlignment = Alignment.Center) {
        val initial = name.trim().firstOrNull()?.uppercaseChar()
        if (initial != null) {
            Text(initial.toString(), fontSize = (maxWidth.value * 0.45f).sp, fontWeight = FontWeight.Bold, color = Color.White)
        } else {
            TvIcon(Icons.Default.Person, "Profile", Modifier.size(maxWidth * 0.55f))
        }
    }
}

@Composable
private fun AvatarTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 96,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width((size + 24).dp)) {
        FocusSurface(
            onClick = onClick,
            modifier = modifier
                .size(size.dp)
                .then(if (selected) Modifier.border(3.dp, LauncherColors.Accent, CircleShape) else Modifier),
            shape = CircleShape,
            focusedScale = 1.1f,
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ) {
            Box(Modifier.fillMaxSize().padding(if (selected) 5.dp else 0.dp)) { content() }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) LauncherColors.Accent else Color.White,
        )
    }
}

@Composable
fun ProfileSwitcherDialog(vm: LauncherViewModel, onAdd: () -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val state by vm.profiles.collectAsStateWithLifecycle()
    val activeIndex = state.profiles.indexOfFirst { it.id == state.activeId }.coerceAtLeast(0)
    val active = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.ignoreHeldSelectKey().width(720.dp).background(LauncherColors.Panel, RoundedCornerShape(20.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Who's watching?", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(8.dp)) {
                itemsIndexed(state.profiles, key = { _, p -> p.id }) { index, profile ->
                    AvatarTile(
                        label = profile.name.ifBlank { "Profile ${index + 1}" },
                        selected = profile.id == state.activeId,
                        modifier = if (index == activeIndex) Modifier.focusRequester(active) else Modifier,
                        onClick = {
                            onDismiss()
                            vm.switchProfile(profile.id)
                        },
                    ) { AvatarImage(profile, vm, Modifier.fillMaxSize()) }
                }
                item(key = "add") {
                    AvatarTile("Add profile", selected = false, onClick = {
                        onDismiss()
                        onAdd()
                    }) {
                        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                            TvIcon(Icons.Default.Add, "Add profile", Modifier.size(40.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            PillButton("Manage profiles") {
                onDismiss()
                onManage()
            }
        }
        LaunchedEffect(Unit) {
            delay(80)
            active.safeRequestFocus()
        }
    }
}

/** Default (initial), the preset icons, or a custom picture for the active profile. */
@Composable
fun AvatarPickerDialog(
    vm: LauncherViewModel,
    onBrowse: () -> Unit,
    onSystemPicker: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by vm.profiles.collectAsStateWithLifecycle()
    val profile = state.active
    val avatar = profile.avatar
    val first = remember { FocusRequester() }
    // Index -1 is the default avatar, then the presets.
    val options = listOf(-1) + AvatarPresets.indices
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.ignoreHeldSelectKey().width(720.dp).background(LauncherColors.Panel, RoundedCornerShape(20.dp)).padding(24.dp),
        ) {
            Text("Profile picture", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            options.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    row.forEach { option ->
                        val isDefault = option < 0
                        val selected = if (isDefault) avatar.kind == AvatarKind.DEFAULT else avatar.kind == AvatarKind.PRESET && avatar.preset == option
                        AvatarTile(
                            label = if (isDefault) "Default" else "",
                            selected = selected,
                            size = 80,
                            modifier = if (isDefault) Modifier.focusRequester(first) else Modifier,
                            onClick = {
                                if (isDefault) vm.setAvatarDefault() else vm.setAvatarPreset(option)
                                onDismiss()
                            },
                        ) {
                            val shown = if (isDefault) profile.copy(avatar = Avatar()) else profile.copy(avatar = Avatar(AvatarKind.PRESET, option))
                            AvatarImage(shown, vm, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            if (avatar.kind == AvatarKind.IMAGE) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(profile, vm, Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Using your own picture", fontSize = 14.sp, color = LauncherColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Browse images on this TV") {
                    onDismiss()
                    onBrowse()
                }
                PillButton("Use system file picker") {
                    onDismiss()
                    onSystemPicker()
                }
                PillButton("Cancel", onClick = onDismiss)
            }
        }
        LaunchedEffect(Unit) {
            delay(80)
            first.safeRequestFocus()
        }
    }
}
