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

    init {
        Log.d("testing!!!!!", "new PenStroke created")
    }

    fun add(x: Float, y: Float, pressure: Float) {
        points.add(PenPoint(x, y, pressure))
        pathIsValid = false
    }

    private fun pressureToRadius(pressure: Float): Float {
        return pressure * 50F
    }

    fun getPoints() = points

    fun renderPath(): Path {
        if (!pathIsValid) {
            if (points.size == 0) {
                path = Path()
                pathIsValid = true
                return path//@Runnable
            }

            if (points.size == 1) {
                // this can just be a circle
                val newpath = Path()
                newpath.addCircle(points[0].x, points[0].y, pressureToRadius(points[0].pressure), Path.Direction.CW)
                path = newpath
                pathIsValid = true
                return path//@Runnable
            }

            // starting cap (round)
            // start with a square for now
            val first_to_second = normalize(Float2(points[1].x - points[0].x, points[1].y - points[0].y))
            val point0 = points[0].asFloat2()
            val radius0 = pressureToRadius(points[0].pressure)
            val cw_vec = Float2(first_to_second.y, -first_to_second.x) * radius0 + point0
            val ccw_vec = Float2(-first_to_second.y, first_to_second.x) * radius0 + point0

            val newpath = Path()
            newpath.moveTo(cw_vec.x, cw_vec.y)
            newpath.lineTo(ccw_vec.x, ccw_vec.y)

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
                //val angle2 = -(2*FPI - (-(2*FPI-angle_to_prev) - (2*FPI-angle_to_next)) / 2)
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
            newpath.lineTo(cw_vec_last.x, cw_vec_last.y)
            newpath.lineTo(ccw_vec_last.x, ccw_vec_last.y)

            // other side of the path
            for (point in return_points.reversed()) {
                newpath.lineTo(point.x, point.y)
            }

            newpath.close()
            path = newpath
            pathIsValid = true
        }



//        for (pair in points) {
//            if (path == null) {
//                path = Path()
//                path!!.moveTo(pair.x, pair.y)
//            }
//            else {
//                path!!.lineTo(pair.x, pair.y)
//            }
//        }

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
        strokeWidth = 1F
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var strokes: ArrayList<PenStroke> = ArrayList()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.apply {
            for (stroke in strokes) {
                drawPath(stroke.renderPath(), inkPaint)

                for (point in stroke.getPoints()) {
                    drawPoint(point.x, point.y, inkPaint)
                }
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
            Log.d("### testing ###", "Button state: $buttonState")

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
                    Log.d("#######################", "The action is ${action}")
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
                Log.d("############", "The tool type is ${toolType}")
                return false
            }
        }

        invalidate()
        return true
    }
}