package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.LudoBoardGeometry
import com.example.domain.PlayerColor
import com.example.domain.PlayerSeat
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val ClassicLudoRed = Color(0xFFE60000)
val ClassicLudoGreen = Color(0xFF009A17)
val ClassicLudoYellow = Color(0xFFFFD000)
val ClassicLudoBlue = Color(0xFF0088FF)

fun PlayerColor.toComposeColor(): Color = when (this) {
    PlayerColor.RED -> ClassicLudoRed
    PlayerColor.GREEN -> ClassicLudoGreen
    PlayerColor.YELLOW -> ClassicLudoYellow
    PlayerColor.BLUE -> ClassicLudoBlue
}

@Composable
fun LudoBoardCanvas(
    seats: List<PlayerSeat>,
    tokenPositions: List<Int>,
    animatingGlobalTokenIndex: Int?,
    animatingIntermediateStep: Int?,
    animatingTrailSteps: List<Int>,
    isHopImpulse: Boolean,
    currentTurnIndex: Int,
    legalTokenSlotsForCurrentPlayer: Set<Int>,
    safeZonesEnabled: Boolean,
    onTokenClicked: (playerIndex: Int, tokenSlot: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pawnAnimations")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .border(2.5.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .background(Color.White)
            .testTag("ludo_board_canvas")
    ) {
        val boardSizeDp = maxWidth
        val cellSizeDp = boardSizeDp / 15f
        val density = LocalDensity.current
        val cellSizePx = with(density) { cellSizeDp.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width / 15f
            val cellH = size.height / 15f

            // 1. Base 15x15 Grid Cells
            for (r in 0 until 15) {
                for (c in 0 until 15) {
                    val cellColor = resolveCellBackgroundColor(r, c)
                    drawRect(
                        color = cellColor,
                        topLeft = Offset(c * cellW, r * cellH),
                        size = Size(cellW, cellH)
                    )
                    drawRect(
                        color = Color(0xFF475569),
                        topLeft = Offset(c * cellW, r * cellH),
                        size = Size(cellW, cellH),
                        style = Stroke(width = 1.3f)
                    )
                }
            }

            // 2. Draw 4 Corner Yards matching the video layout:
            // Top-Left = Green, Top-Right = Yellow, Bottom-Left = Red, Bottom-Right = Blue
            drawPlayerYard(0, 0, ClassicLudoGreen, cellW, cellH)
            drawPlayerYard(0, 9, ClassicLudoYellow, cellW, cellH)
            drawPlayerYard(9, 0, ClassicLudoRed, cellW, cellH)
            drawPlayerYard(9, 9, ClassicLudoBlue, cellW, cellH)

            // 3. Draw Home Stretch Entry Arrows (→ Green at (7,0), ↓ Yellow at (0,7), ← Blue at (7,14), ↑ Red at (14,7))
            drawLaneEntryArrow(
                center = Offset(0.5f * cellW, 7.5f * cellH),
                radius = cellW * 0.32f,
                direction = ArrowDirection.RIGHT,
                color = ClassicLudoGreen
            )
            drawLaneEntryArrow(
                center = Offset(7.5f * cellW, 0.5f * cellH),
                radius = cellW * 0.32f,
                direction = ArrowDirection.DOWN,
                color = ClassicLudoYellow
            )
            drawLaneEntryArrow(
                center = Offset(14.5f * cellW, 7.5f * cellH),
                radius = cellW * 0.32f,
                direction = ArrowDirection.LEFT,
                color = ClassicLudoBlue
            )
            drawLaneEntryArrow(
                center = Offset(7.5f * cellW, 14.5f * cellH),
                radius = cellW * 0.32f,
                direction = ArrowDirection.UP,
                color = ClassicLudoRed
            )

            // 4. Draw Outlined 5-Point Safe Stars matching the video
            if (safeZonesEnabled) {
                val starCells = listOf(8, 21, 34, 47)
                starCells.forEach { globalIdx ->
                    val (r, c) = LudoBoardGeometry.MAIN_TRACK_COORDS[globalIdx]
                    drawOutlinedStar(
                        center = Offset((c + 0.5f) * cellW, (r + 0.5f) * cellH),
                        radius = cellW * 0.36f,
                        strokeColor = Color(0xFF334155)
                    )
                }
            }

            // 5. Draw Center 3x3 Home Triangles (Left = Green, Top = Yellow, Right = Blue, Bottom = Red)
            val centerLeft = 6f * cellW
            val centerTop = 6f * cellH
            val centerRight = 9f * cellW
            val centerBottom = 9f * cellH
            val centerMid = Offset(7.5f * cellW, 7.5f * cellH)

            // Left Triangle (Green)
            drawHomeTriangle(
                p1 = Offset(centerLeft, centerTop),
                p2 = Offset(centerLeft, centerBottom),
                p3 = centerMid,
                color = ClassicLudoGreen
            )
            // Top Triangle (Yellow)
            drawHomeTriangle(
                p1 = Offset(centerLeft, centerTop),
                p2 = Offset(centerRight, centerTop),
                p3 = centerMid,
                color = ClassicLudoYellow
            )
            // Right Triangle (Blue)
            drawHomeTriangle(
                p1 = Offset(centerRight, centerTop),
                p2 = Offset(centerRight, centerBottom),
                p3 = centerMid,
                color = ClassicLudoBlue
            )
            // Bottom Triangle (Red)
            drawHomeTriangle(
                p1 = Offset(centerLeft, centerBottom),
                p2 = Offset(centerRight, centerBottom),
                p3 = centerMid,
                color = ClassicLudoRed
            )

            // 6. Draw Glowing Trail Dots along the path of the currently hopping pawn (just like 00:02 in the video!)
            if (animatingGlobalTokenIndex != null && animatingTrailSteps.isNotEmpty()) {
                val movingPlayerIdx = animatingGlobalTokenIndex / 4
                val movingSlot = animatingGlobalTokenIndex % 4
                val movingColor = PlayerColor.fromIndex(movingPlayerIdx)
                val dotColor = movingColor.toComposeColor()

                animatingTrailSteps.forEachIndexed { idx, stepVal ->
                    val coord = LudoBoardGeometry.getBoardCoordinate(movingColor, movingSlot, stepVal)
                    val center = Offset(coord.col * cellW, coord.row * cellH)
                    val alphaFraction = ((idx + 1).toFloat() / (animatingTrailSteps.size + 1).toFloat()).coerceIn(0.25f, 0.9f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                dotColor.copy(alpha = alphaFraction),
                                dotColor.copy(alpha = alphaFraction * 0.45f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = cellW * 0.36f
                        ),
                        radius = cellW * 0.36f,
                        center = center
                    )
                    drawCircle(
                        color = dotColor.copy(alpha = alphaFraction),
                        radius = cellW * 0.14f,
                        center = center
                    )
                }
            }
        }

        // Player Name Labels on each Yard's bottom bar (just like "You" in the video!)
        seats.forEachIndexed { idx, seat ->
            if (seat.isActiveInMatch) {
                val (labelRow, labelCol) = when (seat.color) {
                    PlayerColor.GREEN -> 5.45f to 3.0f
                    PlayerColor.YELLOW -> 5.45f to 12.0f
                    PlayerColor.RED -> 14.45f to 3.0f
                    PlayerColor.BLUE -> 14.45f to 12.0f
                }
                val labelWidthDp = cellSizeDp * 5.2f
                val labelHeightDp = cellSizeDp * 0.95f
                val offsetX = ((labelCol * cellSizePx) - with(density) { labelWidthDp.toPx() / 2f }).roundToInt()
                val offsetY = ((labelRow * cellSizePx) - with(density) { labelHeightDp.toPx() / 2f }).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX, offsetY) }
                        .size(labelWidthDp, labelHeightDp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = seat.name,
                        color = if (seat.color == PlayerColor.YELLOW) Color(0xFF1E293B) else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        // 7. Render 3D Teardrop Map-Pin Pawns on top of the board
        for (playerIdx in 0 until 4) {
            val seat = seats.getOrNull(playerIdx) ?: continue
            if (!seat.isActiveInMatch) continue
            val color = seat.color
            val tokenColor = color.toComposeColor()

            for (slot in 0 until 4) {
                val globalIdx = playerIdx * 4 + slot
                val isCurrentlyAnimating = animatingGlobalTokenIndex == globalIdx
                val effectiveStep = if (isCurrentlyAnimating && animatingIntermediateStep != null) {
                    animatingIntermediateStep
                } else {
                    tokenPositions.getOrElse(globalIdx) { -1 }
                }

                val targetCoord = LudoBoardGeometry.getBoardCoordinate(color, slot, effectiveStep)

                val duplicateShift = if (effectiveStep in 0..55 && !isCurrentlyAnimating) {
                    val siblingsOnSameStep = (0 until slot).count { prevSlot ->
                        tokenPositions.getOrElse(playerIdx * 4 + prevSlot) { -1 } == effectiveStep
                    }
                    siblingsOnSameStep * 0.13f
                } else {
                    0f
                }

                val animatedRow by animateFloatAsState(
                    targetValue = targetCoord.row + duplicateShift,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "tokenRow_$globalIdx"
                )
                val animatedCol by animateFloatAsState(
                    targetValue = targetCoord.col + duplicateShift,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "tokenCol_$globalIdx"
                )

                val hopScale by animateFloatAsState(
                    targetValue = if (isCurrentlyAnimating && isHopImpulse) 1.32f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessHigh
                    ),
                    label = "hopScale_$globalIdx"
                )
                val hopElevationOffsetPx by animateFloatAsState(
                    targetValue = if (isCurrentlyAnimating && isHopImpulse) -cellSizePx * 0.34f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessHigh
                    ),
                    label = "hopArc_$globalIdx"
                )

                val isLegalForActiveTurn = (playerIdx == currentTurnIndex) &&
                    (slot in legalTokenSlotsForCurrentPlayer) &&
                    !seat.isAi

                val scaleMultiplier = when {
                    isCurrentlyAnimating -> hopScale
                    isLegalForActiveTurn -> pulseScale
                    else -> 1.0f
                }

                val pawnBoxSizeDp = cellSizeDp * 1.18f * scaleMultiplier
                val pawnBoxSizePx = with(density) { pawnBoxSizeDp.toPx() }

                val offsetX = (animatedCol * cellSizePx - pawnBoxSizePx / 2f).roundToInt()
                // Anchor the pin's bottom tip near the center of the cell
                val offsetY = (animatedRow * cellSizePx - pawnBoxSizePx * 0.62f + hopElevationOffsetPx).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX, offsetY) }
                        .size(pawnBoxSizeDp)
                        .clip(CircleShape)
                        .clickable(enabled = isLegalForActiveTurn) {
                            onTokenClicked(playerIdx, slot)
                        }
                        .semantics {
                            contentDescription = "${seat.name} Token ${slot + 1}"
                        }
                        .testTag("token_${playerIdx}_${slot}"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawTeardropLudoPawn(
                            houseColor = tokenColor,
                            showDashedBaseRing = effectiveStep < 0 || isLegalForActiveTurn,
                            isHighlightedLegal = isLegalForActiveTurn,
                            ringRotationDegrees = ringRotation
                        )
                    }
                }
            }
        }
    }
}

