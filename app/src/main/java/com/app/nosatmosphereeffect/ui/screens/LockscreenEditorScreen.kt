package com.app.nosatmosphereeffect.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.helper.*
import com.app.nosatmosphereeffect.ui.components.ClockGlassPreview
import com.app.nosatmosphereeffect.ui.components.SettingSwitchRow
import com.app.nosatmosphereeffect.ui.model.EffectCatalog
import com.app.nosatmosphereeffect.ui.model.EffectItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

enum class EditorActiveSheet {
    NONE,
    STYLE,
    CLOCK,
    DEPTH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockscreenEditorScreen(
    wallpaperBitmap: Bitmap?,
    maskBitmap: Bitmap?,
    isSegmenting: Boolean,
    maskFailureReason: String?,
    activeEffectId: String,
    onEffectChange: (String) -> Unit,
    isAtmosphereGlassEnabled: Boolean,
    onAtmosphereGlassEnabledChange: (Boolean) -> Unit,
    clockStyle: ClockStyle,
    onClockStyleChange: (ClockStyle) -> Unit,
    customFontId: String?,
    onCustomFontIdChange: (String?) -> Unit,
    customFonts: List<CustomFontInfo>,
    onImportFontClick: () -> Unit,
    clockSize: Float,
    onClockSizeChange: (Float) -> Unit,
    clockOpacity: Float,
    onClockOpacityChange: (Float) -> Unit,
    clockColor: Int,
    onClockColorChange: (Int) -> Unit,
    depthEnabled: Boolean,
    onDepthEnabledChange: (Boolean) -> Unit,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onApply: () -> Unit
) {
    val context = LocalContext.current
    var activeSheet by remember { mutableStateOf(EditorActiveSheet.NONE) }
    var autoColor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        autoColor = withContext(Dispatchers.IO) { ClockPalette.autoColorFor(context) }
    }

    val resolvedColor = ClockPalette.resolve(clockColor, autoColor)

    // Clock Face Renderer for the live preview
    val faceRenderer = remember(context) { ClockFaceRenderer(context) }
    var faceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var faceRevision by remember { mutableIntStateOf(0) }
    var faceBox by remember { mutableStateOf(ClockFaceBox.IDENTITY) }

    val customTypeface = remember(customFontId, customFonts) {
        customFontId?.let { CustomFontManager.getTypeface(context, it) }
    }

    DisposableEffect(faceRenderer) {
        onDispose {
            synchronized(faceRenderer) { faceRenderer.release() }
        }
    }

    LaunchedEffect(clockStyle, customTypeface, customFontId, resolvedColor, clockSize) {
        withContext(Dispatchers.Default) {
            synchronized(faceRenderer) {
                faceRenderer.customFontId = customFontId
                faceRenderer.customTypeface = customTypeface
                faceRenderer.style = clockStyle
                faceRenderer.color = resolvedColor
                faceRenderer.showDate = true
                faceRenderer.animateDigits = false
                faceRenderer.clockPlacement = ClockPlacement(
                    centerX = AtmosphereClockPolicy.DEFAULT_CENTER_X,
                    top = AtmosphereClockPolicy.DEFAULT_TOP,
                    height = clockSize,
                    widthScale = 1.0f
                )
                val measured = runCatching {
                    faceRenderer.measureFace(System.currentTimeMillis())
                }.getOrNull()
                if (measured != null) {
                    faceBox = measured
                }
                val rendered = runCatching {
                    faceRenderer.render(
                        nowMillis = System.currentTimeMillis(),
                        uptimeMs = SystemClock.uptimeMillis()
                    )?.copy(Bitmap.Config.ARGB_8888, false)
                }.getOrNull()
                if (rendered != null) {
                    faceBitmap = rendered
                    faceRevision++
                }
            }
        }
    }

