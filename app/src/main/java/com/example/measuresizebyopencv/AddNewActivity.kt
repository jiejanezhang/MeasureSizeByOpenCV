package com.example.measuresizebyopencv

//import com.google.common.primitives.Bytes
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.graphics.Bitmap
import android.icu.text.DecimalFormat
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc.*
import java.io.IOException
import java.math.RoundingMode
import java.util.*
import kotlin.math.*

// Thanks to https://github.com/wh173d3v11/OpenCVSamples. It is really helpful as a good start!

enum class ANGLE {
    ANGLE_VERY_SHARP,
    ANGLE_BARELY_SHARP, // 65°~90°
    ANGLE_OTHER
}

class AddNewActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var seekbarThresholdMin: SeekBar
    private lateinit var seekbarThresholdMin2: SeekBar
    private lateinit var seekbarThresholdMin3: SeekBar
    private lateinit var switchFilter: SwitchCompat
    private lateinit var radioGroup: RadioGroup
    private lateinit var seekBarIteration: SeekBar

    private var imageBitmap: Bitmap? = null
    private var contourContainer: MatOfPoint? = null
    private var topCoinSize: Double = 0.0
    private var topCoinCenter: Point? = null
    private var backCoinSize: Double = 0.0
    private var backCoinCenter: Point? = null
    private var volume: Double = 2.0
    private var realCoinSize = 30.0

    private lateinit var bluetoothAdapter: BluetoothAdapter

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val BLUETOOTH_PERMISSION_CODE = 100
        private const val TAG = "MY_APP_DEBUG_TAG"
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        seekbarThresholdMin = findViewById(R.id.seekbarThresholdMin)
        seekbarThresholdMin2 = findViewById(R.id.seekbarThresholdMin2)
        seekbarThresholdMin3 = findViewById(R.id.seekBarThresholdMin3)
        switchFilter = findViewById(R.id.switchFilter)
        radioGroup = findViewById(R.id.radioGroup)
        seekBarIteration = findViewById(R.id.seekBarIteration)

        // Initialize OpenCV
        OpenCVLoader.initDebug()


        // on below line we are initializing our variables.
        seekBarIteration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateImage(updateTopCoin = false, updateBackCoin = false)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // empty
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // empty
            }
        })

        switchFilter.setOnCheckedChangeListener { compoundButton, b ->
            updateImage()
        }

        seekbarThresholdMin.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateImage(updateTopCoin = false, updateBackCoin = false)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // empty
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // empty
            }

        })

        seekbarThresholdMin2.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateImage(updateContainer = false, updateBackCoin = false)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // empty
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // empty
            }
        })

        seekbarThresholdMin3.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateImage(updateContainer = false, updateTopCoin = false)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // empty
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // empty
            }
        })
        // Add listener for our radio group.
        radioGroup.setOnCheckedChangeListener(object: RadioGroup.OnCheckedChangeListener{

            override fun onCheckedChanged(group: RadioGroup?, checkedId: Int) {
                updateImage()
            }
        }
        )

    }

    private fun mapSeekBarProgressToValue(
        progress: Int,
        minValue: Double,
        maxValue: Double
    ): Double {
        val range = maxValue - minValue
        return minValue + (progress / 100.0) * range
    }

    // Button click handler to capture or select an image
    @RequiresApi(Build.VERSION_CODES.P)
    fun onCaptureOrSelectImageClick(view: View) {
        imagePickerResult.launch("image/*")
        val textViewVolume = findViewById<TextView>(R.id.textViewVolume)
        textViewVolume.text = "容量:未知"
        volume = 0.0
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private val imagePickerResult =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)

                updateImage()
            }
        }

    // Button click handler to capture or select an image
    @RequiresApi(Build.VERSION_CODES.P)
    fun onStartToMeasureSizeClick(view: View) {
        if (contourContainer == null) {
            return
        }
        volume = calculateVolume()/1000000
        val decimalFormat = DecimalFormat("#.##")
        decimalFormat.roundingMode = RoundingMode.CEILING.ordinal
        val textViewVolume = findViewById<TextView>(R.id.textViewVolume)
        textViewVolume.text = decimalFormat.format(volume).toString() + "L"
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.P)
    fun onOKToAddClick(view: View) {
        if (volume == 0.0){
            Toast.makeText(this@AddNewActivity, "Volume is zero", Toast.LENGTH_SHORT).show()
            return
        }

        // To popup dialog for container name
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        builder.setTitle("输入容器名称:")
        val dialogLayout = inflater.inflate(R.layout.alert_dialog_with_edittext, null)
        val editText  = dialogLayout.findViewById<EditText>(R.id.editText)
        builder.setView(dialogLayout)

        var data = ""
        val decimalFormat = DecimalFormat("#.##")
        decimalFormat.roundingMode = RoundingMode.CEILING.ordinal
        val volume_data = decimalFormat.format(volume).toString()
        builder.setPositiveButton("OK") { _, _ ->
            val intent = Intent()
            data = editText.text.toString() + " " + volume_data + "L"
            intent.putExtra("infor", data)
            setResult(MainmenuActivity.NEW_CONTAINER, intent)
            finish()
        }
        builder.setNegativeButton("Cancel"){ _, _ ->
            val intent = Intent()
            intent.putExtra("infor", data)
            setResult(MainmenuActivity.NEW_CONTAINER, intent)
            finish()
        }
        builder.show()

    }

    private fun updateImage(updateContainer: Boolean = true, updateTopCoin: Boolean = true, updateBackCoin: Boolean = true) {
        // Calculate low threshold based on SeekBar progress
        // radioGroup.checkedRadioButtonId -- we are getting radio button from our group.
        val radioButton = findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
        var origMat = Mat() // The original picture to be drawn onto.
        // Convert the input imageBitmap to a Mat object (OpenCV format)
        Utils.bitmapToMat(imageBitmap, origMat)
        if (updateContainer) {
            val minThreshold = mapSeekBarProgressToValue(seekbarThresholdMin.progress, 0.0, 10.0)
            Log.v("Threshlod", "Container Threshlod" + minThreshold.toString())
            contourContainer = processContainer(origMat, minThreshold, radioButton.text as String)
        }
        if (updateTopCoin) {
            val minThreshold = mapSeekBarProgressToValue(seekbarThresholdMin2.progress, 0.0, 50.0)
            processCoin(origMat, minThreshold, topCoin = true)
            Log.v("Threshlod", "TopCoin Threshlod" + minThreshold.toString())
        }
        if (updateBackCoin) {
            val minThreshold = mapSeekBarProgressToValue(seekbarThresholdMin3.progress, 2.0, 6.0)
            processCoin(origMat, minThreshold, topCoin = false)
            Log.v("Threshlod","BackCoin Threshlod" + minThreshold.toString())
        }

        if ( contourContainer != null) {
            val curveList = ArrayList<MatOfPoint>()
            curveList.add(contourContainer!!)
            drawContours(origMat, curveList, 0, Scalar(255.0, 0.0, 0.0), 11)
        }

        if (topCoinSize > 0.0) {
            circle(origMat, topCoinCenter, (topCoinSize/2).toInt(), Scalar(255.0, 0.0, 0.0), 11)
        }

        if (backCoinSize > 0.0) {
            circle(origMat, backCoinCenter, (backCoinSize/2).toInt(), Scalar(0.0, 255.0, 255.0), 11)
        }

        showImage(origMat)
    }



    private fun calculateVolume(): Double{
        val origMat = Mat()
        Utils.bitmapToMat(imageBitmap, origMat)

        val comparator: Comparator<MutableList<Point>?> = object : Comparator<MutableList<Point>?> {
            override fun compare(vector1: MutableList<Point>?, vector2: MutableList<Point>?): Int {
                return if (vector1!![0].y > vector2!![0].y)
                    1
                else
                    -1
            }
        }

        // Sort the pointers in order of Y
        var pointList: MutableList<Point> = contourContainer!!.toList()
        var vectorList: MutableList<MutableList<Point>> = mutableListOf()
        println("find the vectors:")
        for ( (index, point) in pointList.withIndex()){
            val vector : MutableList<Point> = mutableListOf()
            val nextIndex = if (index == pointList.size -1 )  0 else index + 1
            if (pointList[index].y < pointList[nextIndex].y){
                vector.add(pointList[index])
                vector.add(pointList[nextIndex])
                println("Vector($index): $index -> $nextIndex")
            }
            else{
                vector.add(pointList[nextIndex])
                vector.add(pointList[index])
                println("Vector($index): $nextIndex -> $index")
            }
            vectorList.add(vector)
        }

        println("Order the vectors:")
        Collections.sort(vectorList, comparator)
        for ( (index, vector) in vectorList.withIndex()) {
            line(origMat, vector[0], vector[1],  Scalar(0.0, 0.0, 255.0), 9)
            println("Vector($index): [${vector[0].x}, ${vector[0].y}] -> [${vector[1].x}, ${vector[1].y}]")
        }


        val maxY = (vectorList[vectorList.size-1][1].y).toInt()
        val minY = (vectorList[0][0].y).toInt()
        var volume = 0.0
        val sliceHeight = 50
        for (y in minY..maxY step sliceHeight)
        {
            println("Scan Y: $y")
            val jointPoints : MutableList<Point> = mutableListOf()
            for ( vector in vectorList){
                if ( y >= vector[0].y && y <= vector[1].y ) {
                    // x = x1 + (y-y1)(x1-x2)/(y1-y2)
                    val x = vector[0].x + (y - vector[0].y)*(vector[0].x - vector[1].x)/(vector[0].y - vector[1].y)
                    jointPoints.add(Point(x, y.toDouble()))
                }
            }
            if ( jointPoints.size >= 2) {
                line(origMat, jointPoints[0], jointPoints[1], Scalar(0.0, 0.0, 255.0), 9)
                println("Joint Points:${jointPoints[0]}, ${jointPoints[1]}")
                // Volume = pi*r²*h
                volume += sliceHeight * PI * abs(jointPoints[0].x - jointPoints[1].x).pow(2) / 4
            }
        }
        showImage(origMat)

        var baseCoinSize : Double
        if (topCoinSize >0 && backCoinSize >0 ) {
            baseCoinSize = 2 * topCoinSize * backCoinSize/(topCoinSize + backCoinSize)
        }
        else{
            if (topCoinSize > 0) {
                baseCoinSize = 2 * topCoinSize * 216/(topCoinSize + 216) // Based on the data from several photoes
            }else if (backCoinSize > 0) {
                baseCoinSize = 2 * 388 * backCoinSize/(388 + backCoinSize) // Based on the data from several photoes
            }else{
                baseCoinSize = 301.0 // Based on the data from several photoes
            }
        }


        val heightRatio = baseCoinSize / topCoinSize
        volume *= heightRatio

        val imageMeter = baseCoinSize / realCoinSize // 像素长度/mm
        volume /= imageMeter.pow(3)

        println("Volume: $volume")
        return volume
    }

    private fun distSquare(aPoint: Point, bPoint: Point): Double{
        return (aPoint.x - bPoint.x).pow(2) +
                (aPoint.y - bPoint.y).pow(2)
    }

    private fun isSharp(query_A: Point, B: Point, C: Point): ANGLE{
        // cosA= [b²＋c²－a²]/ (2bc)
        val square_b = distSquare(query_A, C)
        val square_c = distSquare(query_A, B)
        val square_a = distSquare(B, C)
        val cosA = (square_b + square_c - square_a)/(2 * sqrt(square_b) * sqrt(square_c))
        println("square_b: $square_b, square_c: $square_c, square_a: $square_a, cosA: ${cosA}")
        // cos65 = 0.423
        if (cosA > 0.423) return ANGLE.ANGLE_VERY_SHARP
        if (cosA < 0.423 && cosA > 0) return ANGLE.ANGLE_BARELY_SHARP
        return ANGLE.ANGLE_OTHER
    }
    // Process contour:
    // 1. Detect the convex points with bounding box.
    // 2. If it is sharp angle, remove the convex.
    private fun processContour(contour : MatOfPoint, perimeter: Double, origMat: Mat, toDrawPoly: Boolean, toDrawResult: Boolean): Pair<MatOfPoint, Boolean>  {
        val curveList = ArrayList<MatOfPoint>()
        curveList.add(contour)

        if (toDrawPoly) drawContours(origMat, curveList, 0, Scalar(255.0, 255.0, .0), 11)

        // Find the binding box Rect of approx Poly DP
        val boundRect: Rect = boundingRect(contour)
        println("Bounding Box tl: [${boundRect.tl().x} ${boundRect.tl().y}]   br: [${boundRect.br().x} ${boundRect.br().y}]")

        // Judge the bounding points
        val pointList = contour.toList()
        var newPointList: MutableList<Point> = mutableListOf()
        var updated = false
        for ( (index, point) in pointList.withIndex()) {
            println("$index: [${point.x}0.2 ${point.y}]")
            var textPoint = Point(point.x-10, point.y+20)
            putText(origMat,index.toString(), textPoint, FONT_HERSHEY_PLAIN, 10.0, Scalar(255.0, 255.0, 0.0), 11)

            // Check if the point is in bounding box
            if (point.x in boundRect.tl().x - 2 .. boundRect.tl().x + 2 ||
                point.x in boundRect.br().x - 2 .. boundRect.br().x + 2 ||
                point.y in boundRect.tl().y - 2 .. boundRect.tl().y + 2 ||
                point.y in boundRect.br().y - 2 .. boundRect.br().y + 2 )
            {
                println("Point is in bounding box")
                val preIndex = if (index == 0)  pointList.size - 1 else index - 1
                val nextIndex = if (index == pointList.size -1 )  0 else index + 1
                val prePoint = pointList[preIndex]
                val nextPoint = pointList[nextIndex]
                println("Previous Point is $preIndex. Next Point is $nextIndex")

                when (isSharp(point, prePoint, nextPoint )) {
                    // <65°
                    ANGLE.ANGLE_VERY_SHARP -> {
                        println("ANGLE_VERY_SHARP")
                        println("Remove this point.")
                        updated = true
                    }
                    // 65°<90°
                    ANGLE.ANGLE_BARELY_SHARP -> {
                        println("ANGLE_BARELY_SHARP")
                        val b = sqrt(distSquare(point, prePoint))
                        val c = sqrt(distSquare(point, nextPoint))
                        if (b > 0.07 * perimeter ||
                            c > 0.07 * perimeter )
                        {
                            println("Corner point. Keep this point.")
                            newPointList.add(point)
                        }
                        else{
                            println("Remove this point.")
                            println("b: $b  c: $c  perimeter: $perimeter ")
                            updated = true
                        }
                    }
                    ANGLE.ANGLE_OTHER -> {
                        println("ANGLE_OTHER.")
                        println("Keep this point.")
                        newPointList.add(point)
                    }
                }
            }
            else {
                println("Keep this point.")
                newPointList.add(point)
            }
        }

        // Show the new contour with the updated pointList
        if (updated) {
            contour.fromArray(*newPointList.toTypedArray())
            curveList.removeAt(0)
            curveList.add(contour)
        }
        if (toDrawResult) {
            drawContours(origMat, curveList, 0, Scalar(255.0, 0.0, 0.0), 11)
        }
        val result :Pair<MatOfPoint, Boolean> = contour to updated
        return result
    }

    private fun showImage(imgMat: Mat)
    {
        // Create a new Bitmap named bwBitmap to hold the filtered image
        val bwBitmap = Bitmap.createBitmap(imgMat.cols(), imgMat.rows(), Bitmap.Config.RGB_565)
        // Convert the filtered Mat back to a Bitmap format
        Utils.matToBitmap(imgMat, bwBitmap)
        imageView.setImageBitmap(bwBitmap)
    }

    // Process the captured or selected image with OpenCV and display the result
    private fun processContainer(
        origMat: Mat,
        minThreshold: Double = 30.0,
        filterOption: String = "None"
    ) : MatOfPoint? {
        // Create a Mat object to hold the image data
        val imageMat = Mat()
        var tmpMat = Mat()
        //val origMat = Mat() // The original picture to be drawn onto.
        // Convert the input imageBitmap to a Mat object (OpenCV format)
        Utils.bitmapToMat(imageBitmap, imageMat)
        //Utils.bitmapToMat(imageBitmap, origMat)

        // Convert BGRA to BGR.
        cvtColor(imageMat, imageMat, COLOR_BGRA2BGR);

        // Blur to filter the noise
        blur(imageMat, tmpMat, Size(5.0, 5.0))

        // Call Canny for contour detection
        Canny(tmpMat, imageMat, minThreshold, minThreshold * 3.0, 3, true)

        // Morphological Transformations
        val kernelMat = getStructuringElement(MORPH_RECT, Size(5.0, 5.0))
        dilate(imageMat, imageMat, kernelMat, Point(-1.0, -1.0), 2)

        // find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        findContours(imageMat, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_TC89_KCOS )

        // find the largest contours and draw it
        var maxArea = -1.0
        var maxIdx = 0
        for ((index, contour) in contours.withIndex()) {
            val contourArea = contourArea(contour)
            if (contourArea < maxArea) {
                continue
            }
            maxArea = contourArea
            maxIdx = index

        }
        if (maxArea < 0) {
            return null
        }

        var toDrawResult = false
        var toDrawPoly = false
        when (filterOption)
        {
            "Processed Curve Only" -> toDrawResult = true
            "Approx Poly Only" -> toDrawPoly = true
            "Both" -> {
                toDrawResult = true
                toDrawPoly = true
            }
        }
        drawContours(origMat, contours, maxIdx, Scalar(255.0, 0.0, 0.0), 15)

        // approximate a contour shape
        var contour = contours[maxIdx]
        val contour2f = MatOfPoint2f()
        contour.convertTo(contour2f, CvType.CV_32FC2)
        val perimeter = arcLength(contour2f, true)
        val epsilon = 0.01 * perimeter
        val approxContour2f = MatOfPoint2f()
        approxPolyDP(contour2f, approxContour2f, epsilon, true)
        approxContour2f.convertTo(contour, CvType.CV_32S)

        // Process the contour to remove the sharp part and get the main body
        var iteration = 5
        while (iteration-- > 0){
            Utils.bitmapToMat(imageBitmap, origMat)
            val result = processContour(contour, perimeter, origMat, toDrawPoly, toDrawResult)
            if (result.second)
            {
                contour = result.first
            }
            else break
        }
        println("Iteration: $iteration")
        return contour

    }


    // Process the coin
    private fun processCoin(
        origMat: Mat,
        minThreshold: Double = 30.0,
        topCoin: Boolean = true
    ) {
        // Initialize the coin data.
        if (topCoin) {
            topCoinSize = 0.0
            topCoinCenter = null
        }else{
            backCoinSize = 0.0
            backCoinCenter = null
        }

        // Create a Mat object to hold the image data
        val imageMat = Mat()
        var tmpMat = Mat()

        // Convert the input imageBitmap to a Mat object (OpenCV format)
        Utils.bitmapToMat(imageBitmap, imageMat)

        // Convert BGRA to BGR.
        cvtColor(imageMat, imageMat, COLOR_BGR2GRAY);

        // Blur to filter the noise
        blur(imageMat, tmpMat, Size(5.0, 5.0))

        // Call Canny for contour detection
        Canny(tmpMat, imageMat, minThreshold, minThreshold * 3.0, 3, true)

        // Morphological Transformations
        val kernelMat = getStructuringElement(MORPH_RECT, Size(5.0, 5.0))
        dilate(imageMat, imageMat, kernelMat, Point(-1.0, -1.0), 2)

        showImage(imageMat)
        // find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        findContours(imageMat, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_TC89_KCOS )



        var containerBound: Rect? = null
        if (contourContainer != null) {
            containerBound = boundingRect(contourContainer)
        }
        // find the contours and draw it
        var maxArea = -1.0
        var maxIdx = -1
        var distance = if (containerBound == null) 1000.0 else containerBound?.width?.times(0.2)  // The back icon shall be close to the container.
        var topIdx = -1
        for ((index, contour) in contours.withIndex()) {
            val contourArea = contourArea(contour)

            // Skip the small contour
            if (contourArea < 10000.0 ) {
                continue
            }
            // There should be at least 5 points to fit the ellipse
            if (contour.toList().size < 5) {
                println("Skip the <5 points.")
                continue
            }


            // Skip the very narrow area or too long area
            val boundRect: Rect = boundingRect(contour)
            var longEdge = boundRect.height.toFloat()
            var shortEdge = boundRect.width.toFloat()
            if (boundRect.width > boundRect.height) {
                longEdge = boundRect.width.toFloat()
                shortEdge = boundRect.height.toFloat()
            }
            if (((longEdge/shortEdge) > 6.0) || longEdge < 100 || longEdge > 800){
                println("Skip the very narrow area or too long area. LongEdge: $longEdge   shortEdge: $shortEdge")
                continue
            }
            //drawContours(origMat, contours, index, Scalar(255.0, 255.0, 0.0), 5)


            if (topCoin)
            {
                // Top coin is on the container. Skip if out of container.
                if (containerBound != null) {
                    if (boundRect.x < containerBound.x || boundRect.x > containerBound.x + containerBound.width
                        || boundRect.y < containerBound.y || boundRect.y > containerBound.y + containerBound.height)
                        continue
                }
                if (contourArea > maxArea) {
                    maxArea = contourArea
                    maxIdx = index
                }
            }
            else{
                if (containerBound != null) {
                    // Back coin is out of the container. Skip if it is in the container.
                    if (boundRect.x > containerBound.x && boundRect.x < containerBound.x + containerBound.width
                        && boundRect.y > containerBound.y && boundRect.y < containerBound.y + containerBound.height)
                       continue

                    // Coin is above the Container.
                    // If bottom of boundRect is below containerBound.y, skip it.
                    val bottom = boundRect.y.toDouble() + boundRect.height
                    if ( bottom > containerBound.y){
                        continue
                    }

                    // Find the closest one.
                    if (containerBound.y - bottom < distance!!) {
                        distance = containerBound.y - bottom
                        topIdx = index
                    }
                }
            }

        }
        if (maxIdx < 0 && topCoin) {
            return
        }
        if (topIdx < 0 && !topCoin) {
            return
        }
        // Find the fit Ellipse
        val contour :MatOfPoint = if (topCoin) contours[maxIdx]  else contours[topIdx]
        //drawContours(origMat, contours, if (topCoin) maxIdx  else topIdx, Scalar(255.0, 255.0, 0.0), 5)
        val contour2f = MatOfPoint2f()
        contour.convertTo(contour2f, CvType.CV_32FC2)
        var elli = fitEllipse(contour2f)
        var center: Point = elli.center
        var radius: Double = if (elli.size.width < elli.size.height) elli.size.width/2 else elli.size.height/2


        if (topCoin) {
            topCoinSize = radius * 2
            topCoinCenter = center
        } else {
            backCoinSize = radius * 2
            backCoinCenter = center
        }
        return
    }

}