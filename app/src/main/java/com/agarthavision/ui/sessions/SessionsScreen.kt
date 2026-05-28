package com.agarthavision.ui.sessions

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.ui.components.AgarthaBottomBar
import com.agarthavision.ui.navigation.Screen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Design Tokens
private val Brand = Color(0xFF1E40AF)
private val BrandDeep = Color(0xFF1E3A8A)
private val BrandTintGrad = Color(0xFFF4F7FF)
private val Success = Color(0xFF34C759)
private val SuccessDeep = Color(0xFF248A3D)
private val Danger = Color(0xFFFF3B30)
private val Ink = Color(0xFF0F172A)
private val Body = Color(0xFF3C3C43)
private val Muted = Color(0xFF6E6E73)
private val Bg = Color(0xFFF2F2F7)
private val Surface = Color(0xFFFFFFFF)
private val Hairline = Color(0x1E3C3C43)
private val HairlineSoft = Color(0x143C3C43)

@Composable
fun SessionsScreen(
    onNavigate: (String) -> Unit = {},
    onSessionSelected: (String) -> Unit,
    viewModel: SessionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var contextMenuState by remember { mutableStateOf<ContextMenuState?>(null) }
    var renameSessionId by remember { mutableStateOf<String?>(null) }

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

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Scaffold(
            bottomBar = { AgarthaBottomBar(activeRoute = Screen.Sessions.route, onNavigate = onNavigate) },
            containerColor = Color.Transparent,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (contextMenuState != null) contextMenuState = null
                })
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 20.dp)
                ) {
                    val activeSessions = state.sessions.filter { it.session.endedAt == null }
                    val dateString = Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, MMM dd"))
                    val activeCountText = if (activeSessions.isEmpty()) "No active sessions" else "${activeSessions.size} active"

                    LargeTitle(dateString, activeCountText)
                    SearchBar(
                        query = state.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChanged
                    )

                    if (state.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Brand)
                        }
                    } else if (state.sessions.isEmpty()) {
                        EmptyState()
                    } else {
                        SessionFeed(
                            sessions = state.sessions,
                            onCardClick = { session ->
                                viewModel.onResumeSession(session.session.id)
                            },
                            onMoreClick = { session, offset ->
                                contextMenuState = ContextMenuState(session, offset)
                            },
                        )
                    }
                }

                // Floating CTA — always pinned above tab bar
                FloatingNewSessionCTA(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 16.dp),
                    onClick = { showCreateDialog = true }
                )
            }
        }

        // Dim Layer
        if (contextMenuState != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { contextMenuState = null })
                    }
            )
        }

        // Popover
        contextMenuState?.let { menuState ->
            ContextMenuPopover(
                state = menuState,
                onDismiss = { contextMenuState = null },
                onResume = { viewModel.onResumeSession(it.session.id) },
                onRename = { renameSessionId = it.session.id },
                onExport = { viewModel.onExportSession(it.session.id) },
                onEnd = { viewModel.onEndSession(it.session.id) }
            )
        }
    }

    if (showCreateDialog) {
        CreateSessionDialog(
            isCreating = state.isCreating,
            onDismiss = { showCreateDialog = false },
            onCreate = { label, notes ->
                showCreateDialog = false
                viewModel.onCreateSession(label, notes)
            }
        )
    }

    renameSessionId?.let { sessionId ->
        RenameSessionDialog(
            onDismiss = { renameSessionId = null },
            onRename = { newLabel ->
                viewModel.onRenameSession(sessionId, newLabel)
                renameSessionId = null
            }
        )
    }
}

@Composable
fun LargeTitle(date: String, activeCountText: String) {
    Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp)) {
        Text("Sessions", fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, color = Ink, lineHeight = 34.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text("$date · $activeCountText", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Muted)
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(bottom = 16.dp)
            .background(Color(120, 120, 128, (0.12f * 255).toInt()), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = "Search", tint = Muted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search by ID or label", color = Muted, fontSize = 15.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = Ink, fontFamily = FontFamily.Default),
                modifier = Modifier.fillMaxWidth(),
                cursorBrush = SolidColor(Brand),
            )
        }
    }
}

@Composable
fun SessionFeed(
    sessions: List<SessionWithStats>,
    onCardClick: (SessionWithStats) -> Unit,
    onMoreClick: (SessionWithStats, Offset) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 88.dp) // reserve space so last card isn't hidden behind the floating CTA
    ) {
        items(sessions, key = { it.session.id }) { sessionWithStats ->
            if (sessionWithStats.session.endedAt == null) {
                ActiveSessionCard(sessionWithStats, onCardClick, onMoreClick)
            } else {
                DefaultSessionCard(sessionWithStats, onCardClick, onMoreClick)
            }
        }
    }
}

