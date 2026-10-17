package com.toffa.hookahmap

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class TableType {
    BOOTH_TOP, BOOTH_TOP_SINGLE, BOOTH_RIGHT, RECT_H2, RECT_V2, SQUARE_4, ROUND_4
}

enum class TableZone(val title: String) {
    HOOKAH("Кальянная зона (всегда)"),
    WHITE("Белая зона (после 22:00)")
}

enum class HookahStage(
    val step: Int,
    val label: String,
    val shortName: String,
    val fillColor: Color,
    val strokeColor: Color,
    val textColor: Color
) {
    NONE(0, "Свободен / Без кальяна", "Чистый", Color(0xFFFFFFFF), Color(0xFF475569), Color(0xFF1E293B)),
    SERVED(1, "1. Кальян подан", "Подан", Color(0xFF2563EB), Color(0xFF93C5FD), Color.White),
    COAL_1(2, "2. Первая замена углей", "1-я замена", Color(0xFF16A34A), Color(0xFF86EFAC), Color.White),
    COAL_2(3, "3. Вторая замена углей", "2-я замена", Color(0xFFEAB308), Color(0xFFFEF08A), Color(0xFF0F172A)),
    COAL_3(4, "4. Третья замена углей", "3-я замена", Color(0xFFF97316), Color(0xFFFED7AA), Color.White),
    COAL_4(5, "5. Четвертая замена углей", "4-я замена", Color(0xFFDC2626), Color(0xFFFECACA), Color.White),
    DEAD(6, "6. Кальян Сдох", "Сдох", Color(0xFF64748B), Color(0xFFCBD5E1), Color(0xFFE2E8F0));

    fun next(): HookahStage = when (this) {
        NONE -> SERVED
        SERVED -> COAL_1
        COAL_1 -> COAL_2
        COAL_2 -> COAL_3
        COAL_3 -> COAL_4
        COAL_4 -> DEAD
        DEAD -> NONE
    }
}

data class TableData(
    val id: Int,
    val x: Float,
    val y: Float,
    val type: TableType,
    val zone: TableZone
)

data class TableState(
    val stage: HookahStage = HookahStage.NONE,
    val updatedAtMillis: Long = 0L
)

