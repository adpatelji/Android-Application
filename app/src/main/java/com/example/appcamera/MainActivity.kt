package com.example.appcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private var c:Int = 1

    lateinit var labeler: ImageLabeler


    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up the listener for take photo button
        camera_capture_button.setOnClickListener {
//            takePhoto()
            c = 1
            value = HUMANSCORE
            humanCurrentScore = -1
            computerCurrentScore = -1
            endGame = false
            human_score_value.text = "0"
            computer_score_value.text = "0"
        }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

        val localModel = LocalModel.Builder()
                .setAssetFilePath("model.tflite")
                // or .setAbsoluteFilePath(absolute file path to model file)
                // or .setUri(URI to model file)
                .build()

        val customImageLabelerOptions = CustomImageLabelerOptions.Builder(localModel)
                .setConfidenceThreshold(0.5f)
                .setMaxResultCount(5)
                .build()
        labeler = ImageLabeling.getClient(customImageLabelerOptions)

        comImage.setImageURI(Uri.fromFile(File("drawable-v24/myimage.jpeg")))


    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            })
    }
    var value = HUMANSCORE
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer(labeler) { luma: String ->
                        if (c % 150 == 0) {
                            CoroutineScope(Dispatchers.Main).launch {
                                logicForGame(luma.toInt())
                                viewFinder.visibility = View.GONE
                                replacedImage.visibility = View.VISIBLE
                                replacedImage.setImageResource(R.drawable.response_message)

                                if (endGame) {
                                    findViewById<TextView>(WHOBATS).text =
                                        if (computerCurrentScore > humanCurrentScore) "Computer Wins!!"
                                        else if (computerCurrentScore < humanCurrentScore) "HUMAN Wins"
                                        else "Tied"
                                    val intent = Intent(
                                        this@MainActivity
                                        ,Results::class.java
                                    )
                                    intent.putExtra("humanScore", humanCurrentScore)
                                    intent.putExtra("computerScore", computerCurrentScore)

                                    startActivity(intent)
                                }
                            }
                            c = 1
                        } else if (c > 100) {
                            CoroutineScope(Dispatchers.Main).launch {
                                findViewById<TextView>(WHOBATS).text =
                                    if (value == HUMANSCORE) "HUMAN BATTING 1st inning"
                                    else "COMPUTER BATTING ${humanCurrentScore - computerCurrentScore} needs to win"
                                viewFinder.visibility = View.VISIBLE
                                replacedImage.visibility = View.GONE
                                findViewById<ImageView>(R.id.comImage).setImageResource(R.drawable.computer_prep)
                            }
                            c++;
                        } else {
                            CoroutineScope(Dispatchers.Main).launch {
                                findViewById<TextView>(WHOBATS).text =
                                    if (value == HUMANSCORE) "HUMAN BATTING 1st inning"
                                    else "COMPUTER BATTING ${humanCurrentScore - computerCurrentScore} needs to win"
                            }
                            c++;
                        }
                    })
                }


            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    fun logicForGame(humanMove: Int){
        val computerMove:Int =  Random.nextInt(1, 7)
        changeImage(computerMove)
        if( computerMove == humanMove ){
            if( value == COMPUTERSCORE ){
//                endGame make result
                endGame = true
            }
            else{
                findViewById<TextView>(WHOBATS).text = "HUMAN OUT!!!"
                value = COMPUTERSCORE

            }
        }
        else {
            val currentScore =
                if (value == HUMANSCORE) humanMove
                else computerMove

            val preScore = findViewById<TextView>(value).text.toString().toInt()
            findViewById<TextView>(value).text = (currentScore + preScore).toString();

            if (value == HUMANSCORE)
                humanCurrentScore = currentScore + preScore
            else computerCurrentScore =  currentScore + preScore

        }
        if( computerCurrentScore!=-1 && computerCurrentScore > humanCurrentScore ){
            endGame = true
        }


    //        Log.d(TAG, "Average luminosity: $luma")
    }
    fun changeImage(sc:Int){
        when( sc ){
            1 -> findViewById<ImageView>(R.id.comImage).setImageResource(R.drawable.f1)
            2 -> findViewById<ImageView>(R.id.comImage).setImageResource(R.drawable.f2)
            3 -> findViewById<ImageView>(R.id.comImage).setImageResource(R.drawable.f3)
            4 -> findViewById<ImageView>(R.id.comImage).setImageResource(R.drawable.f4)
            5 -> findViewById<ImageView>(R.id.comImage).setImageResource(R.drawable.f5)
            6 -> findViewById<ImageView>(R.id.comImage).setImageResource(R.drawable.f6)
            else -> findViewById<ImageView>(R.id.comImage).setImageResource(R.drawable.computer_prep)

        }
    }
    private class LuminosityAnalyzer(
        private val labeler: ImageLabeler,
        private val listener: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val img = imageProxy.image;
            val image = InputImage.fromMediaImage(img, imageProxy.imageInfo.rotationDegrees)
            var str:String  = function(image)
            val buffer = imageProxy.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(str)

            imageProxy.close()
        }
        var str:Int? = null
        private fun function(image: InputImage):String {
            labeler.process(image)
                    .addOnSuccessListener { labels ->
                        println(labels.size)
                        for (label in labels) {
                            val text = label.text
                            val confidence = label.confidence
                            val index = label.index
                            str  = label.index
                            break

//                            println("message $str")
//                            Log.i(TAG,"text: "+ text.toString() +"confidene "+ confidence.toString()+" index " + index.toString())

                        }

                    }
                    .addOnFailureListener { e ->
//                        Toast.makeText(this,e.message.toString(),Toast.LENGTH_LONG).show()
                    }
            if(str == null)return "0";
            println("message $str")
            return str.toString()

        }

    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        const val HUMANSCORE =  R.id.human_score_value
        const val COMPUTERSCORE = R.id.computer_score_value
        const val WHOBATS = R.id.who_playing

        var humanCurrentScore = -1
        var computerCurrentScore = -1
        var endGame = false;

    }
}