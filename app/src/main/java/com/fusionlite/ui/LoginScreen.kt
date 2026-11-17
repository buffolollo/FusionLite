package com.fusionlite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlite.api.FusionSolarConfig
import com.fusionlite.data.ThemeMode

/**
 * Sign-in screen.
 *
 * Accessibility decisions:
 * - the submit button stays enabled (unless a request is in flight) so a blank
 *   form can explain itself with inline errors instead of silently refusing;
 * - a failed sign-in renders an assertive live-region banner and moves focus
 *   to it, so the error is announced and reachable by keyboard;
 * - password has a labelled show/hide toggle with a 48dp target;
 * - server settings are an explicit switch, not an unlabeled tap target.
 */
@Composable
fun LoginScreen(
    user: String,
    onUser: (String) -> Unit,
    pass: String,
    onPass: (String) -> Unit,
    host: String,
    onHost: (String) -> Unit,
    port: String,
    onPort: (String) -> Unit,
    loading: Boolean,
    message: String?,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var advanced by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    val focusManager = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    val userFocus = remember { FocusRequester() }
    val hostFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    val errorInteraction = remember { MutableInteractionSource() }

    val userError = if (attempted && user.isBlank()) "Enter your FusionSolar username." else null
    val passError = if (attempted && pass.isBlank()) "Enter your password." else null
    val hostError = if (advanced && host.isBlank()) "Enter the server host." else null

    fun submit() {
        attempted = true
        when {
            user.isBlank() -> {
                userFocus.requestFocus()
                return
            }
            pass.isBlank() -> {
                passwordFocus.requestFocus()
                return
            }
            advanced && host.isBlank() -> {
                hostFocus.requestFocus()
                return
            }
        }
        focusManager.clearFocus()
        onLogin()
    }

    LaunchedEffect(message) {
        if (message != null) {
            runCatching { errorFocus.requestFocus() }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(palette.loginGradient)),
    ) {
        val gutter = design.gutterFor(maxWidth)
        SolarPanelScene(
            powerFraction = 0.55f,
            showHouse = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = gutter, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(120.dp))
            Column(
                Modifier.widthIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(design.spacingMd),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(palette.gold.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        FusionIcon(FusionIcon.SUN, size = 24.dp, tint = palette.gold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "FUSIONLITE",
                        style = design.type.labelMicro,
                        color = palette.cyan,
                        letterSpacing = 2.4.sp,
                    )
                }
                Text(
                    "Your solar plant,\nlive and simple.",
                    style = design.type.displaySmall,
                    color = palette.loginTitle,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Sign in with the same account you use in the FusionSolar app. " +
                        "The session stays on this device.",
                    style = design.type.body,
                    color = palette.loginSubtitle,
                )

                FusionCard(
                    padding = design.spacingXl,
                    accent = palette.borderStrong,
                ) {
                    Text(
                        "SIGN IN",
                        style = design.type.labelMicro,
                        color = palette.cyan,
                    )

                    if (message != null) {
                        FusionBanner(
                            tone = FusionTone.DANGER,
                            title = "Could not sign in",
                            message = message,
                            modifier = Modifier
                                .focusRequester(errorFocus)
                                .focusable(interactionSource = errorInteraction)
                                .fusionInteractive(
                                    design = design,
                                    shape = RoundedCornerShape(design.radiusLg),
                                    interactionSource = errorInteraction,
                                ),
                        )
                    }

                    FusionTextField(
                        value = user,
                        onValueChange = onUser,
                        label = "Username",
                        placeholder = "name@example.com",
                        leadingIcon = FusionIcon.USER,
                        enabled = !loading,
                        error = userError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { passwordFocus.requestFocus() },
                        ),
                        accent = palette.cyan,
                        modifier = Modifier.focusRequester(userFocus),
                    )

                    FusionTextField(
                        value = pass,
                        onValueChange = onPass,
                        label = "Password",
                        leadingIcon = FusionIcon.LOCK,
                        enabled = !loading,
                        error = passError,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            FusionIconButton(
                                icon = if (showPassword) FusionIcon.EYE_OFF else FusionIcon.EYE,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                onClick = { showPassword = !showPassword },
                                container = Color.Transparent,
                                tint = palette.textTertiary,
                                iconSize = 18.dp,
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                submit()
                            },
                        ),
                        accent = palette.cyan,
                        modifier = Modifier.focusRequester(passwordFocus),
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = advanced,
                                role = Role.Switch,
                                onValueChange = { advanced = it },
                            )
                            .semantics {
                                stateDescription = if (advanced) {
                                    "Server settings expanded"
                                } else {
                                    "Server settings collapsed"
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "Server settings",
                                style = design.type.label,
                                color = palette.textPrimary,
                            )
                            Text(
                                "${FusionSolarConfig.DEFAULT_HOST}",
                                style = design.type.bodySmall,
                                color = palette.textTertiary,
                            )
                        }
                        FusionIcon(
                            if (advanced) FusionIcon.CHEVRON_UP else FusionIcon.CHEVRON_DOWN,
                            size = 18.dp,
                            tint = palette.cyan,
                        )
                    }

                    if (advanced) {
                        Row(horizontalArrangement = Arrangement.spacedBy(design.spacingSm)) {
                            FusionTextField(
                                value = host,
                                onValueChange = onHost,
                                label = "Host",
                                enabled = !loading,
                                error = hostError,
                                leadingIcon = FusionIcon.SERVER,
                                modifier = Modifier
                                    .weight(2.2f)
                                    .focusRequester(hostFocus),
                                accent = palette.cyan,
                            )
                            FusionTextField(
                                value = port,
                                onValueChange = onPort,
                                label = "Port",
                                enabled = !loading,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                modifier = Modifier.weight(1f),
                                accent = palette.cyan,
                            )
                        }
                    }

                    FusionButton(
                        text = if (loading) "Signing in…" else "Sign in",
                        onClick = { submit() },
                        loading = loading,
                        enabled = !loading,
                        fillWidth = true,
                        icon = FusionIcon.LOCK,
                    )

                    Row(verticalAlignment = Alignment.Top) {
                        FusionIcon(
                            FusionIcon.LOCK,
                            size = 13.dp,
                            tint = palette.textTertiary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            "Credentials never leave this device except in the login request. " +
                                "The password is encrypted with the Android Keystore.",
                            style = design.type.bodySmall,
                            color = palette.textTertiary,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                Text(
                    "Default server ${FusionSolarConfig.DEFAULT_HOST}:${FusionSolarConfig.DEFAULT_PORT}",
                    style = design.type.bodySmall,
                    color = palette.textTertiary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            ThemeToggle(mode = themeMode, onMode = onThemeMode)
        }
    }
}