fun DrawScope.drawTeardropLudoPawn(
    houseColor: Color,
    showDashedBaseRing: Boolean,
    isHighlightedLegal: Boolean,
    ringRotationDegrees: Float
) {
    val w = size.width
    val h = size.height
    val baseCenter = Offset(w * 0.5f, h * 0.76f)

    // 1. Dashed circular base ring (visible in yard and when selectable, just like in the video)
    if (showDashedBaseRing) {
        rotate(degrees = if (isHighlightedLegal) ringRotationDegrees else 0f, pivot = baseCenter) {
            drawCircle(
                color = if (isHighlightedLegal) Color(0xFFFFD700) else Color(0xFF1E293B),
                radius = w * 0.28f,
                center = baseCenter,
                style = Stroke(
                    width = w * 0.065f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.11f, w * 0.08f), 0f)
                )
            )
        }
    }

    // 2. Drop shadow ellipse under pawn tip
    drawOval(
        color = Color.Black.copy(alpha = 0.28f),
        topLeft = Offset(w * 0.28f, h * 0.68f),
        size = Size(w * 0.44f, h * 0.16f)
    )

    // 3. White glossy Teardrop Map-Pin silhouette
    val headCenterY = h * 0.36f
    val headRadius = w * 0.31f
    val tipY = h * 0.77f

    val pinPath = Path().apply {
        moveTo(w * 0.5f, tipY)
        cubicTo(
            w * 0.28f, h * 0.58f,
            w * 0.19f, headCenterY + headRadius * 0.4f,
            w * 0.19f, headCenterY
        )
        cubicTo(
            w * 0.19f, headCenterY - headRadius * 1.1f,
            w * 0.81f, headCenterY - headRadius * 1.1f,
            w * 0.81f, headCenterY
        )
        cubicTo(
            w * 0.81f, headCenterY + headRadius * 0.4f,
            w * 0.72f, h * 0.58f,
            w * 0.5f, tipY
        )
        close()
    }

    // Fill pin body with subtle 3D silver-white gradient
    drawPath(
        path = pinPath,
        brush = Brush.linearGradient(
            colors = listOf(Color.White, Color(0xFFE2E8F0), Color(0xFFCBD5E1)),
            start = Offset(w * 0.25f, h * 0.15f),
            end = Offset(w * 0.75f, tipY)
        )
    )

    // Outer dark metallic rim
    drawPath(
        path = pinPath,
        color = if (isHighlightedLegal) Color(0xFFB45309) else Color(0xFF334155),
        style = Stroke(width = w * 0.055f)
    )

    // 4. Inner vibrant house-colored gem circle inside the teardrop head
    val gemCenter = Offset(w * 0.5f, headCenterY + h * 0.01f)
    val gemRadius = w * 0.185f
    drawCircle(
        color = houseColor,
        radius = gemRadius,
        center = gemCenter
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.45f),
        radius = gemRadius * 0.38f,
        center = Offset(gemCenter.x - gemRadius * 0.28f, gemCenter.y - gemRadius * 0.28f)
    )
}

