package com.agarthavision.ui.sessions

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.ui.navigation.Screen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// --- Tokens ---
private val Blue = Color(0xFF1E3FD9)
private val BlueHover = Color(0xFF1A36BF)
private val BlueTint = Color(0xFFE6EBFC)
private val BlueTint2 = Color(0xFFF1F4FE)
private val White = Color(0xFFFFFFFF)
private val Gray50 = Color(0xFFF7F8FA)
private val Gray100 = Color(0xFFEEF0F4)
private val Gray200 = Color(0xFFE2E5EB)
private val Gray300 = Color(0xFFCBD0DA)
private val Gray400 = Color(0xFF9CA3AF)
private val Gray500 = Color(0xFF6B7280)
private val Gray700 = Color(0xFF374151)
private val Gray900 = Color(0xFF0F172A)
private val Red = Color(0xFFDC2626)
private val RedTint = Color(0xFFFEE2E2)
private val Green = Color(0xFF16A34A)
private val GreenTint = Color(0xFFDCFCE7)

@Composable
fun SvgIcon(
    pathData: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    strokeWidth: Float = 1.6f,
    drawExtras: (DrawScope.() -> Unit)? = null
) {
    val path = remember(pathData) {
        PathParser().parsePathString(pathData).toPath()
    }
    Canvas(modifier = modifier) {
        val scale = size.width / 24f // assuming 24x24 viewBox
        scale(scale, scale, pivot = Offset.Zero) {
            if (pathData.isNotEmpty()) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            drawExtras?.invoke(this)
        }
    }
}

@Composable
fun SessionsScreen(
    onNavigate: (String) -> Unit = {},
    onSessionSelected: (String) -> Unit,
    viewModel: SessionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionsEvent.NavigateToCapture -> onSessionSelected(event.sessionId)
                is SessionsEvent.ShareExport -> {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, event.content)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                }
            }
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var activeKebabSessionId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(White)
    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp)
                    .align(Alignment.TopCenter)
            ) {
                // App Bar
                val activeCount = state.sessions.count { it.session.endedAt == null }
                AppBar(activeCount = activeCount, totalCount = state.sessions.size)

                // Sessions List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.sessions, key = { it.session.id }) { sessionData ->
                        SessionCard(
                            sessionData = sessionData,
                            isActive = sessionData.session.endedAt == null,
                            isKebabOpen = sessionData.session.id == activeKebabSessionId,
                            onClick = { 
                                if (sessionData.session.endedAt == null) {
                                    viewModel.onResumeSession(sessionData.session.id)
                                } else {
                                    onSessionSelected(sessionData.session.id)
                                }
                            },
                            onKebabClick = {
                                activeKebabSessionId = sessionData.session.id
                            },
                            onKebabDismiss = {
                                activeKebabSessionId = null
                            },
                            onResume = {
                                viewModel.onResumeSession(sessionData.session.id)
                                activeKebabSessionId = null
                            },
                            onExport = {
                                viewModel.onExportSession(sessionData.session.id)
                                activeKebabSessionId = null
                            },
                            onEnd = {
                                viewModel.onEndSession(sessionData.session.id)
                                activeKebabSessionId = null
                            }
                        )
                    }
                }

                // Sticky bottom CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(White)
                        .border(1.dp, Gray100) // Top hairline
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
                ) {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(49.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = White)
                    ) {
                        SvgIcon("M12 5v14M5 12h14", strokeWidth = 2.2f, color = White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

    if (showCreateDialog) {
        NewSessionSheet(
            onDismiss = { showCreateDialog = false },
            onSubmit = { label, note ->
                viewModel.onCreateSession(label, note)
                showCreateDialog = false
            }
        )
    }

    // KebabMenu is now hoisted into SessionCard
}

@Composable
private fun AppBar(activeCount: Int, totalCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 12.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sessions",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Gray900,
                letterSpacing = (-0.02).em
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$totalCount sessions · $activeCount active",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Gray500
            )
        }
    }
}

