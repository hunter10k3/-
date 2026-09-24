package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentFormat

@Composable
fun FormatBadge(
    format: DocumentFormat,
    modifier: Modifier = Modifier,
    isSmall: Boolean = false
) {
    val bgColor = Color(format.badgeColor)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor.copy(alpha = 0.15f))
            .padding(horizontal = if (isSmall) 6.dp else 8.dp, vertical = if (isSmall) 2.dp else 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = format.displayName,
            color = bgColor,
            fontSize = if (isSmall) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
