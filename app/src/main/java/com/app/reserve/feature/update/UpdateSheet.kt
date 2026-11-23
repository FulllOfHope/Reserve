package com.app.reserve.feature.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.app.reserve.R
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.radius_lg
import com.app.reserve.core.designsystem.radius_md
import com.app.reserve.core.designsystem.spacing_lg
import com.app.reserve.core.designsystem.spacing_md
import com.app.reserve.core.designsystem.spacing_sm
import com.app.reserve.core.designsystem.spacing_xl
import com.app.reserve.core.designsystem.spacing_xs
import com.app.reserve.core.designsystem.spacing_xxl
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.update.UpdateInfo

/** A deliberately quiet, designed sheet — never a raw AlertDialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    update: UpdateInfo,
    onUpdate: () -> Unit,
    onSnooze: () -> Unit,
    onSkipVersion: () -> Unit
) {
    val colors = LocalReserveColors.current
    ModalBottomSheet(
        onDismissRequest = onSnooze,
        containerColor = colors.surface,
        contentColor = colors.textBase,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        contentWindowInsets = { WindowInsets.ime },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing_xxl)
                .navigationBarsPadding()
                .padding(bottom = spacing_lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(radius_md))
                        .background(colors.fillLight)
                        .border(1.dp, colors.strokeFaint, RoundedCornerShape(radius_md)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.reverse_glass),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.width(spacing_md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RESERVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLighter
                    )
                    Text(
                        text = "Update available",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textBase
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing_md))
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = colors.fillLight,
                border = BorderStroke(1.dp, colors.strokeFaint)
            ) {
                Text(
                    text = "v${update.versionName}",
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = colors.textBase,
                    modifier = Modifier.padding(horizontal = spacing_md, vertical = spacing_xs)
                )
            }

            Spacer(modifier = Modifier.height(spacing_lg))
            Surface(
                shape = RoundedCornerShape(radius_lg),
                color = colors.fillBase,
                border = BorderStroke(1.dp, colors.strokeFaint)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(spacing_lg)
                ) {
                    Text(
                        text = "RELEASE NOTES",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLighter
                    )
                    Spacer(modifier = Modifier.height(spacing_sm))
                    Text(
                        text = update.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing_md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = colors.textLighter,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(spacing_xs))
                Text(
                    text = "Checked with GitHub. No analytics or tracking.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLighter
                )
            }

            Spacer(modifier = Modifier.height(spacing_xl))
            Button(
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(radius_md),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryDefault,
                    contentColor = colors.textReverse
                )
            ) {
                Text("Update", style = MaterialTheme.typography.titleMedium)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textLight)
                ) {
                    Text("Remind me in a week")
                }
                TextButton(
                    onClick = onSkipVersion,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textLight)
                ) {
                    Text("Skip this version")
                }
            }
        }
    }
}
