package com.cropexpert.app.ui.splash

import android.app.Activity
import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cropexpert.app.ui.theme.AgriculturalGreen
import com.cropexpert.app.ui.theme.MidnightBlue
import com.cropexpert.app.ui.theme.White70
import kotlinx.coroutines.delay

/**
 * Refined Shield Shape with elegant organic curves and a distinct sharp bottom vertex.
 */
val ShieldShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val topRadius = w * 0.2f
    
    moveTo(topRadius, 0f)
    lineTo(w - topRadius, 0f)
    quadraticBezierTo(w, 0f, w, topRadius)
    lineTo(w, h * 0.4f)
    // Curving gently inward before meeting at a sharp point
    quadraticBezierTo(w * 0.95f, h * 0.85f, w * 0.5f, h)
    quadraticBezierTo(w * 0.05f, h * 0.85f, 0f, h * 0.4f)
    lineTo(0f, topRadius)
    quadraticBezierTo(0f, 0f, topRadius, 0f)
    close()
}

/**
 * Custom Logo Component drawn entirely on Canvas for high-fidelity geometry.
 */
@Composable
fun LogoComponent(modifier: Modifier = Modifier) {
    val leafPath = remember { Path() }
    val stemPath = remember { Path() }
    val swooshPath = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Orbital "Swooshes" (Dynamic flight paths)
        swooshPath.reset()
        swooshPath.moveTo(w * 0.1f, h * 0.6f)
        swooshPath.quadraticBezierTo(w * 0.3f, h * 0.15f, w * 0.9f, h * 0.35f)
        drawPath(swooshPath, Color.White, alpha = 0.3f, style = Stroke(width = 1.dp.toPx()))

        swooshPath.reset()
        swooshPath.moveTo(w * 0.2f, h * 0.85f)
        swooshPath.quadraticBezierTo(w * 0.85f, h * 0.85f, w * 0.75f, h * 0.1f)
        drawPath(swooshPath, Color.White, alpha = 0.2f, style = Stroke(width = 1.dp.toPx()))

        // 2. Internal Sparkles (Repositioned stars)
        drawCircle(Color.White, radius = 1.5.dp.toPx(), center = Offset(w * 0.25f, h * 0.35f)) // Near leaf tip
        drawCircle(Color.White, radius = 1.5.dp.toPx(), center = Offset(w * 0.88f, h * 0.42f)) // Near satellite
        drawCircle(Color.White, radius = 1.2.dp.toPx(), center = Offset(w * 0.5f, h * 0.1f))   // Top center

        // 3. Organic Stem & Leaf (Bottom-left)
        stemPath.reset()
        stemPath.moveTo(w * 0.5f, h * 0.9f) // Rooted at bottom center
        stemPath.quadraticBezierTo(w * 0.5f, h * 0.8f, w * 0.35f, h * 0.75f)
        drawPath(stemPath, Color(0xFF2E7D32), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        leafPath.reset()
        leafPath.moveTo(w * 0.35f, h * 0.75f)
        // Organic teardrop shape pointing top-left
        leafPath.quadraticBezierTo(w * 0.05f, h * 0.65f, w * 0.25f, h * 0.35f) // Full organic curve, sharp tip
        leafPath.quadraticBezierTo(w * 0.55f, h * 0.6f, w * 0.35f, h * 0.75f)
        leafPath.close()

        val leafBrush = Brush.linearGradient(
            colors = listOf(Color(0xFF8BC34A), Color(0xFF2E7D32)),
            start = Offset(w * 0.35f, h * 0.75f),
            end = Offset(w * 0.25f, h * 0.35f)
        )
        drawPath(leafPath, leafBrush)
        
        // Leaf central vein
        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(w * 0.35f, h * 0.75f),
            end = Offset(w * 0.25f, h * 0.35f),
            strokeWidth = 0.8.dp.toPx()
        )

        // 4. Tilted & Segmented Satellite (Top-right)
        val satCenterX = w * 0.7f
        val satCenterY = h * 0.3f
        
        rotate(degrees = -35f, pivot = Offset(satCenterX, satCenterY)) {
            val bodyW = w * 0.13f
            val bodyH = h * 0.08f
            val amber = Color(0xFFFFB74D)
            val gap = 2.dp.toPx()

            // White Body
            drawRect(Color.White, Offset(satCenterX - bodyW / 2, satCenterY - bodyH / 2), Size(bodyW, bodyH))

            // Segmented Solar Panels (2 distinct rectangles on each side)
            val panelW = bodyW * 0.5f
            val panelH = bodyH * 1.5f
            
            // Left panels
            drawRect(amber, Offset(satCenterX - bodyW/2 - panelW - gap, satCenterY - panelH/2), Size(panelW, panelH))
            drawRect(amber, Offset(satCenterX - bodyW/2 - panelW*2 - gap*2, satCenterY - panelH/2), Size(panelW, panelH))
            
            // Right panels
            drawRect(amber, Offset(satCenterX + bodyW/2 + gap, satCenterY - panelH/2), Size(panelW, panelH))
            drawRect(amber, Offset(satCenterX + bodyW/2 + panelW + gap*2, satCenterY - panelH/2), Size(panelW, panelH))
        }
    }
}

