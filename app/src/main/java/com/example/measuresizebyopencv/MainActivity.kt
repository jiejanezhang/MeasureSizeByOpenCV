package com.example.measuresizebyopencv

import android.R.attr.src
import android.graphics.Bitmap
import android.graphics.ImageDecoder
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
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc.*


class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var seekbarThresholdMin: SeekBar
    private lateinit var switchFilter: SwitchCompat
    private lateinit var radioGroup: RadioGroup

    private var imageBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        seekbarThresholdMin = findViewById(R.id.seekbarThresholdMin)
        switchFilter = findViewById(R.id.switchFilter)
        radioGroup = findViewById(R.id.radioGroup)
        // Initialize OpenCV
        OpenCVLoader.initDebug()

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


        // on below line we are initializing our variables.

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

                processImage()
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
        filterOption: String = "Original Picture"
    ) {
        // Create a Mat object to hold the image data
        val imageMat = Mat()
        var tmpMat = Mat()
        // Convert the input imageBitmap to a Mat object (OpenCV format)
        Utils.bitmapToMat(imageBitmap, imageMat)

        // Convert BGRA to BGR.
        cvtColor(imageMat, imageMat, COLOR_BGRA2BGR);

        // GaussianBlur to filter the noise
        when (filterOption) {
            "Original Picture" -> {
                showImage(imageMat)
                tmpMat =  imageMat.clone()
            }
            "Averaging" -> {
                blur(imageMat, tmpMat, Size(5.0, 5.0))
            }
            "Gaussian Blurring" -> {
                GaussianBlur(imageMat, tmpMat, Size(5.0, 5.0), 0.0, 0.0)
            }
            "Median Blurring" -> {
                medianBlur(imageMat, tmpMat, 5)
            }
            "Bilateral Filtering" -> {
                bilateralFilter(imageMat, tmpMat, 9, 75.0, 75.0)
            }
        }

        // Call Canny for contour detection
        Canny(tmpMat, imageMat, minThreshold, minThreshold * 3.0, 3, true)

        showImage(imageMat)
    }
}