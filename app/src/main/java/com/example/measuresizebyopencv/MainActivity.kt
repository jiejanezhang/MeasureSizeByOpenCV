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
        val minThreshold = mapSeekBarProgressToValue(seekbarThresholdMin.progress, 0.0, 100.0)
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
        filterOption: String = "NONE"
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

        // Call approxPolyDP() to approximate a contour shape and draw it
        val contour = contours[maxIdx]
        val contour2f = MatOfPoint2f()
        contour.convertTo(contour2f, CvType.CV_32FC2)
        val approxContour2f = MatOfPoint2f()
        val epsilon = 0.007 * arcLength(contour2f, true)
        approxPolyDP(contour2f, approxContour2f, epsilon, true)
        val curve = MatOfPoint()
        approxContour2f.convertTo(curve, CvType.CV_32S)
        val curveList = ArrayList<MatOfPoint>()
        curveList.add(curve)

        when (filterOption) {
            "Contour Only" -> {
                drawContours(origMat, contours, maxIdx, Scalar(255.0, 0.0, 0.0), 15)
            }
            "Approx Poly Only" -> {
                drawContours(origMat, curveList, 0, Scalar(0.0, 255.0, 255.0), 15)

            }
            "Both" -> {
                drawContours(origMat, contours, maxIdx, Scalar(255.0, 0.0, 0.0), 15)
                drawContours(origMat, curveList, 0, Scalar(0.0, 255.0, 255.0), 15)
            }
        }

        showImage(origMat)

    }
}