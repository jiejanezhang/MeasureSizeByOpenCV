package com.example.measuresizebyopencv

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*

const val MESSAGE_READ: Int = 0
const val MESSAGE_WRITE: Int = 1
const val MESSAGE_TOAST: Int = 2


class MainmenuActivity : AppCompatActivity() {
/*    private lateinit var checkBox1: CheckBox
    private lateinit var checkBox2: CheckBox
    private lateinit var checkBox3: CheckBox*/
    private lateinit var btnAdd: Button
    private lateinit var btnSave: Button
    private var checkBoxlist = Array(Companion.CHECKBOX_MAX, { it })
    companion object {
        private const val REQUEST_ENABLE_BT = 1
        const val NEW_CONTAINER = 2
        private const val CHECKBOX_MAX = 5
    }
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mainmenu)
        btnAdd = findViewById(R.id.btnAdd)
        btnSave = findViewById(R.id.btnSave)
        btnAdd.isEnabled = false
        btnSave.isEnabled = false
        creatCheckBox()

    }

    private fun creatCheckBox(){

        // References the root LinearLayout from
        // the activity_main layout file
        val layout = findViewById<LinearLayout>(R.id.root_layout)
        var chkId = 1001
        for ( i in 0..Companion.CHECKBOX_MAX-1) {
            // Create a new Checkbox at run-time
            val geekBox = CheckBox(this)

            // Define the layout properties and text for our check box
            geekBox.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            geekBox.text = ""

            // Set-up a listener when
            // the check box is toggled.
            geekBox.setOnCheckedChangeListener { _, isChecked ->
                btnSave.isEnabled = true }
            geekBox.isVisible = false
            geekBox.id = chkId++
            // Add our created check box to the root
            // layout for it to be displayed
            layout.addView(geekBox, 3+ i)
            checkBoxlist[i] = geekBox.id
        }
    }

    private fun writeCheckBoxes(op: String, info: String) {
        when (op){
            "write"-> {

                val containers = info.split("\n")
                for (idx in 0 .. CHECKBOX_MAX - 1 ){
                    val checkBox :CheckBox = findViewById(checkBoxlist[idx])
                    if (idx <= containers.size -1  && containers[idx].length > 0){
                        checkBox.text = containers[idx]
                        checkBox.visibility = VISIBLE
                        checkBox.isChecked = true
                    }
                    else {
                        checkBox.visibility = INVISIBLE
                        checkBox.isChecked = false
                    }
                }
            }
            "append" -> {
                    for (idx in 0..checkBoxlist.size - 1) {
                        val checkBox :CheckBox = findViewById(checkBoxlist[idx])
                        if (checkBox.visibility == INVISIBLE) {
                            checkBox.visibility = VISIBLE
                            checkBox.text = info
                            checkBox.isChecked = true
                            break
                        }
                    }
            }

        }
    }

    private fun readCheckBoxes(): String{
        var info = ""
        for (idx in 0..checkBoxlist.size - 1) {
            val checkBox :CheckBox = findViewById(checkBoxlist[idx])
            if (checkBox.visibility == VISIBLE && checkBox.isChecked) {
                info += checkBox.text as String + "\n"
            }
        }
        if (info != "") info = info.substring(0, info.length - 1) // remove the last \n in the end.
        return info
    }
    @RequiresApi(Build.VERSION_CODES.P)
    fun onAddNewClick(view: View) {
        val intent =  Intent(this, AddNewActivity::class.java)
        startActivityForResult(intent, NEW_CONTAINER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // If you have multiple activities returning results then you should include unique request codes for each
        if (requestCode == NEW_CONTAINER) {
            val infor = data?.getStringExtra("infor").toString()
            if (infor != "" && infor != "null")
            {
                writeCheckBoxes("append", infor)
                btnSave.isEnabled = true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("MissingPermission")
    fun onSaveClick(view: View) {

        var bluetoothAdapter = getSystemService(BluetoothManager::class.java).adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this@MainmenuActivity, "没有蓝牙功能。", Toast.LENGTH_SHORT).show()
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            Toast.makeText(this@MainmenuActivity, "蓝牙功能未打开。", Toast.LENGTH_SHORT).show()
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, MainmenuActivity.REQUEST_ENABLE_BT)
            return
        }

        // Search the Paied device list for "Faucet_Controller".
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        var BTdevice : BluetoothDevice? = null
        val DEVNAME = "Faucet_Controller"

        pairedDevices?.forEach { device ->
            if (device.name == DEVNAME) {
                BTdevice = device
            }
        }
        if (BTdevice == null ) {
            Toast.makeText(this@MainmenuActivity, DEVNAME+ "没有配对。", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this@MainmenuActivity, "正在连接蓝牙设备，请稍后...", Toast.LENGTH_SHORT).show()

        Thread {
            var BTSocket = BTdevice!!.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))

            try {
                BTSocket.connect()
                // The connection attempt succeeded. Perform work associated with
                // the connection.
                Log.v("BT", "BT connected.")
                var outputStream = BTSocket.outputStream
                val contents = readCheckBoxes()
                var data = "#W" + contents + "&"
                val byteArray = data.toByteArray()
                outputStream.write(byteArray)
                Log.v("BT", "Data is sent." + data)

                Thread.sleep(100)
                BTSocket.close()
                runOnUiThread{
                    writeCheckBoxes("write", contents)
                    Toast.makeText(this@MainmenuActivity, "数据已发送。", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Log.e("BT", "Connect or send data error", e)
                runOnUiThread{
                    Toast.makeText(this@MainmenuActivity, "蓝牙连接、收发数据出错。请稍后再试。", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()


    }
    // Button click handler to connect to device
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.P)
    fun onConnectClick(view: View) {
        var bluetoothAdapter = getSystemService(BluetoothManager::class.java).adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this@MainmenuActivity, "没有蓝牙功能。", Toast.LENGTH_SHORT).show()
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            Toast.makeText(this@MainmenuActivity, "蓝牙功能未打开。", Toast.LENGTH_SHORT).show()
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, MainmenuActivity.REQUEST_ENABLE_BT)
            return
        }

        // Search the Paied device list for "Faucet_Controller".
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        var BTdevice: BluetoothDevice? = null
        val DEVNAME = "Faucet_Controller"

        pairedDevices?.forEach { device ->
            if (device.name == DEVNAME) {
                BTdevice = device
            }
        }
        if (BTdevice == null) {
            Toast.makeText(this@MainmenuActivity, DEVNAME + "没有配对。", Toast.LENGTH_SHORT)
                .show()
            return
        }


        Toast.makeText(
            this@MainmenuActivity,
            "正在连接蓝牙设备，请稍后...",
            Toast.LENGTH_SHORT
        ).show()
        Thread {
            //Thread.sleep(0)
            var BTSocket =
                BTdevice!!.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
            try {
                BTSocket.connect()
                // The connection attempt succeeded. Perform work associated with
                // the connection.
                Log.v("BT", "BT connected.")
                var outputStream = BTSocket.outputStream
                var inputStream = BTSocket.inputStream
                var data = "#G"
                val byteArray = data.toByteArray()
                outputStream.write(byteArray)
                Log.v("BT", "Data is sent.")
                Thread.sleep(150)

                var readBuffer = ByteArray(255) // store for the stream
                inputStream.read(readBuffer)
                Thread.sleep(150)
                var byteBuffer: ByteBuffer = ByteBuffer.wrap(readBuffer)
                var contents = StandardCharsets.UTF_8.decode(byteBuffer).toString()
                println("Contents:" + contents)
                if (contents.indexOf( '&' ) < 0) contents = ""
                contents = contents.substringBefore('&')
                println("Clean up contents:" + contents)

                Log.v("BT", "Data is recieved.")
                BTSocket.close()

                runOnUiThread{
                    writeCheckBoxes("write", contents)
                    btnAdd.isEnabled = true
                }
                // Read from the InputStream
            } catch (e: IOException) {
                Log.e("BT", "Connect or send data error", e)
                BTSocket.close()
                runOnUiThread{
                    Toast.makeText(this@MainmenuActivity, "蓝牙连接、收发数据出错。请稍后再试。", Toast.LENGTH_SHORT).show()
                }
            }

        }.start()

        /*
            try {
                BTSocket.connect()
                // The connection attempt succeeded. Perform work associated with
                // the connection.
                Log.v("BT", "BT connected.")
                var outputStream = BTSocket.outputStream
                var inputStream = BTSocket.inputStream
                var data = "#G"
                val byteArray = data.toByteArray()
                outputStream.write(byteArray)
                Log.v("BT", "Data is sent.")
                Thread.sleep(100)

                var readBuffer = ByteArray(255) // store for the stream
                inputStream.read(readBuffer)
                Thread.sleep(100)
                var byteBuffer: ByteBuffer = ByteBuffer.wrap(readBuffer)
                var contents = StandardCharsets.UTF_8.decode(byteBuffer).toString()
                println("Contents:" + contents)
                contents = contents.substringBefore('&')
                println("Clean up contents:" + contents)
                writeCheckBoxes("write", contents)
                btnAdd.isEnabled = true
                //Toast.makeText(this@MainmenuActivity, "Connecting and getting data. Please wait...", Toast.LENGTH_LONG).show()
                Log.v("BT", "Data is recieved.")
                BTSocket.close()

                // Read from the InputStream
            } catch (e: IOException) {
                Log.e("BT", "Connect or send data error", e)
                BTSocket.close()
                Toast.makeText(this@MainmenuActivity, "Connect or send/recieve data error. Please try again.", Toast.LENGTH_SHORT).show()
            }

        */
    }

}