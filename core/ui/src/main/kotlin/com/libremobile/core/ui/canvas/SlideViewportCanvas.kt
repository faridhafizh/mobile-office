package com.libremobile.core.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.libremobile.core.model.impress.PresentationDocument
import com.libremobile.core.model.impress.Slide

@Composable
fun SlideViewportCanvas(
    document: PresentationDocument,
    activeSlideIndex: Int,
    onSlideSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSlide = document.getSlide(activeSlideIndex) ?: Slide()
    val textMeasurer = rememberTextMeasurer()

    val textColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxSize()) {
        // Main Viewport: Active Slide View
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(24.dp)
        ) {
            // 16:9 Aspect Ratio Slide Canvas
            Surface(
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(6.dp),
                color = Color(activeSlide.backgroundColor.toArgbLong()),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .shadow(8.dp, RoundedCornerShape(6.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scaleX = size.width / document.slideWidthPt
                    val scaleY = size.height / document.slideHeightPt

                    for (shape in activeSlide.shapes) {
                        val left = shape.bounds.left * scaleX
                        val top = shape.bounds.top * scaleY
                        val width = shape.bounds.width * scaleX
                        val height = shape.bounds.height * scaleY

                        // Shape Fill
                        val fillColor = shape.fill.color
                        if (fillColor != null) {
                            drawRect(
                                color = Color(fillColor.toArgbLong()),
                                topLeft = Offset(left, top),
                                size = Size(width, height)
                            )
                        }

                        // Shape Stroke
                        val strokeColor = shape.stroke.color
                        if (strokeColor != null && shape.stroke.widthPt > 0f) {
                            drawRect(
                                color = Color(strokeColor.toArgbLong()),
                                topLeft = Offset(left, top),
                                size = Size(width, height),
                                style = Stroke(width = shape.stroke.widthPt * scaleX)
                            )
                        }

                        // Shape Text
                        val tf = shape.textFrame
                        if (tf != null) {
                            var textY = top + 8f
                            for (p in tf.paragraphs) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = p.plainText,
                                    style = TextStyle(
                                        fontSize = (16f * scaleY).sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor
                                    ),
                                    topLeft = Offset(left + 8f, textY),
                                    size = Size(width - 16f, height - 16f)
                                )
                                textY += 24f * scaleY
                            }
                        }
                    }
                }
            }
        }

        // Thumbnails Strip
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth().height(90.dp)
        ) {
            LazyRow(
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(document.slides) { idx, slide ->
                    val isSelected = (idx == activeSlideIndex)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) else null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .width(100.dp)
                            .height(65.dp)
                            .clickable { onSlideSelect(idx) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Slide ${idx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