val ALL_TABLES = listOf(
    // Верхний ряд (60 - 53)
    TableData(60, 145f, 82f, TableType.BOOTH_TOP, TableZone.WHITE),
    TableData(59, 245f, 82f, TableType.BOOTH_TOP, TableZone.WHITE),
    TableData(58, 338f, 82f, TableType.BOOTH_TOP, TableZone.WHITE),
    TableData(57, 650f, 82f, TableType.BOOTH_TOP, TableZone.HOOKAH),
    TableData(56, 732f, 82f, TableType.BOOTH_TOP, TableZone.HOOKAH),
    TableData(55, 800f, 82f, TableType.BOOTH_TOP_SINGLE, TableZone.HOOKAH),
    TableData(54, 868f, 82f, TableType.BOOTH_TOP, TableZone.HOOKAH),
    TableData(53, 948f, 82f, TableType.BOOTH_TOP, TableZone.HOOKAH),

    // Ряды 49-52 и 48-43
    TableData(49, 145f, 200f, TableType.RECT_H2, TableZone.WHITE),
    TableData(50, 235f, 200f, TableType.RECT_H2, TableZone.WHITE),
    TableData(51, 682f, 200f, TableType.RECT_H2, TableZone.HOOKAH),
    TableData(52, 772f, 200f, TableType.RECT_H2, TableZone.HOOKAH),

    TableData(48, 145f, 272f, TableType.RECT_H2, TableZone.WHITE),
    TableData(47, 235f, 272f, TableType.RECT_H2, TableZone.WHITE),
    TableData(46, 412f, 272f, TableType.RECT_H2, TableZone.WHITE),
    TableData(45, 592f, 272f, TableType.RECT_H2, TableZone.WHITE),
    TableData(44, 682f, 272f, TableType.RECT_H2, TableZone.HOOKAH),
    TableData(43, 772f, 272f, TableType.RECT_H2, TableZone.HOOKAH),

    // Ряд 36 - 42
    TableData(36, 145f, 362f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(37, 235f, 362f, TableType.ROUND_4, TableZone.WHITE),
    TableData(38, 412f, 362f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(39, 592f, 362f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(40, 682f, 362f, TableType.SQUARE_4, TableZone.HOOKAH),
    TableData(41, 772f, 362f, TableType.ROUND_4, TableZone.HOOKAH),
    TableData(42, 956f, 362f, TableType.BOOTH_RIGHT, TableZone.HOOKAH),

    // Ряд 35 - 27
    TableData(35, 145f, 470f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(34, 235f, 470f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(33, 342f, 470f, TableType.RECT_V2, TableZone.WHITE),
    TableData(32, 412f, 470f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(31, 502f, 470f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(30, 592f, 470f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(29, 682f, 470f, TableType.SQUARE_4, TableZone.HOOKAH),
    TableData(28, 772f, 470f, TableType.SQUARE_4, TableZone.HOOKAH),
    TableData(27, 956f, 462f, TableType.BOOTH_RIGHT, TableZone.HOOKAH),

    // Ряд 17 - 26
    TableData(17, 44f, 576f, TableType.RECT_V2, TableZone.WHITE),
    TableData(18, 145f, 576f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(19, 235f, 576f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(20, 342f, 576f, TableType.RECT_V2, TableZone.WHITE),
    TableData(21, 412f, 576f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(22, 502f, 576f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(23, 592f, 576f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(24, 682f, 576f, TableType.SQUARE_4, TableZone.HOOKAH),
    TableData(25, 772f, 576f, TableType.SQUARE_4, TableZone.HOOKAH),
    TableData(26, 956f, 558f, TableType.BOOTH_RIGHT, TableZone.HOOKAH),

    // Ряд 16 - 9 + 8
    TableData(16, 44f, 684f, TableType.RECT_V2, TableZone.WHITE),
    TableData(15, 145f, 684f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(14, 235f, 684f, TableType.ROUND_4, TableZone.WHITE),
    TableData(13, 412f, 684f, TableType.ROUND_4, TableZone.WHITE),
    TableData(12, 502f, 684f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(11, 592f, 684f, TableType.SQUARE_4, TableZone.WHITE),
    TableData(10, 682f, 684f, TableType.SQUARE_4, TableZone.HOOKAH),
    TableData(9, 772f, 684f, TableType.ROUND_4, TableZone.HOOKAH),
    TableData(8, 956f, 720f, TableType.BOOTH_RIGHT, TableZone.HOOKAH),

    // Ряд 2 - 5 + 7
    TableData(2, 412f, 766f, TableType.RECT_H2, TableZone.WHITE),
    TableData(3, 502f, 766f, TableType.RECT_H2, TableZone.WHITE),
    TableData(4, 592f, 766f, TableType.RECT_H2, TableZone.WHITE),
    TableData(5, 772f, 766f, TableType.RECT_H2, TableZone.HOOKAH),
    TableData(7, 956f, 820f, TableType.BOOTH_RIGHT, TableZone.HOOKAH),

    // Нижний ряд 1 и 6
    TableData(1, 412f, 882f, TableType.ROUND_4, TableZone.WHITE),
    TableData(6, 956f, 920f, TableType.BOOTH_RIGHT, TableZone.HOOKAH)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HookahFloorScreen()
            }
        }
    }
}

@Composable
fun HookahFloorScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hookah_floor_prefs", Context.MODE_PRIVATE) }

    val tableStates = remember {
        mutableStateMapOf<Int, TableState>().apply {
            ALL_TABLES.forEach { t ->
                val savedStep = prefs.getInt("stage_${t.id}", 0)
                val savedTime = prefs.getLong("time_${t.id}", 0L)
                val stage = HookahStage.entries.find { it.step == savedStep } ?: HookahStage.NONE
                put(t.id, TableState(stage, savedTime))
            }
        }
    }

    var forceAfter22 by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val isWhiteZoneUnlocked = forceAfter22 || currentTime.hour >= 22

    fun updateTable(table: TableData, newStage: HookahStage) {
        val updatedTime = if (newStage == HookahStage.NONE) 0L else System.currentTimeMillis()
        tableStates[table.id] = TableState(newStage, updatedTime)
        prefs.edit()
            .putInt("stage_${table.id}", newStage.step)
            .putLong("time_${table.id}", updatedTime)
            .apply()
    }

    fun handleTableTap(table: TableData) {
        if (table.zone == TableZone.WHITE && !isWhiteZoneUnlocked) {
            Toast.makeText(
                context,
                "Стол №${table.id} в Белой зоне! Доступно только после 22:00",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val current = tableStates[table.id]?.stage ?: HookahStage.NONE
        updateTable(table, current.next())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Верхняя панель управления
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(14.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Схема зала • Кальянная карта",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                val statusText = if (isWhiteZoneUnlocked) {
                    "Белая зона ОТКРЫТА (${currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))})"
                } else {
                    "Белая зона ЗАКРЫТА до 22:00 (${currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))})"
                }
                Text(
                    text = statusText,
                    color = if (isWhiteZoneUnlocked) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { forceAfter22 = !forceAfter22 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (forceAfter22) Color(0xFF4F46E5) else Color(0xFF334155)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (forceAfter22) "Тест: После 22:00" else "Реальное время",
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        ALL_TABLES.forEach { updateTable(it, HookahStage.NONE) }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Сброс всех", fontSize = 12.sp, color = Color(0xFFF87171))
                }
            }
        }

        // Легенда статусов
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HookahStage.entries.drop(1).forEach { stage ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(stage.fillColor)
                    )
                    Text(
                        text = "${stage.step}. ${stage.shortName}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Интерактивная карта зала (Canvas с точными пропорциями 1050x980)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1050f / 980f)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                .background(Color(0xFFF8FAFC))
        ) {
            FloorMapCanvas(
                tableStates = tableStates,
                isWhiteZoneUnlocked = isWhiteZoneUnlocked,
                onTableClick = { handleTableTap(it) }
            )
        }

        // Нижняя панель активных столов
        val activeTables = ALL_TABLES.filter {
            (tableStates[it.id]?.stage ?: HookahStage.NONE) != HookahStage.NONE
        }.sortedBy { it.id }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Активные столы со статусом (${activeTables.size})",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Нажмите на карточку для смены этапа",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            if (activeTables.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нет активных столов. Нажмите на любой доступный стол на схеме выше.",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 230.dp),
                    modifier = Modifier.heightIn(max = 520.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activeTables, key = { it.id }) { table ->
                        val state = tableStates[table.id] ?: TableState()
                        val elapsedSec = ((nowMillis - state.updatedAtMillis) / 1000L).coerceAtLeast(0L)
                        val mins = elapsedSec / 60
                        val secs = elapsedSec % 60
                        val timeFormatted = "%02d:%02d".format(mins, secs)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, state.stage.fillColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .clickable { handleTableTap(table) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(state.stage.fillColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${table.id}",
                                        color = state.stage.textColor,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp
                                    )
                                }

                                Column {
                                    Text(
                                        text = state.stage.label,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${table.zone.title} • ⏱ $timeFormatted",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = { updateTable(table, HookahStage.NONE) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("✕", color = Color(0xFF94A3B8), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloorMapCanvas(
    tableStates: Map<Int, TableState>,
    isWhiteZoneUnlocked: Boolean,
    onTableClick: (TableData) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isWhiteZoneUnlocked) {
                detectTapGestures { offset ->
                    val scaleX = 1050f / size.width
                    val scaleY = 980f / size.height
                    val logicalX = offset.x * scaleX
                    val logicalY = offset.y * scaleY

                    val tappedTable = ALL_TABLES.find { table ->
                        kotlin.math.abs(logicalX - table.x) <= 44f &&
                                kotlin.math.abs(logicalY - table.y) <= 44f
                    }
                    if (tappedTable != null) {
                        onTableClick(tappedTable)
                    }
                }
            }
    ) {
        val sx = size.width / 1050f
        val sy = size.height / 980f

        // 1. Светлая зона (только после 22:00)
        drawRect(
            color = Color(0xFFF3F4F6),
            topLeft = Offset.Zero,
            size = size
        )

        // 2. Тёмная кальянная зона справа (полигон точно по схеме)
        val hookahZonePath = Path().apply {
            moveTo(615f * sx, 0f)
            lineTo(1040f * sx, 0f)
            lineTo(1040f * sx, 980f * sy)
            lineTo(885f * sx, 980f * sy)
            lineTo(885f * sx, 795f * sy)
            lineTo(645f * sx, 795f * sy)
            lineTo(645f * sx, 175f * sy)
            lineTo(615f * sx, 175f * sy)
            close()
        }
        drawPath(
            path = hookahZonePath,
            color = Color(0xFFCBD5E1)
        )
        drawPath(
            path = hookahZonePath,
            color = Color(0xFF64748B),
            style = Stroke(width = 2.5f * sx)
        )

        // 3. Внутренние линии разметки зала
        drawLine(
            color = Color(0xFF475569),
            start = Offset(392f * sx, 175f * sy),
            end = Offset(392f * sx, 300f * sy),
            strokeWidth = 2f * sx
        )
        drawLine(
            color = Color(0xFF475569),
            start = Offset(392f * sx, 300f * sy),
            end = Offset(470f * sx, 300f * sy),
            strokeWidth = 2f * sx
        )
        drawLine(
            color = Color(0xFF475569),
            start = Offset(470f * sx, 300f * sy),
            end = Offset(470f * sx, 420f * sy),
            strokeWidth = 2f * sx
        )
        drawLine(
            color = Color(0xFF475569),
            start = Offset(470f * sx, 420f * sy),
            end = Offset(375f * sx, 420f * sy),
            strokeWidth = 2f * sx
        )
        drawLine(
            color = Color(0xFF475569),
            start = Offset(375f * sx, 420f * sy),
            end = Offset(375f * sx, 980f * sy),
            strokeWidth = 2f * sx
        )
        drawLine(
            color = Color(0xFF475569),
            start = Offset(375f * sx, 628f * sy),
            end = Offset(960f * sx, 628f * sy),
            strokeWidth = 2f * sx
        )

        // 4. Архитектурные блоки (СЦЕНА, ЛЕСТНИЦА, ДЕТСКАЯ, БАР/КАССА, DJ, WC, СТЕЙШЕН)
        drawLandmarkBox(392f, 36f, 223f, 139f, "СЦЕНА", sx, sy, 18f)
        drawLandmarkBox(16f, 308f, 62f, 206f, "ЛЕСТНИЦА", sx, sy, 13f, verticalText = true)
        drawLandmarkBox(18f, 725f, 344f, 188f, "ДЕТСКАЯ", sx, sy, 16f)
        drawLandmarkBox(265f, 725f, 20f, 92f, "ГЛ.СТЕЙШН", sx, sy, 8.5f, verticalText = true)
        drawLandmarkBox(348f, 315f, 26f, 78f, "СТЕЙШЕН", sx, sy, 9f, verticalText = true)
        drawLandmarkBox(348f, 646f, 26f, 78f, "СТЕЙШЕН", sx, sy, 9f, verticalText = true)
        drawLandmarkBox(650f, 736f, 72f, 36f, "СТЕЙШЕН", sx, sy, 9.5f)
        drawLandmarkBox(946f, 172f, 42f, 84f, "СТЕЙШЕН", sx, sy, 10f, verticalText = true)
        drawLandmarkBox(965f, 596f, 66f, 62f, "WC", sx, sy, 16f)
        drawLandmarkBox(598f, 854f, 48f, 58f, "DJ", sx, sy, 15f)
        drawLandmarkBox(655f, 854f, 138f, 58f, "БАР/КАССА", sx, sy, 15f)

        // 5. Отрисовка всех 60 столов
        ALL_TABLES.forEach { table ->
            val state = tableStates[table.id] ?: TableState()
            val isLocked = table.zone == TableZone.WHITE && !isWhiteZoneUnlocked
            drawTableItem(table, state.stage, isLocked, sx, sy)
        }
    }
}

private fun DrawScope.drawLandmarkBox(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    label: String,
    sx: Float,
    sy: Float,
    fontSize: Float,
    verticalText: Boolean = false
) {
    drawRect(
        color = Color(0xFFD1D5DB),
        topLeft = Offset(x * sx, y * sy),
        size = Size(w * sx, h * sy)
    )
    drawRect(
        color = Color(0xFF6B7280),
        topLeft = Offset(x * sx, y * sy),
        size = Size(w * sx, h * sy),
        style = Stroke(width = 1.5f * sx)
    )

    val cx = (x + w / 2f) * sx
    val cy = (y + h / 2f) * sy

    drawContext.canvas.nativeCanvas.apply {
        save()
        if (verticalText) {
            rotate(-90f, cx, cy)
        }
        val paint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = fontSize * sx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.WHITE
        }
        val strokePaint = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * sx
            color = android.graphics.Color.BLACK
        }
        val textY = cy - (paint.descent() + paint.ascent()) / 2f
        drawText(label, cx, textY, strokePaint)
        drawText(label, cx, textY, paint)
        restore()
    }
}

private fun DrawScope.drawTableItem(
    table: TableData,
    stage: HookahStage,
    isLocked: Boolean,
    sx: Float,
    sy: Float
) {
    val cx = table.x * sx
    val cy = table.y * sy
    val seatColor = if (isLocked && stage == HookahStage.NONE) Color(0xFF94A3B8) else Color(0xFF64748B)
    val seatStroke = Color(0xFF334155)

    fun drawChair(dx: Float, dy: Float, w: Float, h: Float) {
        val left = cx + dx * sx
        val top = cy + dy * sy
        drawRoundRect(
            color = seatColor,
            topLeft = Offset(left, top),
            size = Size(w * sx, h * sy),
            cornerRadius = CornerRadius(3f * sx, 3f * sy)
        )
        drawRoundRect(
            color = seatStroke,
            topLeft = Offset(left, top),
            size = Size(w * sx, h * sy),
            cornerRadius = CornerRadius(3f * sx, 3f * sy),
            style = Stroke(width = 1.2f * sx)
        )
    }

    // Стулья и диваны вокруг стола
    when (table.type) {
        TableType.BOOTH_TOP -> {
            drawChair(-36f, -34f, 72f, 12f)
            drawChair(-22f, 16f, 14f, 12f)
            drawChair(8f, 16f, 14f, 12f)
        }
        TableType.BOOTH_TOP_SINGLE -> {
            drawChair(-24f, -34f, 48f, 12f)
            drawChair(-7f, 16f, 14f, 12f)
        }
        TableType.BOOTH_RIGHT -> {
            drawChair(18f, -36f, 13f, 72f)
            drawChair(-28f, -20f, 11f, 13f)
            drawChair(-28f, 7f, 11f, 13f)
        }
        TableType.RECT_H2 -> {
            drawChair(-30f, -12f, 9f, 24f)
            drawChair(21f, -12f, 9f, 24f)
        }
        TableType.RECT_V2 -> {
            drawChair(-13f, -28f, 26f, 9f)
            drawChair(-13f, 19f, 26f, 9f)
        }
        TableType.SQUARE_4 -> {
            drawChair(-33f, -18f, 10f, 14f)
            drawChair(-33f, 4f, 10f, 14f)
            drawChair(23f, -18f, 10f, 14f)
            drawChair(23f, 4f, 10f, 14f)
        }
        TableType.ROUND_4 -> {
            listOf(45f, 135f, 225f, 315f).forEach { angle ->
                rotate(degrees = angle, pivot = Offset(cx, cy)) {
                    drawChair(-7f, -29f, 14f, 8f)
                }
            }
        }
    }

    // Столешница
    val tableFill = if (isLocked && stage == HookahStage.NONE) Color(0xFFE2E8F0) else stage.fillColor
    val tableStroke = if (isLocked && stage == HookahStage.NONE) Color(0xFF94A3B8) else stage.strokeColor

    val (tw, th) = when (table.type) {
        TableType.BOOTH_TOP -> 68f to 36f
        TableType.BOOTH_TOP_SINGLE -> 42f to 36f
        TableType.BOOTH_RIGHT -> 36f to 58f
        TableType.RECT_H2 -> 42f to 32f
        TableType.RECT_V2 -> 32f to 38f
        TableType.SQUARE_4 -> 46f to 44f
        TableType.ROUND_4 -> 42f to 42f
    }

    if (table.type == TableType.ROUND_4) {
        drawCircle(
            color = tableFill,
            radius = 21f * sx,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = tableStroke,
            radius = 21f * sx,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5f * sx)
        )
    } else {
        drawRoundRect(
            color = tableFill,
            topLeft = Offset(cx - (tw / 2f) * sx, cy - (th / 2f) * sy),
            size = Size(tw * sx, th * sy),
            cornerRadius = CornerRadius(6f * sx, 6f * sy)
        )
        drawRoundRect(
            color = tableStroke,
            topLeft = Offset(cx - (tw / 2f) * sx, cy - (th / 2f) * sy),
            size = Size(tw * sx, th * sy),
            cornerRadius = CornerRadius(6f * sx, 6f * sy),
            style = Stroke(width = 2.5f * sx)
        )
    }

    // Номер стола и подпись (22:00 или номер этапа)
    drawContext.canvas.nativeCanvas.apply {
        val numberPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 14.5f * sx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = if (isLocked && stage == HookahStage.NONE) {
                Color(0xFF64748B).toArgb()
            } else {
                stage.textColor.toArgb()
            }
        }
        val numY = cy - (numberPaint.descent() + numberPaint.ascent()) / 2f
        drawText(table.id.toString(), cx, numY, numberPaint)

        if (stage != HookahStage.NONE) {
            val badgeX = cx + (tw / 2f - 4f) * sx
            val badgeY = cy - (th / 2f - 4f) * sy
            val badgeBgPaint = Paint().apply {
                isAntiAlias = true
                color = Color(0xFF0F172A).toArgb()
            }
            drawCircle(badgeX, badgeY, 8.5f * sx, badgeBgPaint)

            val badgeTextPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = 10f * sx
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.WHITE
            }
            val by = badgeY - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
            drawText(stage.step.toString(), badgeX, by, badgeTextPaint)
        } else if (isLocked) {
            val lockPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = 7.5f * sx
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color(0xFF64748B).toArgb()
            }
            drawText("22:00", cx, cy + (th / 2f - 3f) * sy, lockPaint)
        }
    }
}
