package com.cropexpert.app.ui.farm

import android.content.Context
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Base64
import android.view.MotionEvent
import com.cropexpert.app.domain.model.FarmTerrainResponse
import com.google.android.gms.maps.model.LatLng
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal class TerrainSurfaceView(
    context: Context,
    private val onFieldLongPress: (LatLng) -> Unit
) : GLSurfaceView(context) {
    private val terrainRenderer = TerrainRenderer()
    private var lastTerrain: FarmTerrainResponse? = null
    private var lastBoundary: List<LatLng> = emptyList()
    private var startX = 0f
    private var startY = 0f
    private var previousX = 0f
    private var previousY = 0f
    private var downTime = 0L
    private var moved = false
    private var previousPinchDistance = 0f

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(terrainRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        isClickable = true
    }

    fun showTerrain(terrain: FarmTerrainResponse?, boundary: List<LatLng>) {
        if (terrain === lastTerrain && boundary == lastBoundary) return
        lastTerrain = terrain
        lastBoundary = boundary.toList()
        queueEvent {
            terrainRenderer.setTerrain(terrain, boundary)
            requestRender()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                previousX = event.x
                previousY = event.y
                downTime = event.eventTime
                moved = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                previousPinchDistance = pointerDistance(event)
                moved = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val distance = pointerDistance(event)
                    if (previousPinchDistance > 0 && distance > 0) {
                        terrainRenderer.zoom(previousPinchDistance / distance)
                        requestRender()
                    }
                    previousPinchDistance = distance
                    moved = true
                } else {
                    val dx = event.x - previousX
                    val dy = event.y - previousY
                    terrainRenderer.orbit(dx, dy)
                    requestRender()
                    if (distance(event.x, event.y, startX, startY) > 18f) moved = true
                }
                previousX = event.x
                previousY = event.y
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                previousPinchDistance = 0f
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && event.eventTime - downTime >= 550) {
                    terrainRenderer.fieldPointAt(event.x, event.y, width, height)?.let(onFieldLongPress)
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
}

private class TerrainRenderer : GLSurfaceView.Renderer {
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val inverseViewProjection = FloatArray(16)
    private var program = 0
    private var textureId = 0
    private var boundary: List<LatLng> = emptyList()
    private var texturePolygon: List<Point2> = emptyList()
    private var topVertices: FloatBuffer? = null
    private var topVertexCount = 0
    private var sideVertices: FloatBuffer? = null
    private var sideVertexCount = 0
    private var bottomVertices: FloatBuffer? = null
    private var bottomVertexCount = 0
    private var imageBytes: ByteArray? = null
    private var longitudeSpan = 0.0
    private var latitudeSpan = 0.0
    private var minLongitude = 0.0
    private var maxLatitude = 0.0
    private var widthWorld = 2f
    private var heightWorld = 2f
    @Volatile private var yaw = -38f
    @Volatile private var pitch = 38f
    @Volatile private var cameraDistance = 3.8f
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    fun setTerrain(value: FarmTerrainResponse?, fieldBoundary: List<LatLng>) {
        boundary = fieldBoundary
        if (value != null && fieldBoundary.size >= 3) {
            imageBytes = try {
                Base64.decode(value.texturePngBase64, Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                null
            }
            buildMesh(value, fieldBoundary)
        } else {
            topVertices = null
            sideVertices = null
            bottomVertices = null
            imageBytes = null
        }
    }

    fun orbit(dx: Float, dy: Float) {
        yaw = (yaw + dx * 0.55f) % 360f
        pitch = (pitch - dy * 0.45f).coerceIn(12f, 76f)
    }

    fun zoom(scale: Float) {
        if (scale.isFinite() && scale > 0) {
            cameraDistance = (cameraDistance * scale).coerceIn(2.1f, 8f)
        }
    }

    fun fieldPointAt(screenX: Float, screenY: Float, width: Int, height: Int): LatLng? {
        if (width <= 0 || height <= 0 || boundary.size < 3 || longitudeSpan <= 0 || latitudeSpan <= 0) {
            return null
        }
        val near = unproject(screenX, screenY, 0f, width, height) ?: return null
        val far = unproject(screenX, screenY, 1f, width, height) ?: return null
        val deltaY = far[1] - near[1]
        if (kotlin.math.abs(deltaY) < 1e-6f) return null
        val fraction = (0.32f - near[1]) / deltaY
        if (fraction !in 0f..1f) return null
        val x = near[0] + (far[0] - near[0]) * fraction
        val z = near[2] + (far[2] - near[2]) * fraction
        val u = x / widthWorld + 0.5
        val v = 0.5 - z / heightWorld
        val candidate = LatLng(
            maxLatitude - v * latitudeSpan,
            minLongitude + u * longitudeSpan
        )
        return candidate.takeIf { isInside(it, boundary) }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.86f, 0.91f, 0.84f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        textureId = 0
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uploadTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = max(1, width)
        surfaceHeight = max(1, height)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        val aspect = surfaceWidth.toFloat() / surfaceHeight
        val fov = 39f
        val top = (kotlin.math.tan(Math.toRadians((fov / 2).toDouble())) * 0.1).toFloat()
        val right = top * aspect
        Matrix.frustumM(projection, 0, -right, right, -top, top, 0.1f, 30f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (topVertexCount == 0) return

        val angle = Math.toRadians(yaw.toDouble())
        val pitchRadians = Math.toRadians(pitch.toDouble())
        val horizontal = cameraDistance * cos(pitchRadians).toFloat()
        val cameraX = sin(angle).toFloat() * horizontal
        val cameraZ = cos(angle).toFloat() * horizontal
        Matrix.setLookAtM(
            view, 0,
            cameraX, cameraDistance * sin(pitchRadians).toFloat() + 0.2f, cameraZ,
            0f, 0.2f, 0f,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        Matrix.invertM(inverseViewProjection, 0, viewProjection, 0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(
            GLES20.glGetUniformLocation(program, "uMvp"),
            1,
            false,
            viewProjection,
            0
        )
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "uLight"), -0.45f, 0.85f, 0.5f)
        drawBuffer(topVertices, topVertexCount, textured = true)
        drawBuffer(sideVertices, sideVertexCount, textured = false)
        drawBuffer(bottomVertices, bottomVertexCount, textured = false)
    }

    private fun buildMesh(data: FarmTerrainResponse, points: List<LatLng>) {
        if (data.bounds.size != 4 || data.elevationGrid.size < 2 ||
            data.elevationGrid.any { it.size < 2 }
        ) {
            topVertexCount = 0
            return
        }
        minLongitude = data.bounds[0]
        val minLatitude = data.bounds[1]
        val maxLongitude = data.bounds[2]
        maxLatitude = data.bounds[3]
        longitudeSpan = maxLongitude - minLongitude
        latitudeSpan = maxLatitude - minLatitude
        if (longitudeSpan <= 0 || latitudeSpan <= 0) {
            topVertexCount = 0
            return
        }
        val meanLatitude = Math.toRadians((minLatitude + maxLatitude) / 2)
        val widthMeters = longitudeSpan * 111_320 * cos(meanLatitude)
        val heightMeters = latitudeSpan * 111_320
        val longestSide = max(widthMeters, heightMeters).coerceAtLeast(1.0)
        widthWorld = (2 * widthMeters / longestSide).toFloat().coerceAtLeast(0.08f)
        heightWorld = (2 * heightMeters / longestSide).toFloat().coerceAtLeast(0.08f)
        val rows = data.elevationGrid.size
        val columns = data.elevationGrid.minOf { it.size }
        val validElevations = data.elevationGrid.flatten().filterNotNull().filter(Double::isFinite)
        val fieldMean = validElevations.average().takeIf(Double::isFinite) ?: 0.0
        val elevations = data.elevationGrid.map { row ->
            row.take(columns).map { it?.takeIf(Double::isFinite) ?: fieldMean }
        }
        val low = validElevations.minOrNull() ?: fieldMean
        val high = validElevations.maxOrNull() ?: low
        val relief = max(high - low, 0.75)
        val verticalScale = 0.72 / relief
        val bottomY = -0.18f
        val projectedPoints = points.map { point ->
            Point2(
                ((point.longitude - minLongitude) / longitudeSpan).toFloat(),
                ((maxLatitude - point.latitude) / latitudeSpan).toFloat()
            )
        }
        texturePolygon = projectedPoints
        val top = FloatList()
        val gridSize = 128
        val fieldTriangles = triangulate(projectedPoints).map { triangle ->
            listOf(
                projectedPoints[triangle.first],
                projectedPoints[triangle.second],
                projectedPoints[triangle.third]
            )
        }
        for (row in 0 until gridSize - 1) {
            for (column in 0 until gridSize - 1) {
                val left = column.toFloat() / (gridSize - 1)
                val right = (column + 1).toFloat() / (gridSize - 1)
                val topEdge = row.toFloat() / (gridSize - 1)
                val bottomEdge = (row + 1).toFloat() / (gridSize - 1)
                fieldTriangles.forEach { triangle ->
                    if (triangle.maxOf { it.x } < left || triangle.minOf { it.x } > right ||
                        triangle.maxOf { it.y } < topEdge || triangle.minOf { it.y } > bottomEdge
                    ) return@forEach
                    val clipped = clipToCell(triangle, left, topEdge, right, bottomEdge)
                    if (clipped.size >= 3) {
                        val first = vertexAt(clipped.first(), elevations, columns, rows, low, verticalScale)
                        for (index in 1 until clipped.lastIndex) {
                            val second = vertexAt(clipped[index], elevations, columns, rows, low, verticalScale)
                            val third = vertexAt(clipped[index + 1], elevations, columns, rows, low, verticalScale)
                            appendTriangle(top, first, second, third)
                        }
                    }
                }
            }
        }
        topVertices = top.toBuffer()
        topVertexCount = top.count

        val sides = FloatList()
        val bottom = FloatList()
        val lowBottom = bottomY
        val polygonVertices = projectedPoints.map { point ->
            val elevation = sampleGrid(elevations, point.x.toDouble(), point.y.toDouble(), columns, rows)
            Vertex(
                x = ((point.x - 0.5f) * widthWorld).toFloat(),
                y = ((elevation - low) * verticalScale).toFloat(),
                z = ((0.5f - point.y) * heightWorld).toFloat(),
                u = point.x,
                v = point.y
            )
        }
        for (index in polygonVertices.indices) {
            val first = polygonVertices[index]
            val next = polygonVertices[(index + 1) % polygonVertices.size]
            val lowerFirst = first.copy(y = lowBottom)
            val lowerNext = next.copy(y = lowBottom)
            appendSolidQuad(sides, first, next, lowerFirst, lowerNext)
        }
        triangulate(projectedPoints).forEach { triangle ->
            val vertices = listOf(triangle.first, triangle.second, triangle.third).map { index ->
                val point = projectedPoints[index]
                val upper = polygonVertices[index]
                Vertex(upper.x, lowBottom, upper.z, point.x, point.y)
            }
            appendSolidTriangle(bottom, vertices[0], vertices[1], vertices[2])
        }
        sideVertices = sides.toBuffer()
        sideVertexCount = sides.count
        bottomVertices = bottom.toBuffer()
        bottomVertexCount = bottom.count
        uploadTexture()
    }

    private fun uploadTexture() {
        val bytes = imageBytes ?: return
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val bitmap = android.graphics.Bitmap.createBitmap(
            source.width,
            source.height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val clip = Path().apply {
            texturePolygon.forEachIndexed { index, point ->
                val x = point.x * source.width
                val y = point.y * source.height
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        canvas.clipPath(clip)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        if (textureId == 0) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            textureId = ids[0]
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        source.recycle()
        bitmap.recycle()
    }

    private fun drawBuffer(buffer: FloatBuffer?, count: Int, textured: Boolean) {
        if (buffer == null || count == 0) return
        buffer.position(0)
        val stride = 8 * 4
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val normal = GLES20.glGetAttribLocation(program, "aNormal")
        val uv = GLES20.glGetAttribLocation(program, "aUv")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(3)
        GLES20.glEnableVertexAttribArray(normal)
        GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(6)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, stride, buffer)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uUseTexture"), if (textured) 1 else 0)
        GLES20.glUniform4f(
            GLES20.glGetUniformLocation(program, "uSolidColor"),
            if (textured) 1f else 0.34f,
            if (textured) 1f else 0.24f,
            if (textured) 1f else 0.15f,
            1f
        )
        if (textured && textureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(normal)
        GLES20.glDisableVertexAttribArray(uv)
    }

    private fun appendTriangle(target: FloatList, a: Vertex, b: Vertex, c: Vertex) {
        val normal = faceNormal(a, b, c)
        if (normal[1] >= 0f) {
            target.add(a, normal)
            target.add(b, normal)
            target.add(c, normal)
        } else {
            val upwardNormal = faceNormal(a, c, b)
            target.add(a, upwardNormal)
            target.add(c, upwardNormal)
            target.add(b, upwardNormal)
        }
    }

    private fun appendSolidTriangle(target: FloatList, a: Vertex, b: Vertex, c: Vertex) {
        val normal = faceNormal(a, b, c)
        target.add(a, normal)
        target.add(b, normal)
        target.add(c, normal)
    }

    private fun appendSolidQuad(target: FloatList, a: Vertex, b: Vertex, c: Vertex, d: Vertex) {
        appendSolidTriangle(target, a, c, b)
        appendSolidTriangle(target, b, c, d)
    }

    private fun faceNormal(a: Vertex, b: Vertex, c: Vertex): FloatArray {
        val ux = b.x - a.x
        val uy = b.y - a.y
        val uz = b.z - a.z
        val vx = c.x - a.x
        val vy = c.y - a.y
        val vz = c.z - a.z
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        return floatArrayOf(nx / length, ny / length, nz / length)
    }

    private fun sampleGrid(
        values: List<List<Double>>,
        u: Double,
        v: Double,
        columns: Int,
        rows: Int
    ): Double {
        val px = (u.coerceIn(0.0, 1.0) * (columns - 1))
        val py = (v.coerceIn(0.0, 1.0) * (rows - 1))
        val x0 = px.toInt()
        val y0 = py.toInt()
        val x1 = min(x0 + 1, columns - 1)
        val y1 = min(y0 + 1, rows - 1)
        val fx = px - x0
        val fy = py - y0
        val top = values[y0][x0] * (1 - fx) + values[y0][x1] * fx
        val bottom = values[y1][x0] * (1 - fx) + values[y1][x1] * fx
        return top * (1 - fy) + bottom * fy
    }

    private fun vertexAt(
        point: Point2,
        elevations: List<List<Double>>,
        columns: Int,
        rows: Int,
        low: Double,
        verticalScale: Double
    ): Vertex {
        val height = sampleGrid(elevations, point.x.toDouble(), point.y.toDouble(), columns, rows)
        return Vertex(
            x = ((point.x - 0.5f) * widthWorld).toFloat(),
            y = ((height - low) * verticalScale).toFloat(),
            z = ((0.5f - point.y) * heightWorld).toFloat(),
            u = point.x,
            v = point.y
        )
    }

    private fun clipToCell(
        triangle: List<Point2>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): List<Point2> {
        var polygon = triangle
        polygon = clip(polygon, { it.x >= left }) { a, b ->
            interpolateAtX(a, b, left)
        }
        polygon = clip(polygon, { it.x <= right }) { a, b ->
            interpolateAtX(a, b, right)
        }
        polygon = clip(polygon, { it.y >= top }) { a, b ->
            interpolateAtY(a, b, top)
        }
        return clip(polygon, { it.y <= bottom }) { a, b ->
            interpolateAtY(a, b, bottom)
        }
    }

    private fun clip(
        polygon: List<Point2>,
        isInside: (Point2) -> Boolean,
        intersection: (Point2, Point2) -> Point2
    ): List<Point2> {
        if (polygon.isEmpty()) return emptyList()
        val clipped = mutableListOf<Point2>()
        var previous = polygon.last()
        var previousInside = isInside(previous)
        polygon.forEach { current ->
            val currentInside = isInside(current)
            if (currentInside) {
                if (!previousInside) clipped.add(intersection(previous, current))
                clipped.add(current)
            } else if (previousInside) {
                clipped.add(intersection(previous, current))
            }
            previous = current
            previousInside = currentInside
        }
        return clipped
    }

    private fun interpolateAtX(a: Point2, b: Point2, x: Float): Point2 {
        val fraction = (x - a.x) / (b.x - a.x)
        return Point2(x, a.y + fraction * (b.y - a.y))
    }

    private fun interpolateAtY(a: Point2, b: Point2, y: Float): Point2 {
        val fraction = (y - a.y) / (b.y - a.y)
        return Point2(a.x + fraction * (b.x - a.x), y)
    }

    private fun unproject(x: Float, y: Float, depth: Float, width: Int, height: Int): FloatArray? {
        val input = floatArrayOf(
            2f * x / width - 1f,
            1f - 2f * y / height,
            2f * depth - 1f,
            1f
        )
        val output = FloatArray(4)
        Matrix.multiplyMV(output, 0, inverseViewProjection, 0, input, 0)
        if (kotlin.math.abs(output[3]) < 1e-6f) return null
        return floatArrayOf(output[0] / output[3], output[1] / output[3], output[2] / output[3])
    }

    private fun isInside(point: Point2, polygon: List<Point2>): Boolean {
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            if ((current.y > point.y) != (previous.y > point.y) &&
                point.x < (previous.x - current.x) * (point.y - current.y) /
                (previous.y - current.y) + current.x
            ) inside = !inside
            previous = current
        }
        return inside
    }

    private fun isInside(point: LatLng, polygon: List<LatLng>): Boolean {
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            if ((current.latitude > point.latitude) != (previous.latitude > point.latitude) &&
                point.longitude < (previous.longitude - current.longitude) *
                (point.latitude - current.latitude) /
                (previous.latitude - current.latitude) + current.longitude
            ) inside = !inside
            previous = current
        }
        return inside
    }

    private fun triangulate(points: List<Point2>): List<Triple<Int, Int, Int>> {
        if (points.size < 3) return emptyList()
        val remaining = points.indices.toMutableList()
        var simplified = true
        while (simplified && remaining.size > 3) {
            simplified = false
            for (position in remaining.indices) {
                val previous = remaining[(position - 1 + remaining.size) % remaining.size]
                val current = remaining[position]
                val next = remaining[(position + 1) % remaining.size]
                if (kotlin.math.abs(cross(points[previous], points[current], points[next])) <= 1e-8f) {
                    remaining.removeAt(position)
                    simplified = true
                    break
                }
            }
        }
        var signedArea = 0f
        remaining.indices.forEach { index ->
            val current = points[remaining[index]]
            val next = points[remaining[(index + 1) % remaining.size]]
            signedArea += current.x * next.y - next.x * current.y
        }
        val orientation = if (signedArea >= 0) 1f else -1f
        val result = mutableListOf<Triple<Int, Int, Int>>()
        var attempts = 0
        while (remaining.size > 3 && attempts < remaining.size * remaining.size) {
            var clipped = false
            for (position in remaining.indices) {
                val a = remaining[(position - 1 + remaining.size) % remaining.size]
                val b = remaining[position]
                val c = remaining[(position + 1) % remaining.size]
                if (cross(points[a], points[b], points[c]) * orientation <= 1e-7f) continue
                if (remaining.any { index ->
                        index != a && index != b && index != c &&
                            inTriangle(points[index], points[a], points[b], points[c])
                    }
                ) continue
                result.add(Triple(a, b, c))
                remaining.removeAt(position)
                clipped = true
                break
            }
            if (!clipped) break
            attempts++
        }
        if (remaining.size == 3) result.add(Triple(remaining[0], remaining[1], remaining[2]))
        return result
    }

    private fun cross(a: Point2, b: Point2, c: Point2): Float =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun inTriangle(point: Point2, a: Point2, b: Point2, c: Point2): Boolean {
        val first = cross(a, b, point)
        val second = cross(b, c, point)
        val third = cross(c, a, point)
        return (first >= 0 && second >= 0 && third >= 0) ||
            (first <= 0 && second <= 0 && third <= 0)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().also { result ->
            GLES20.glAttachShader(result, vertexShader)
            GLES20.glAttachShader(result, fragmentShader)
            GLES20.glLinkProgram(result)
            val status = IntArray(1)
            GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) {
                "Could not link terrain renderer: ${GLES20.glGetProgramInfoLog(result)}"
            }
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
        }
    }

    private fun compileShader(type: Int, source: String): Int =
        GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) {
                "Could not compile terrain shader: ${GLES20.glGetShaderInfoLog(shader)}"
            }
        }

    private data class Point2(val x: Float, val y: Float)

    private data class Vertex(
        val x: Float,
        val y: Float,
        val z: Float,
        val u: Float,
        val v: Float
    )

    private class FloatList {
        private val values = ArrayList<Float>()
        val count get() = values.size / 8
        fun add(vertex: Vertex, normal: FloatArray) {
            values.add(vertex.x)
            values.add(vertex.y)
            values.add(vertex.z)
            values.add(normal[0])
            values.add(normal[1])
            values.add(normal[2])
            values.add(vertex.u)
            values.add(vertex.v)
        }
        fun toBuffer(): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                values.forEach(::put)
                position(0)
            }
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aUv;
            uniform mat4 uMvp;
            uniform vec3 uLight;
            varying vec2 vUv;
            varying float vLight;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vUv = aUv;
                vLight = max(dot(normalize(aNormal), normalize(uLight)), 0.0);
            }
        """
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform int uUseTexture;
            uniform vec4 uSolidColor;
            varying vec2 vUv;
            varying float vLight;
            void main() {
                vec4 base = uUseTexture == 1 ? texture2D(uTexture, vUv) : uSolidColor;
                if (uUseTexture == 1 && base.a < 0.08) discard;
                float shade = 0.52 + 0.48 * vLight;
                gl_FragColor = vec4(base.rgb * shade, base.a);
            }
        """
    }
}