private enum class ArrowDirection { RIGHT, DOWN, LEFT, UP }

private fun DrawScope.drawLaneEntryArrow(
    center: Offset,
    radius: Float,
    direction: ArrowDirection,
    color: Color
) {
    val strokeW = radius * 0.26f
    when (direction) {
        ArrowDirection.RIGHT -> {
            drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeW, StrokeCap.Round)
            drawLine(color, Offset(center.x + radius * 0.2f, center.y - radius * 0.65f), Offset(center.x + radius, center.y), strokeW, StrokeCap.Round)
            drawLine(color, Offset(center.x + radius * 0.2f, center.y + radius * 0.65f), Offset(center.x + radius, center.y), strokeW, StrokeCap.Round)
        }
        ArrowDirection.LEFT -> {
            drawLine(color, Offset(center.x + radius, center.y), Offset(center.x - radius, center.y), strokeW, StrokeCap.Round)
            drawLine(color, Offset(center.x - radius * 0.2f, center.y - radius * 0.65f), Offset(center.x - radius, center.y), strokeW, StrokeCap.Round)
            drawLine(color, Offset(center.x - radius * 0.2f, center.y + radius * 0.65f), Offset(center.x - radius, center.y), strokeW, StrokeCap.Round)
        }
        ArrowDirection.UP -> {
            drawLine(color, Offset(center.x, center.y + radius), Offset(center.x, center.y - radius), strokeW, StrokeCap.Round)
            drawLine(color, Offset(center.x - radius * 0.65f, center.y - radius * 0.2f), Offset(center.x, center.y - radius), strokeW, StrokeCap.Round)
            drawLine(color, Offset(center.x + radius * 0.65f, center.y - radius * 0.2f), Offset(center.x, center.y - radius), strokeW, StrokeCap.Round)
        }
        ArrowDirection.DOWN -> {
            drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeW, StrokeCap.Round)
            drawLine(color, Offset(center.x - radius * 0.65f, center.y + radius * 0.2f), Offset(center.x, center.y + radius), strokeW, StrokeCap.Round)
            drawLine(color, Offset(center.x + radius * 0.65f, center.y + radius * 0.2f), Offset(center.x, center.y + radius), strokeW, StrokeCap.Round)
        }
    }
}

