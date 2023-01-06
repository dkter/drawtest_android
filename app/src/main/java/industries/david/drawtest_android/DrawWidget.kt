package industries.david.drawtest_android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import dev.romainguy.kotlin.math.PI as FPI
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.normalize
import dev.romainguy.kotlin.math.sqr
import kotlin.math.*

const val S_PEN_BUTTON_DOWN = 211;
const val S_PEN_BUTTON_MOVE = 213;
const val S_PEN_BUTTON_UP = 212;

data class PenPoint(val x: Float, val y: Float, val pressure: Float) {
    fun asFloat2(): Float2 {
        return Float2(x, y)
    }
}

class PenStroke {
    private val points: ArrayList<PenPoint> = ArrayList()

    // used to cache path so it doesn't need to be re-rendered if there are no changes
    private var path: Path = Path()
    private var pathIsValid: Boolean = true

    fun add(x: Float, y: Float, pressure: Float) {
        pathIsValid = false
        val new_point = PenPoint(x, y, pressure)
        if (points.size > 0 && new_point.x == points.last().x && new_point.y == points.last().y) {
            return
        }
        points.add(new_point)
    }

    private fun pressureToRadius(pressure: Float): Float {
        return pressure * 4F
    }

    fun getPoints() = points

    fun trimPoints() {
        var startingIndex = 0
        points.forEachIndexed { index, point ->
            var works = true
            points.slice(0 until index).forEach { point2 ->
                val distance = sqrt(sqr(point.x - point2.x) + sqr(point.y - point2.y))
                if (distance + pressureToRadius(point2.pressure) > pressureToRadius(point.pressure)) {
                    // point2 can't be discarded
                    works = false
                }
            }
            if (works) {
                startingIndex = index
            }
        }
        for (i in 0 until startingIndex) {
            points.removeFirst()
        }

        var endingReverseIndex = 0
        points.reversed().forEachIndexed { index, point ->
            var works = true
            points.reversed().slice(0 until index).forEach { point2 ->
                val distance = sqrt(sqr(point.x - point2.x) + sqr(point.y - point2.y))
                if (distance + pressureToRadius(point2.pressure) > pressureToRadius(point.pressure)) {
                    // point2 can't be discarded
                    works = false
                }
            }
            if (works) {
                endingReverseIndex = index
            }
        }
        for (i in 0 until endingReverseIndex) {
            points.removeLast()
        }
    }

    fun renderPath(): Path {
        if (!pathIsValid) {
            trimPoints()

            if (points.size == 0) {
                path = Path()
                pathIsValid = true
                return path
            }

            if (points.size == 1) {
                // this can just be a circle
                val newpath = Path()
                newpath.addCircle(points[0].x, points[0].y, pressureToRadius(points[0].pressure), Path.Direction.CW)
                path = newpath
                pathIsValid = true
                return path
            }

            // starting cap (round)
            val first_to_second = normalize(Float2(points[1].x - points[0].x, points[1].y - points[0].y))
            val point0 = points[0].asFloat2()
            val radius0 = pressureToRadius(points[0].pressure)
            val cw_vec = Float2(first_to_second.y, -first_to_second.x) * radius0 + point0
            val ccw_vec = Float2(-first_to_second.y, first_to_second.x) * radius0 + point0
            val node1 = cw_vec - first_to_second * radius0 * 2F
            val node2 = ccw_vec - first_to_second * radius0 * 2F

            val newpath = Path()
            newpath.moveTo(cw_vec.x, cw_vec.y)
            newpath.cubicTo(
                node1.x, node1.y,
                node2.x, node2.y,
                ccw_vec.x, ccw_vec.y
            )

            // middle of path
            val return_points = ArrayList<Float2>()
            for (index in 1 until points.size - 1) {
                val radius = pressureToRadius(points[index].pressure)

                // divide angle by 2
                var angle_to_prev = atan2(
                    points[index - 1].y - points[index].y,
                    points[index - 1].x - points[index].x,
                )
                val angle_to_next = atan2(
                    points[index + 1].y - points[index].y,
                    points[index + 1].x - points[index].x,
                )
                if (angle_to_next > angle_to_prev) {
                    angle_to_prev += 2*FPI
                }
                val angle1 = (angle_to_prev + angle_to_next) / 2
                val angle2 = angle1 + FPI  // rotate by 180deg

                val vec1 = Float2(radius * cos(angle1), radius * sin(angle1))
                val vec2 = Float2(radius * cos(angle2), radius * sin(angle2))

                val point1 = points[index].asFloat2() + vec1
                newpath.lineTo(point1.x, point1.y)
                return_points.add(points[index].asFloat2() + vec2)
            }

            // end cap
            val last_to_2last = normalize(Float2(
                points[points.size - 2].x - points.last().x,
                points[points.size - 2].y - points.last().y))
            val point_last = points.last().asFloat2()
            val radius_last = pressureToRadius(points.last().pressure)
            val cw_vec_last = Float2(last_to_2last.y, -last_to_2last.x) * radius_last + point_last
            val ccw_vec_last = Float2(-last_to_2last.y, last_to_2last.x) * radius_last + point_last
            val node1_last = cw_vec_last - last_to_2last * radius_last * 2F
            val node2_last = ccw_vec_last - last_to_2last * radius_last * 2F
            newpath.lineTo(cw_vec_last.x, cw_vec_last.y)
            newpath.cubicTo(
                node1_last.x, node1_last.y,
                node2_last.x, node2_last.y,
                ccw_vec_last.x, ccw_vec_last.y
            )

            // other side of the path
            for (point in return_points.reversed()) {
                newpath.lineTo(point.x, point.y)
            }

            newpath.close()
            newpath.fillType = Path.FillType.WINDING
            path = newpath
            pathIsValid = true
        }

        return path
    }

    fun collidesWith(x: Float, y: Float): Boolean {
        val radius = 20F
        for (point in points) {
            if (abs(point.x - x) <= radius && abs(point.y - y) <= radius) {
                return true
            }
        }
        return false
    }
}

class DrawWidget(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }
    private var strokes: ArrayList<PenStroke> = ArrayList()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.apply {
            for (stroke in strokes) {
                drawPath(stroke.renderPath(), inkPaint)
            }
        }
    }

    private fun erase(x: Float, y: Float) {
        val indices = ArrayList<Int>()
        strokes.forEachIndexed { index, stroke ->
            if (stroke.collidesWith(x, y)) {
                indices.add(index)
            }
        }
        indices.forEachIndexed { strokes_removed, index ->
            strokes.removeAt(index - strokes_removed)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.apply {
            val toolType = event.getToolType(0)
            if (toolType == MotionEvent.TOOL_TYPE_STYLUS) {
                if (action == MotionEvent.ACTION_DOWN) {
                    strokes.add(PenStroke())
                    strokes.last().add(x, y, pressure)
                }
                else if (action == MotionEvent.ACTION_MOVE) {
                    strokes.last().add(x, y, pressure)
                }
                else if (action == S_PEN_BUTTON_DOWN || action == S_PEN_BUTTON_MOVE) {
                    erase(x, y)
                }
                else {
                    return false
                }
            }
            else if (toolType == MotionEvent.TOOL_TYPE_ERASER) {
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    erase(x, y)
                }
                else {
                    return false
                }
            }
            else {
                return false
            }
        }

        invalidate()
        return true
    }
}