@Composable
private fun SessionCard(
    sessionData: SessionWithStats,
    isActive: Boolean,
    isKebabOpen: Boolean,
    onClick: () -> Unit,
    onKebabClick: () -> Unit,
    onKebabDismiss: () -> Unit,
    onResume: () -> Unit,
    onExport: () -> Unit,
    onEnd: () -> Unit
) {
    val session = sessionData.session
    val date = formatDate(session.startedAt)
    val time = formatTime(session.startedAt)
    val meta = if (session.notes.isNullOrBlank()) {
        "$date · $time"
    } else {
        "$date · $time · ${session.notes}"
    }

    val bgColor = if (isActive) BlueTint2 else White
    val borderColor = if (isActive) BlueTint else Gray100

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.label ?: "Session ${session.id.take(8)}",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Gray900,
                letterSpacing = (-0.015).em
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = meta,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Gray500
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isActive) {
                Row(
                    modifier = Modifier
                        .background(Blue, CircleShape)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    LiveDot()
                    Text("Active", color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { if (isKebabOpen) onKebabDismiss() else onKebabClick() },
                    contentAlignment = Alignment.Center
                ) {
                    SvgIcon(
                        pathData = "",
                        drawExtras = {
                            val r = 1f
                            drawCircle(Gray900, radius = r, center = Offset(12f, 5f), style = Stroke(width = 1.8f))
                            drawCircle(Gray900, radius = r, center = Offset(12f, 12f), style = Stroke(width = 1.8f))
                            drawCircle(Gray900, radius = r, center = Offset(12f, 19f), style = Stroke(width = 1.8f))
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    
                    if (isKebabOpen) {
                        KebabMenu(
                            onDismiss = onKebabDismiss,
                            onResume = onResume,
                            onExport = onExport,
                            onEnd = onEnd
                        )
                    }
                }
            } else {
                val eggs = sessionData.totalEpg
                val badgeBg = if (eggs > 0) GreenTint else Gray100
                val badgeColor = if (eggs > 0) Color(0xFF166534) else Gray700
                Box(
                    modifier = Modifier
                        .background(badgeBg, CircleShape)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text("$eggs eggs", color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun LiveDot() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(White.copy(alpha = alpha), CircleShape)
    )
}

@Composable
private fun KebabMenu(
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onExport: () -> Unit,
    onEnd: () -> Unit
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        alignment = Alignment.TopEnd,
        offset = androidx.compose.ui.unit.IntOffset(0, with(LocalDensity.current) { 36.dp.roundToPx() })
    ) {
        Column(
            modifier = Modifier
                .width(168.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp), spotColor = Color(0x2E0F172A))
                .background(White, RoundedCornerShape(12.dp))
                .border(1.dp, Gray100, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
                KebabItem("Resume capture", "M7 10l5-5 5 5M12 5v14", onClick = onResume)
                KebabItem("Export session", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3", onClick = onExport)
                KebabItem("End session", "", isDestructive = true, onClick = onEnd, drawExtras = {
                    drawRoundRect(
                        color = Red,
                        topLeft = Offset(6f, 6f),
                        size = Size(12f, 12f),
                        cornerRadius = CornerRadius(1f, 1f),
                        style = Stroke(width = 1.6f)
                    )
                })
        }
    }
}

@Composable
private fun KebabItem(
    label: String,
    pathData: String,
    isDestructive: Boolean = false,
    drawExtras: (DrawScope.() -> Unit)? = null,
    onClick: () -> Unit
) {
    val color = if (isDestructive) Red else Gray900
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SvgIcon(pathData, color = color, strokeWidth = 1.6f, modifier = Modifier.size(15.dp), drawExtras = drawExtras)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionSheet(
    onDismiss: () -> Unit,
    onSubmit: (label: String, note: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Gray200, RoundedCornerShape(2.dp))
            )
        },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        var label by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var showError by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text("New session", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Gray900, letterSpacing = (-0.015).em)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Set the label and a note before scanning", fontSize = 12.sp, color = Gray500, fontWeight = FontWeight.Medium)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Gray100, CircleShape)
                ) {
                    SvgIcon("M18 6L6 18M6 6l12 12", color = Gray700, strokeWidth = 2f, modifier = Modifier.size(16.dp))
                }
            }
            
            // Body
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                SheetInput(
                    label = "Label",
                    value = label,
                    onValueChange = { label = it; showError = false },
                    placeholder = "e.g. 325",
                    isError = showError && label.isBlank()
                )
                Spacer(modifier = Modifier.height(14.dp))
                SheetInput(
                    label = "Note",
                    value = note,
                    onValueChange = { note = it; showError = false },
                    placeholder = "Patient ID, clinical context, sample details...",
                    isError = false, // Note is never in error since it's optional
                    isTextArea = true,
                    isRequired = false
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (showError && label.isBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RedTint, RoundedCornerShape(8.dp))
                            .border(1.dp, Red.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SvgIcon("M12 8v4M12 16h.01", drawExtras = { drawCircle(Red, radius = 9f, center = Offset(12f, 12f), style = Stroke(width = 1.8f)) }, color = Red, modifier = Modifier.size(16.dp))
                        Text("Please fill in the label field to continue.", fontSize = 12.sp, color = Color(0xFF991B1B), fontWeight = FontWeight.Medium, lineHeight = 16.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BlueTint2, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SvgIcon("M12 8v4M12 16h.01", drawExtras = { drawCircle(Blue, radius = 9f, center = Offset(12f, 12f), style = Stroke(width = 1.8f)) }, color = Blue, modifier = Modifier.size(16.dp))
                        Text("A session label is required by lab protocol. You can edit it later from Session Detail.", fontSize = 12.sp, color = Gray700, lineHeight = 16.sp)
                    }
                }
            }
            
            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(49.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Gray100, contentColor = Gray900)
                ) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        if (label.isBlank()) {
                            showError = true
                        } else {
                            onSubmit(label, note)
                        }
                    },
                    modifier = Modifier.weight(1f).height(49.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = White)
                ) {
                    Text("Start session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(6.dp))
                    SvgIcon("M5 12h14M13 5l7 7-7 7", color = White, strokeWidth = 2.2f, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
    isTextArea: Boolean = false,
    isRequired: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
            if (isRequired) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("REQUIRED", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em, color = Gray500, modifier = Modifier.background(Gray100, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                Text("OPTIONAL", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em, color = Gray400, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
        
        var isFocused by remember { mutableStateOf(false) }
        val borderColor = if (isError) Red else if (isFocused) Blue else Gray200
        val bgColor = if (isError) RedTint.copy(alpha = 0.5f) else White

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = Gray900),
            singleLine = !isTextArea,
            minLines = if (isTextArea) 3 else 1,
            cursorBrush = SolidColor(Blue),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isTextArea) Modifier.heightIn(min = 88.dp) else Modifier)
                        .background(bgColor, RoundedCornerShape(12.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    contentAlignment = if (isTextArea) Alignment.TopStart else Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, fontSize = 15.sp, color = Gray400, lineHeight = 21.75.sp)
                    }
                    innerTextField()
                }
            }
        )
    }
}

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