@Composable
fun SplashScreen(onNavigateToMain: () -> Unit) {
    var isConnected by remember { mutableStateOf(false) }
    val view = LocalView.current
    
    // Animation for pulsing logo scale and amber glow aura
    val infiniteTransition = rememberInfiniteTransition(label = "LogoAnimation")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoScale"
    )

    // Dynamic Dot Animation State
    val dotCount by infiniteTransition.animateValue(
        initialValue = 1,
        targetValue = 4,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DotAnimation"
    )
    val dots = ".".repeat(dotCount)

    // Make Splash Screen Immersive (Hide system UI)
    LaunchedEffect(Unit) {
        if (!view.isInEditMode) {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // Logic for transition phases
    LaunchedEffect(Unit) {
        delay(3000) // Connecting phase
        isConnected = true
        delay(2000) // Loading phase
        onNavigateToMain()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(MidnightBlue, AgriculturalGreen)
                )
            )
    ) {
        // Star field background detail
        Canvas(modifier = Modifier.fillMaxSize()) {
            val starAlpha = 0.3f
            val stars = listOf(
                Offset(0.12f, 0.15f), Offset(0.88f, 0.2f), Offset(0.25f, 0.35f),
                Offset(0.75f, 0.08f), Offset(0.05f, 0.45f), Offset(0.95f, 0.3f)
            )
            stars.forEach { pos ->
                drawCircle(
                    color = Color.White.copy(alpha = starAlpha),
                    radius = 1.5.dp.toPx(),
                    center = Offset(pos.x * size.width, pos.y * size.height)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo Centerpiece with the Shield and Amber Glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .scale(logoScale)
            ) {
                // Amber Glow aura effect behind the shield
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .drawBehind {
                            drawIntoCanvas { canvas ->
                                val paint = Paint().asFrameworkPaint()
                                paint.color = Color(0xFFFFB74D).copy(alpha = glowAlpha).toArgb()
                                paint.maskFilter = BlurMaskFilter(120f, BlurMaskFilter.Blur.NORMAL)
                                canvas.nativeCanvas.drawCircle(size.width / 2, size.height / 2, size.width / 2.4f, paint)
                            }
                        }
                )

                // The Shield Body
                Box(
                    modifier = Modifier
                        .size(185.dp) // Refined for visual prominence
                        .clip(ShieldShape)
                        .background(Color(0xFF0D47A1)) // NASA style deep blue
                        .border(3.dp, Color.White.copy(alpha = 0.9f), ShieldShape),
                    contentAlignment = Alignment.Center
                ) {
                    LogoComponent(modifier = Modifier.fillMaxSize().padding(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Brand Typography refined to match image (Stacked Block Style)
            Text(
                text = "CROP\nEXPERT",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 64.sp,
                    lineHeight = 52.sp, // Vertically compressed for dense look
                    fontWeight = FontWeight.Black, // Extra heavy geometric weight
                    letterSpacing = (-1).sp, // Tightened tracking
                    fontFamily = FontFamily.SansSerif
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Powered by NASA\nEarth Observations",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Multi-language taglines with reduced focus (alpha = 0.5f)
            Text(
                text = "බෝග උපදේශක | பயிர் ஆலோசகர்",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }

        // Bottom Progress Section with dynamic dot text
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 70.dp)
                .fillMaxWidth(0.7f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (!isConnected) "Connecting $dots" else "Loading $dots",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(MaterialTheme.shapes.small),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = false)
@Composable
fun SplashScreenPreview() {
    SplashScreen(onNavigateToMain = {})
}
