package com.example.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.design.ZipSlideTheme

@Composable
fun OnboardingScreen(
    onPermissionGranted: () -> Unit,
    onChooseFolderInstead: (Uri) -> Unit
) {
    val context = LocalContext.current
    var hasAttemptedPermission by remember { mutableStateOf(false) }

    fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    LaunchedEffect(Unit) {
        if (checkStoragePermission()) {
            onPermissionGranted()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onChooseFolderInstead(uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZipSlideTheme.colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = ZipSlideTheme.spacing.screenGutter)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = ZipSlideTheme.spacing.sectionGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 96dp glyph: stacked-zip + image glyph, accent-tinted
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(ZipSlideTheme.colors.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    tint = ZipSlideTheme.colors.accent,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.sectionGap))

            Text(
                text = "Your slideshows,\nfinally visible.",
                style = ZipSlideTheme.typography.display,
                color = ZipSlideTheme.colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s16))

            Text(
                text = "ZipSlide reads 1.jpg from inside each zip as its thumbnail — so you instantly recognize every slideshow by its true cover.",
                style = ZipSlideTheme.typography.body,
                color = ZipSlideTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f)
            )

            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s48))

            // Primary pill button: Grant access (accent fill, black label)
            Button(
                onClick = {
                    hasAttemptedPermission = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    } else {
                        onPermissionGranted()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZipSlideTheme.colors.accent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(ZipSlideTheme.radii.full),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("grant_access_button")
            ) {
                Text(
                    text = if (hasAttemptedPermission) "Open settings" else "Grant access",
                    style = ZipSlideTheme.typography.bodyM.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s12))

            // Text button below: Choose a folder instead
            TextButton(
                onClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("choose_folder_button")
            ) {
                Text(
                    text = "Choose a folder instead",
                    style = ZipSlideTheme.typography.bodyM,
                    color = ZipSlideTheme.colors.textSecondary
                )
            }
        }

        // Footnote (Caption, textTertiary): "Files stay on your device. Nothing is uploaded."
        Text(
            text = "Files stay on your device. Nothing is uploaded.",
            style = ZipSlideTheme.typography.caption,
            color = ZipSlideTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = ZipSlideTheme.spacing.s16)
        )
    }
}
