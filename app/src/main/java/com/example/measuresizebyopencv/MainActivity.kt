package com.example.measuresizebyopencv

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var seekbarThresholdMin: SeekBar
    private lateinit var switchFilter: SwitchCompat
    private lateinit var radioGroup: RadioGroup
    private lateinit var seekBarIteration: SeekBar

    private var imageBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        seekbarThresholdMin = findViewById(R.id.seekbarThresholdMin)
        switchFilter = findViewById(R.id.switchFilter)
        radioGroup = findViewById(R.id.radioGroup)
        seekBarIteration = findViewById(R.id.seekBarIteration)

        // Initialize OpenCV
        OpenCVLoader.initDebug()

        // on below line we are initializing our variables.
        seekBarIteration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateImage()
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
                updateImage()
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

    private fun updateImage() {
        // Calculate low threshold based on SeekBar progress
        val minThreshold = mapSeekBarProgressToValue(seekbarThresholdMin.progress, 0.0, 30.0)
        // radioGroup.checkedRadioButtonId -- we are getting radio button from our group.
        val radioButton = findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
        processImage(minThreshold, radioButton.text as String)
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
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private val imagePickerResult =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)

                updateImage()
            }
        }

    private fun distSquare(aPoint: Point, bPoint: Point): Double{
        return (aPoint.x - bPoint.x).pow(2) +
                (aPoint.y - bPoint.y).pow(2)
    }

    private fun isSharp(query_A: Point, B: Point, C: Point): Boolean{
        // cosA= [b²＋c²－a²]/ (2bc)
        val square_b = distSquare(query_A, C)
        val square_c = distSquare(query_A, B)
        val square_a = distSquare(B, C)
        val cosA = (square_b + square_c - square_a)/(2 * sqrt(square_b) * sqrt(square_c))
        // cos80 = 0.174
        println("square_b: $square_b, square_c: $square_c, square_a: $square_a, cosA: ${cosA}")
        return cosA > 0.174
    }
    // Process contour:
    // 1. Detect the convex points with bounding box.
    // 2. If it is sharp angle, remove the convex.
    private fun processContour(contour : MatOfPoint, origMat: Mat, toDrawPoly: Boolean, toDrawResult: Boolean): MatOfPoint  {
        val curveList = ArrayList<MatOfPoint>()
        curveList.add(contour)

        if (toDrawPoly) drawContours(origMat, curveList, 0, Scalar(255.0, 255.0, .0), 11)

        // Find the binding box Rect of approx Poly DP
        val boundRect: Rect = boundingRect(contour)
        rectangle(origMat, boundRect.tl(), boundRect.br(), Scalar(255.0, 0.0, 255.0), 5, 8, 0);
        println("Bounding Box tl: [${boundRect.tl().x} ${boundRect.tl().y}]   br: [${boundRect.br().x} ${boundRect.br().y}]")

        // Judge the bounding points
        val pointList = contour.toList()
        var newPointList: MutableList<Point> = mutableListOf()
        var updated = false
        for ( (index, point) in pointList.withIndex()) {
            println("$index: [${point.x} ${point.y}]")
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

                // 如果形成<30°锐角则是毛刺，去掉。
                if ( isSharp(point, prePoint, nextPoint) ) {
                    println("Remove this point.")
                    updated = true
                }
                else {
                    println("Keep this point.")
                    newPointList.add(point)
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
/*            for ((index, point) in contour.toList().withIndex()) {
                println("$index: [${point.x} ${point.y}]")
                var textPoint = Point(point.x + 50, point.y + 20)
                putText(
                     origMat,
                    index.toString(),
                    textPoint,
                    FONT_HERSHEY_PLAIN,
                    10.0,
                    Scalar(255.0, 255.0, 255.0),
                    11
                )
            }*/
        }
        return contour
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
    private fun processImage(
        minThreshold: Double = 30.0,
        filterOption: String = "None"
    ) {
        // Create a Mat object to hold the image data
        val imageMat = Mat()
        var tmpMat = Mat()
        val origMat = Mat() // The original picture to be drawn onto.
        // Convert the input imageBitmap to a Mat object (OpenCV format)
        Utils.bitmapToMat(imageBitmap, imageMat)
        Utils.bitmapToMat(imageBitmap, origMat)

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
            return
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
        val epsilon = 0.01 * arcLength(contour2f, true)
        val approxContour2f = MatOfPoint2f()
        approxPolyDP(contour2f, approxContour2f, epsilon, true)
        approxContour2f.convertTo(contour, CvType.CV_32S)

        // Process the contour to remove the sharp part and get the main body
        val iteration = seekBarIteration.progress + 1
        repeat( iteration ) {
            Utils.bitmapToMat(imageBitmap, origMat)
            contour = processContour(contour, origMat, toDrawPoly, toDrawResult)
        }
        showImage(origMat)

    }
}