    val textureBox = ClockBoxPlacement.textureBox(
        placement = ClockPlacement(
            centerX = AtmosphereClockPolicy.DEFAULT_CENTER_X,
            top = AtmosphereClockPolicy.DEFAULT_TOP,
            height = clockSize,
            widthScale = 1.0f
        ),
        face = faceBox,
        screenAspect = 0.46f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Interactive Preview Canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (activeSheet != EditorActiveSheet.NONE) {
                        activeSheet = EditorActiveSheet.NONE
                    }
                }
        ) {
            // Clock Glass Shader Rendered Preview
            ClockGlassPreview(
                wallpaper = wallpaperBitmap,
                face = faceBitmap,
                box = textureBox,
                opacity = clockOpacity,
                mode = ClockOverlayState(
                    styleId = clockStyle.id,
                    frost = 0f
                ).glassMode,
                faceRevision = faceRevision,
                modifier = Modifier.fillMaxSize()
            )

            // If depth effect is enabled and a subject mask is present, composite the subject back over the clock!
            if (depthEnabled && maskBitmap != null && wallpaperBitmap != null && !maskBitmap.isRecycled && !wallpaperBitmap.isRecycled) {
                SubjectDepthOverlay(
                    wallpaper = wallpaperBitmap,
                    mask = maskBitmap,
                    opacity = clockOpacity,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. Top Bar (Close and Apply)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                onClick = onCancel,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Cancel",
                    modifier = Modifier.padding(12.dp)
                )
            }

            Surface(
                onClick = onApply,
                enabled = !isSaving,
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(R.string.editor_button_apply),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // 3. Floating iOS 16 Bottom Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color.Black.copy(alpha = 0.72f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditorNavTabButton(
                        label = stringResource(R.string.editor_tab_style),
                        icon = Icons.Rounded.AutoAwesome,
                        selected = activeSheet == EditorActiveSheet.STYLE,
                        onClick = {
                            activeSheet = if (activeSheet == EditorActiveSheet.STYLE) EditorActiveSheet.NONE else EditorActiveSheet.STYLE
                        }
                    )
                    EditorNavTabButton(
                        label = stringResource(R.string.editor_tab_clock),
                        icon = Icons.Rounded.Schedule,
                        selected = activeSheet == EditorActiveSheet.CLOCK,
                        onClick = {
                            activeSheet = if (activeSheet == EditorActiveSheet.CLOCK) EditorActiveSheet.NONE else EditorActiveSheet.CLOCK
                        }
                    )
                    EditorNavTabButton(
                        label = stringResource(R.string.editor_tab_depth),
                        icon = Icons.Rounded.Layers,
                        selected = activeSheet == EditorActiveSheet.DEPTH,
                        badge = if (depthEnabled) "ON" else null,
                        onClick = {
                            activeSheet = if (activeSheet == EditorActiveSheet.DEPTH) EditorActiveSheet.NONE else EditorActiveSheet.DEPTH
                        }
                    )
                }
            }
        }

        // 4. Modal Bottom Sheets
        when (activeSheet) {
            EditorActiveSheet.STYLE -> {
                ModalBottomSheet(
                    onDismissRequest = { activeSheet = EditorActiveSheet.NONE },
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    StyleBottomSheetContent(
                        activeEffectId = activeEffectId,
                        onEffectChange = onEffectChange,
                        isGlassEnabled = isAtmosphereGlassEnabled,
                        onGlassEnabledChange = onAtmosphereGlassEnabledChange
                    )
                }
            }
            EditorActiveSheet.CLOCK -> {
                ModalBottomSheet(
                    onDismissRequest = { activeSheet = EditorActiveSheet.NONE },
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    ClockBottomSheetContent(
                        clockStyle = clockStyle,
                        onClockStyleChange = onClockStyleChange,
                        customFontId = customFontId,
                        onCustomFontIdChange = onCustomFontIdChange,
                        customFonts = customFonts,
                        onImportFontClick = onImportFontClick,
                        clockSize = clockSize,
                        onClockSizeChange = onClockSizeChange,
                        clockOpacity = clockOpacity,
                        onClockOpacityChange = onClockOpacityChange,
                        clockColor = clockColor,
                        autoColor = autoColor,
                        onClockColorChange = onClockColorChange
                    )
                }
            }
            EditorActiveSheet.DEPTH -> {
                ModalBottomSheet(
                    onDismissRequest = { activeSheet = EditorActiveSheet.NONE },
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DepthBottomSheetContent(
                        depthEnabled = depthEnabled,
                        onDepthEnabledChange = onDepthEnabledChange,
                        isSegmenting = isSegmenting,
                        hasMask = maskBitmap != null,
                        maskFailureReason = maskFailureReason
                    )
                }
            }
            EditorActiveSheet.NONE -> {}
        }
    }
}

