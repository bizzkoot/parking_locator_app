package com.bizzkoot.parkinglocator

import com.bizzkoot.parkinglocator.R
import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt
import android.graphics.Color
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar


class MainActivity : AppCompatActivity() {
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var captureButton: Button
    private lateinit var viewLocationButton: Button
    private lateinit var floorLevelInput: EditText
    private var imageCapture: ImageCapture? = null
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views first
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        viewFinder = findViewById(R.id.viewFinder)
        captureButton = findViewById(R.id.captureButton)
        viewLocationButton = findViewById(R.id.viewLocationButton)
        floorLevelInput = findViewById(R.id.floorLevelInput)

        // Initialize toolbar and drawer toggle
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Initialize other components
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        setupButtonListeners()
        setupNavigationDrawer()
    }

    private fun setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_about -> {
                    showAboutDialog()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_how_to -> {
                    showHowToUseDialog()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About Parking Locator")
            .setMessage("Version ${BuildConfig.VERSION_NAME}\n\nA smart application to help you remember where you parked your car.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showHowToUseDialog() {
        AlertDialog.Builder(this)
            .setTitle("How to Use")
            .setMessage("""
                1. Enter your parking floor level
                2. Tap 'Save Parking Location' to capture photo
                3. View saved location anytime
                4. Use navigation to find your car
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupButtonListeners() {
        captureButton.setOnClickListener {
            captureLocation()
        }
        
        viewLocationButton.setOnClickListener {
            showSavedLocation()
        }
    }

    private fun captureLocation() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            getOutputDirectory(),
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    saveLocationWithPhoto(photoFile.absolutePath)
                }

                override fun onError(exc: ImageCaptureException) {
                    // Handle error
                }
            }
        )
    }

    private fun saveLocationWithPhoto(photoPath: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val parkingLocation = ParkingLocation(
                        it.latitude,
                        it.longitude,
                        floorLevelInput.text.toString(),
                        photoPath,
                        System.currentTimeMillis()
                    )
                    saveParkingLocation(parkingLocation)
                    showLocationSavedDialog()
                }
            }
        }
    }

    private fun saveParkingLocation(location: ParkingLocation) {
        val sharedPref = getSharedPreferences("ParkingLocation", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("latitude", location.latitude.toString())
            putString("longitude", location.longitude.toString())
            putString("floorLevel", location.floorLevel)
            putString("photoPath", location.photoPath)
            putLong("timestamp", location.timestamp)
            apply()
        }
    }

    private fun getSavedParkingLocation(): ParkingLocation? {
        val sharedPref = getSharedPreferences("ParkingLocation", Context.MODE_PRIVATE)
        val latitude = sharedPref.getString("latitude", null)
        val longitude = sharedPref.getString("longitude", null)
        val floorLevel = sharedPref.getString("floorLevel", null)
        val photoPath = sharedPref.getString("photoPath", null)
        val timestamp = sharedPref.getLong("timestamp", System.currentTimeMillis())

        return if (latitude != null && longitude != null && photoPath != null) {
            ParkingLocation(
                latitude.toDouble(),
                longitude.toDouble(),
                floorLevel ?: "",
                photoPath,
                timestamp
            )
        } else null
    }

    private fun showSavedLocation() {
        val location = getSavedParkingLocation()
        if (location != null) {
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.dialog_parking_details)
            
            val imageView = dialog.findViewById<ImageView>(R.id.parkingImage)
            val locationText = dialog.findViewById<TextView>(R.id.locationText)
            val navigateButton = dialog.findViewById<Button>(R.id.navigateButton)
            
            val bitmap = BitmapFactory.decodeFile(location.photoPath)
            val rotatedBitmap = rotateBitmapIfRequired(bitmap, location.photoPath)
            
            val initialMatrix = Matrix()
            imageView.post {
                val scale = Math.min(
                    imageView.width.toFloat() / rotatedBitmap.width,
                    imageView.height.toFloat() / rotatedBitmap.height
                )
                initialMatrix.setScale(scale, scale)
                
                // Center the image
                val dx = (imageView.width - rotatedBitmap.width * scale) / 2
                val dy = (imageView.height - rotatedBitmap.height * scale) / 2
                initialMatrix.postTranslate(dx, dy)
                
                imageView.scaleType = ImageView.ScaleType.MATRIX
                imageView.imageMatrix = initialMatrix
            }
            
            imageView.setImageBitmap(rotatedBitmap)
            imageView.setOnTouchListener(ImageTouchListener())
            
            navigateButton.setOnClickListener {
                openGoogleMapsNavigation(location.latitude, location.longitude)
            }
            
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val dateTime = sdf.format(location.timestamp)
            
            locationText.text = """
                Latitude: ${location.latitude}
                Longitude: ${location.longitude}
                Floor: ${location.floorLevel}
                Time: $dateTime
            """.trimIndent()

            locationText.setTextColor(Color.WHITE)
            
            dialog.show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("No Location Found")
                .setMessage("No parking location has been saved yet.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(viewFinder.surfaceProvider)
            
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                // Handle any errors
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun showLocationSavedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Success")
            .setMessage("Parking location saved successfully!")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app requires camera and location permissions to function.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun openGoogleMapsNavigation(latitude: Double, longitude: Double) {
        val gmmIntentUri = Uri.parse("google.navigation:q=$latitude,$longitude&mode=w")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        startActivity(mapIntent)
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, photoPath: String): Bitmap {
        val ei = ExifInterface(photoPath)
        val orientation = ei.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
            else -> bitmap
        }
    }

    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private inner class ImageTouchListener : View.OnTouchListener {
        private var mode = 0
        private val matrix = Matrix()
        private val savedMatrix = Matrix()
        private val start = PointF()
        private val mid = PointF()
        private var oldDist = 1f
        
        private val NONE = 0
        private val DRAG = 1
        private val ZOOM = 2
        
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val imageView = v as ImageView
            val scale: Float
            
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    matrix.set(imageView.imageMatrix)
                    savedMatrix.set(matrix)
                    start.set(event.x, event.y)
                    mode = DRAG
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    oldDist = spacing(event)
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix)
                        midPoint(mid, event)
                        mode = ZOOM
                    }
                }
                MotionEvent.ACTION_MOVE -> if (mode == DRAG) {
                    matrix.set(savedMatrix)
                    matrix.postTranslate(event.x - start.x, event.y - start.y)
                } else if (mode == ZOOM) {
                    val newDist = spacing(event)
                    if (newDist > 10f) {
                        matrix.set(savedMatrix)
                        scale = newDist / oldDist
                        matrix.postScale(scale, scale, mid.x, mid.y)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> mode = NONE
            }
            
            imageView.imageMatrix = matrix
            return true
        }
        
        private fun spacing(event: MotionEvent): Float {
            val x = event.getX(0) - event.getX(1)
            val y = event.getY(0) - event.getY(1)
            return sqrt(x * x + y * y)
        }
        
        private fun midPoint(point: PointF, event: MotionEvent) {
            val x = event.getX(0) + event.getX(1)
            val y = event.getY(0) + event.getY(1)
            point.set(x / 2, y / 2)
        }
    }
}

data class ParkingLocation(
    val latitude: Double,
    val longitude: Double,
    val floorLevel: String,
    val photoPath: String,
    val timestamp: Long
)