@Composable
fun ActiveSessionCard(
    sessionData: SessionWithStats,
    onClick: (SessionWithStats) -> Unit,
    onMoreClick: (SessionWithStats, Offset) -> Unit
) {
    val session = sessionData.session
    val density = LocalDensity.current
    var moreBtnPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Brand.copy(alpha = 0.12f),
                ambientColor = Brand.copy(alpha = 0.12f)
            )
            .background(
                brush = Brush.verticalGradient(listOf(Color.White, BrandTintGrad)),
                shape = RoundedCornerShape(18.dp)
            )
            .border(0.5.dp, Brand.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .clickable { onClick(sessionData) }
            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(session.id.take(8), fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = BrandDeep, fontFamily = FontFamily.Monospace)
                        Text("SMEAR-${formatDate(session.startedAt)}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = Muted, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Started ${formatTime(session.startedAt)}", fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp, color = Muted, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(session.label ?: "Unnamed Session", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Body)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .background(Success.copy(alpha = 0.16f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(modifier = Modifier.size(5.dp).shadow(5.dp, CircleShape, spotColor = Success, ambientColor = Success).background(Success, CircleShape))
                        Text("ACTIVE", color = SuccessDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onMoreClick(sessionData, moreBtnPosition) }
                            .onGloballyPositioned { coordinates ->
                                moreBtnPosition = coordinates.positionInRoot()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("•••", fontSize = 14.sp, color = Muted, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Brand.copy(alpha = 0.12f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatItem(label = "SAMPLES", value = sessionData.totalSamples.toString(), isBrand = true, isGreen = false)
                Spacer(modifier = Modifier.width(16.dp))
                StatItem(label = "VERIFIED", value = sessionData.verifiedSamples.toString(), isBrand = false, isGreen = true)
                Spacer(modifier = Modifier.width(16.dp))
                StatItem(label = "EPG", value = sessionData.totalEpg.toString(), isBrand = false, isGreen = false)
                Spacer(modifier = Modifier.weight(1f))
                
                Column(horizontalAlignment = Alignment.End) {
                    Text("SYNC", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = Muted, fontFamily = FontFamily.Monospace)
                    Text("✓ Live", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = SuccessDeep)
                }
            }
        }
    }
}

@Composable
fun DefaultSessionCard(
    sessionData: SessionWithStats,
    onClick: (SessionWithStats) -> Unit,
    onMoreClick: (SessionWithStats, Offset) -> Unit
) {
    val session = sessionData.session
    val density = LocalDensity.current
    var moreBtnPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Ink.copy(alpha = 0.05f)
            )
            .background(Surface, RoundedCornerShape(18.dp))
            .border(0.5.dp, HairlineSoft, RoundedCornerShape(18.dp))
            .clickable { onClick(sessionData) }
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(session.id.take(8), fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = Ink, fontFamily = FontFamily.Monospace)
                        Text("SMEAR-${formatDate(session.startedAt)}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = Muted, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${formatTime(session.startedAt)}", fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp, color = Muted, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(session.label ?: "Unnamed Session", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Body)
                }
                
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onMoreClick(sessionData, moreBtnPosition) }
                        .onGloballyPositioned { coordinates ->
                            moreBtnPosition = coordinates.positionInRoot()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("•••", fontSize = 14.sp, color = Muted, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatItem(label = "SAMPLES", value = sessionData.totalSamples.toString())
                Spacer(modifier = Modifier.width(16.dp))
                StatItem(label = "VERIFIED", value = sessionData.verifiedSamples.toString())
                Spacer(modifier = Modifier.width(16.dp))
                StatItem(label = "EPG", value = if (sessionData.totalEpg > 0) sessionData.totalEpg.toString() else "—")
                Spacer(modifier = Modifier.weight(1f))
                
                Column(horizontalAlignment = Alignment.End) {
                    Text("STATUS", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = Muted, fontFamily = FontFamily.Monospace)
                    Text("Paused", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = Muted)
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, isBrand: Boolean = false, isGreen: Boolean = false) {
    Column {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = Muted, fontFamily = FontFamily.Monospace)
        val color = if (isBrand) BrandDeep else if (isGreen) SuccessDeep else Ink
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = color)
    }
}

@Composable
fun FloatingNewSessionCTA(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = Brand.copy(alpha = 0.35f),
                ambientColor = Brand.copy(alpha = 0.22f)
            )
            .background(Brand, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("New session", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = 88.dp), // avoid overlap with floating CTA
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No sessions yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Tap \"New session\" below to start your first patient smear examination.", fontSize = 15.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

data class ContextMenuState(val sessionData: SessionWithStats, val anchorPosition: Offset)

@Composable
fun ContextMenuPopover(
    state: ContextMenuState,
    onDismiss: () -> Unit,
    onResume: (SessionWithStats) -> Unit,
    onRename: (SessionWithStats) -> Unit,
    onExport: (SessionWithStats) -> Unit,
    onEnd: (SessionWithStats) -> Unit
) {
    val density = LocalDensity.current
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            Column(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (state.anchorPosition.x - with(density) { 180.dp.toPx() }).roundToInt().coerceAtLeast(16),
                            y = (state.anchorPosition.y + with(density) { 24.dp.toPx() }).roundToInt()
                        )
                    }
                    .width(220.dp)
                    .shadow(36.dp, RoundedCornerShape(14.dp), spotColor = Ink.copy(alpha = 0.18f))
                    .background(Color(0xFFF8F8FA).copy(alpha = 0.92f), RoundedCornerShape(14.dp))
                    .border(0.5.dp, Hairline, RoundedCornerShape(14.dp))
            ) {
                ContextMenuItem("Resume", "▶") { onResume(state.sessionData); onDismiss() }
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                ContextMenuItem("Rename", "✎") { onRename(state.sessionData); onDismiss() }
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                ContextMenuItem("Export samples", "↓") { onExport(state.sessionData); onDismiss() }
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                ContextMenuItem("End session", "■", isDanger = true) { onEnd(state.sessionData); onDismiss() }
            }
        }
    }
}

@Composable
fun ContextMenuItem(label: String, iconStr: String, isDanger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (isDanger) Danger else Ink
        Text(label, fontSize = 15.sp, color = color)
        Text(iconStr, fontSize = 14.sp, color = color)
    }
}



@Composable
fun CreateSessionDialog(isCreating: Boolean, onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onCreate(label, notes) }, enabled = label.isNotBlank() && !isCreating) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("New Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (required)") })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") })
            }
        }
    )
}

@Composable
fun RenameSessionDialog(onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onRename(label) }, enabled = label.isNotBlank()) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Rename Session") },
        text = {
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("New Label") })
        }
    )
}

fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MMdd"))

fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