@Composable
private fun EditorNavTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent,
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleBottomSheetContent(
    activeEffectId: String,
    onEffectChange: (String) -> Unit,
    isGlassEnabled: Boolean,
    onGlassEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.editor_tab_style),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(EffectCatalog.items) { item ->
                val selected = item.id == activeEffectId
                Surface(
                    onClick = { onEffectChange(item.id) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.width(130.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.transition,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        if (EffectCatalog.supportsAtmosphereGlass(activeEffectId)) {
            SettingSwitchRow(
                title = stringResource(R.string.editor_effect_reeded_glass),
                subtitle = stringResource(R.string.editor_effect_reeded_glass_desc),
                checked = isGlassEnabled,
                onCheckedChange = onGlassEnabledChange
            )
        }
    }
}

@Composable
private fun ClockBottomSheetContent(
    clockStyle: ClockStyle,
    onClockStyleChange: (ClockStyle) -> Unit,
    customFontId: String?,
    onCustomFontIdChange: (String?) -> Unit,
    customFonts: List<CustomFontInfo>,
    onImportFontClick: () -> Unit,
    clockSize: Float,
    onClockSizeChange: (Float) -> Unit,
    clockOpacity: Float,
    onClockOpacityChange: (Float) -> Unit,
    clockColor: Int,
    autoColor: Int?,
    onClockColorChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.editor_clock_typography),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onImportFontClick) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.editor_clock_import_font), style = MaterialTheme.typography.labelMedium)
            }
        }

        // Built-in Font Styles
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(ClockStyle.entries) { style ->
                val selected = customFontId == null && style == clockStyle
                Surface(
                    onClick = {
                        onCustomFontIdChange(null)
                        onClockStyleChange(style)
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "12:45",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(style.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Custom imported fonts
            items(customFonts) { customFont ->
                val selected = customFontId == customFont.id
                Surface(
                    onClick = { onCustomFontIdChange(customFont.id) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "12:45",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(customFont.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Sliders for exact size & opacity
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.editor_clock_size), style = MaterialTheme.typography.bodyMedium)
                Text("${(clockSize * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = clockSize,
                valueRange = 0.08f..0.30f,
                onValueChange = onClockSizeChange
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.editor_clock_opacity), style = MaterialTheme.typography.bodyMedium)
                Text("${(clockOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = clockOpacity,
                valueRange = 0.1f..1.0f,
                onValueChange = onClockOpacityChange
            )
        }

        // Color Presets
        Text(stringResource(R.string.editor_clock_color), style = MaterialTheme.typography.bodyMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ColorPresetChip(
                    color = autoColor ?: 0xFFFFFFFF.toInt(),
                    label = "Auto",
                    selected = ClockPalette.isAuto(clockColor),
                    onClick = { onClockColorChange(ClockPalette.AUTO) }
                )
            }
            items(ClockPalette.PRESETS) { swatch ->
                ColorPresetChip(
                    color = swatch.color,
                    label = swatch.label,
                    selected = !ClockPalette.followsWallpaper(clockColor) && clockColor == swatch.color,
                    onClick = { onClockColorChange(swatch.color) }
                )
            }
        }
    }
}

@Composable
private fun ColorPresetChip(
    color: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun DepthBottomSheetContent(
    depthEnabled: Boolean,
    onDepthEnabledChange: (Boolean) -> Unit,
    isSegmenting: Boolean,
    hasMask: Boolean,
    maskFailureReason: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.editor_depth_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        SettingSwitchRow(
            title = stringResource(R.string.editor_depth_title),
            subtitle = stringResource(R.string.editor_depth_desc),
            checked = depthEnabled,
            onCheckedChange = onDepthEnabledChange
        )

        // Status badge
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isSegmenting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.editor_depth_status_processing), style = MaterialTheme.typography.bodyMedium)
                } else if (hasMask) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                    Text(stringResource(R.string.editor_depth_status_ready), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = Color(0xFFF59E0B))
                    Text(
                        maskFailureReason ?: stringResource(R.string.editor_depth_status_none),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Draws the extracted subject back over the clock for the real iOS depth effect preview.
 */
@Composable
private fun SubjectDepthOverlay(
    wallpaper: Bitmap,
    mask: Bitmap,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
    }

    Canvas(modifier) {
        val viewWidth = size.width
        val viewHeight = size.height
        if (viewWidth <= 0f || viewHeight <= 0f) return@Canvas

        val scale = max(viewWidth / wallpaper.width, viewHeight / wallpaper.height)
        val originX = (viewWidth - wallpaper.width * scale) / 2f
        val originY = (viewHeight - wallpaper.height * scale) / 2f

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val layerCount = native.saveLayer(0f, 0f, viewWidth, viewHeight, null)

            // Draw sharp wallpaper scaled
            val wallpaperRect = android.graphics.Rect(0, 0, wallpaper.width, wallpaper.height)
            val dstRect = android.graphics.RectF(
                originX,
                originY,
                originX + wallpaper.width * scale,
                originY + wallpaper.height * scale
            )
            native.drawBitmap(wallpaper, wallpaperRect, dstRect, paint)

            // Mask with subject mask using DST_IN
            val maskPaint = Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
            }
            val maskRect = android.graphics.Rect(0, 0, mask.width, mask.height)
            native.drawBitmap(mask, maskRect, dstRect, maskPaint)

            native.restoreToCount(layerCount)
        }
    }
}