private fun resolveCellBackgroundColor(r: Int, c: Int): Color {
    // Top-Left Green starting square & home stretch
    if (r == 6 && c == 1) return ClassicLudoGreen
    if (r == 7 && c in 1..5) return ClassicLudoGreen

    // Top-Right Yellow starting square & home stretch
    if (r == 1 && c == 8) return ClassicLudoYellow
    if (r in 1..5 && c == 7) return ClassicLudoYellow

    // Bottom-Right Blue starting square & home stretch
    if (r == 8 && c == 13) return ClassicLudoBlue
    if (r == 7 && c in 9..13) return ClassicLudoBlue

    // Bottom-Left Red starting square & home stretch
    if (r == 13 && c == 6) return ClassicLudoRed
    if (r in 9..13 && c == 7) return ClassicLudoRed

    return Color.White
}

private fun DrawScope.drawPlayerYard(
    startRow: Int,
    startCol: Int,
    houseColor: Color,
    cellW: Float,
    cellH: Float
) {
    val left = startCol * cellW
    val top = startRow * cellH
    val width = 6f * cellW
    val height = 6f * cellH

    drawRect(
        color = houseColor,
        topLeft = Offset(left, top),
        size = Size(width, height)
    )
    drawRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = 2.5f)
    )

    // Crisp inner white square matching the video's yard box
    val padX = cellW * 0.95f
    val padTop = cellH * 0.85f
    val padBottom = cellH * 1.05f
    drawRect(
        color = Color.White,
        topLeft = Offset(left + padX, top + padTop),
        size = Size(width - 2 * padX, height - padTop - padBottom)
    )
    drawRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(left + padX, top + padTop),
        size = Size(width - 2 * padX, height - padTop - padBottom),
        style = Stroke(width = 2f)
    )

    // 4 colored token spots inside the white box
    val pocketOffsets = listOf(
        Offset((startCol + 1.85f) * cellW, (startRow + 1.8f) * cellH),
        Offset((startCol + 4.15f) * cellW, (startRow + 1.8f) * cellH),
        Offset((startCol + 1.85f) * cellW, (startRow + 3.9f) * cellH),
        Offset((startCol + 4.15f) * cellW, (startRow + 3.9f) * cellH)
    )
    pocketOffsets.forEach { center ->
        drawCircle(
            color = houseColor,
            radius = cellW * 0.44f,
            center = center
        )
        drawCircle(
            color = Color(0xFF1E293B),
            radius = cellW * 0.44f,
            center = center,
            style = Stroke(width = 1.8f)
        )
    }
}

private fun DrawScope.drawHomeTriangle(
    p1: Offset,
    p2: Offset,
    p3: Offset,
    color: Color
) {
    val path = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        close()
    }
    drawPath(path = path, color = color)
    drawPath(path = path, color = Color(0xFF1E293B), style = Stroke(width = 2.2f))
}

private fun DrawScope.drawOutlinedStar(
    center: Offset,
    radius: Float,
    strokeColor: Color
) {
    val path = Path()
    val points = 10
    for (i in 0 until points) {
        val r = if (i % 2 == 0) radius else radius * 0.42f
        val angle = Math.toRadians((i * 36.0) - 90.0)
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = Color.White)
    drawPath(path = path, color = strokeColor, style = Stroke(width = 1.8f))
}
