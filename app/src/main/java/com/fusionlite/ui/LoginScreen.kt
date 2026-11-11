package com.fusionlite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlite.api.FusionSolarConfig

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
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var advanced by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF081226), Color(0xFF0D1D36), Color(0xFF07101F))
                )
            )
    ) {
        SolarPanelScene(
            powerFraction = 0.55f,
            showHouse = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(160.dp))
            Text(
                "FusionLite",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                "Your solar plant, live and beautifully simple.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(6.dp))

            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = NightSurface.copy(alpha = 0.96f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, NightOutline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "SIGN IN",
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        color = SolarCyan,
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = onUser,
                        label = { Text("Username") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = onPass,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { advanced = !advanced }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Server settings",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (advanced) "hide ▲" else "show ▼",
                            fontSize = 11.sp,
                            color = SolarCyan,
                        )
                    }
                    if (advanced) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = host,
                                onValueChange = onHost,
                                label = { Text("Host") },
                                singleLine = true,
                                modifier = Modifier.weight(2.4f),
                            )
                            OutlinedTextField(
                                value = port,
                                onValueChange = onPort,
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Button(
                        onClick = onLogin,
                        enabled = !loading && user.isNotBlank() && pass.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SolarGold,
                            contentColor = Color(0xFF241800),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF241800),
                            )
                            Spacer(Modifier.height(0.dp))
                        } else {
                            Text("Sign in", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    message?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Text(
                        "Credentials and session are stored only on this device " +
                            "(password encrypted with Android Keystore).",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
            Text(
                "Default server: ${FusionSolarConfig.DEFAULT_HOST}:${FusionSolarConfig.DEFAULT_PORT}",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
