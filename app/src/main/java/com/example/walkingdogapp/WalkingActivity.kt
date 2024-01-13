package com.example.walkingdogapp

import android.app.ActivityManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.example.walkingdogapp.databinding.ActivityWalkingBinding
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale


class WalkingActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityWalkingBinding
    private lateinit var mynavermap: NaverMap
    private lateinit var walkViewModel: LocateInfoViewModel

    private var coordList = mutableListOf<LatLng>()
    private var isTracking = false
    private var trackingMarker = Marker()
    private var trackingPath = PathOverlay()
    private lateinit var trackingCamera : CameraUpdate

    private val cameraPermission = arrayOf(android.Manifest.permission.CAMERA)
    private val storegePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    private val cameraCode = 98
    private val storageCode = 99

    private lateinit var uri : Uri
    private lateinit var currentPhotoPath: String

    // 뒤로 가기
    private val BackPressCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            stopWalkingService()
            goHome()
        }
    }

    private val getResultCamera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            galleryAddPic()
        }
    }

    fun RandomFileName(): String {
        return SimpleDateFormat(
            "yyyyMMddHHmmss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWalkingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 보류
        walkViewModel = ViewModelProvider(this).get(LocateInfoViewModel::class.java)
        walkViewModel.getLastLocation()

        // 백그라운드 위치 서비스 시작
        startWalkingService()

        val mapFragment: MapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as MapFragment
        mapFragment.getMapAsync(this)

        walkViewModel.currentCoord.observe(this) {
            walkViewModel.getCurrentAddress { locateName ->
                binding.textLocation.text = locateName
            }
        }

        this.onBackPressedDispatcher.addCallback(this, BackPressCallback)

        // 트래킹 모드 여부 확인 및 여부에 따른 버튼과 텍스트 변경
        WalkingService.isTracking.observe(this) {
            this.isTracking = it
            binding.apply {
                if (isTracking) {
                    textIsTracking.text = "산책 중지하기"
                    btnStart.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    textIsTracking.text = "산책 시작하기"
                    btnStart.setImageResource(android.R.drawable.ic_media_play)
                }
            }
            Log.d("current coord", this.isTracking.toString())
        }

        // 버튼 이벤트 설정
        binding.apply {
            btnBack.setOnClickListener {
                stopWalkingService()
                goHome()
            }

            btnStart.setOnClickListener {
                if (isWalkingServiceRunning()) {
                    if(isTracking) {
                        stopTracking()
                    }
                    else {
                        startTracking()
                    }
                }
            }

            btnCamera.setOnClickListener {
                startCamera()
            }
        }
    }

    override fun onMapReady(map: NaverMap) {
        this.mynavermap = map
        mynavermap.uiSettings.isRotateGesturesEnabled = false
        mynavermap.uiSettings.isCompassEnabled = false

        trackingMarker.icon = OverlayImage.fromResource(R.mipmap.ic_launcher_round)
        trackingMarker.width = 100
        trackingMarker.height = 100
        trackingMarker.anchor = PointF(0.5f, 0.5f)

        trackingPath.width = 15
        trackingPath.color = Color.YELLOW

        walkViewModel.currentCoord.observe(this) {
            val firstCamera = CameraUpdate.scrollAndZoomTo(LatLng(it.latitude, it.longitude),
                17.0)
            mynavermap.moveCamera(firstCamera)
            trackingMarker.position = LatLng(it.latitude, it.longitude)
            trackingMarker.map = mynavermap
        }

        // 위치 업데이트 이벤트
        WalkingService.coordList.observe(this) {
            coordList = it
            if (coordList.isNotEmpty()) {
                trackingMarker.map = null
                trackingMarker.position = coordList.last()  // 현재 위치에 아이콘 설정
                trackingMarker.map = mynavermap
                trackingCamera =
                    CameraUpdate.scrollTo(coordList.last()).animate(CameraAnimation.Easing)  // 현재 위치로 카메라 이동
                mynavermap.moveCamera(trackingCamera)
                if (coordList.size > 1) {
                    trackingPath.map = null
                    trackingPath.coords = coordList // 이동 경로 그림
                    trackingPath.map = mynavermap
                    binding.InfoDistance.text = getString(R.string.totaldistance,
                        WalkingService.totalDistance / 1000.0)
                }
            }
        }

        WalkingService.walkTime.observe(this) {
            val minutes = it / 60
            val seconds = it % 60
            binding.InfoTime.text = getString(R.string.totaltime, minutes, seconds)
        }
    }

    private fun isWalkingServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (myService in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (WalkingService::class.java.name == myService.service.className) {
                if (myService.foreground) {
                    return true
                }
            }
            return false
        }
        return false
    }

    private fun startWalkingService() {
        if (!isWalkingServiceRunning()) {
            Log.d("WalkingService", "start")
            val serviceIntent = Intent(this, WalkingService::class.java)
            serviceIntent.action = Constant.ACTION_START_Walking_SERVICE
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun stopWalkingService() {
        if (isWalkingServiceRunning()) {
            Log.d("WalkingService", "stop")
            val serviceIntent = Intent(this, WalkingService::class.java)
            serviceIntent.action = Constant.ACTION_STOP_Walking_SERVICE
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun startTracking() {
        if (isWalkingServiceRunning()) {
            val serviceIntent = Intent(this, WalkingService::class.java)
            serviceIntent.action = Constant.ACTION_START_Walking_Tracking
            startService(serviceIntent)
        }
    }

    private fun stopTracking() {
        if (isWalkingServiceRunning()) {
            val serviceIntent = Intent(this, WalkingService::class.java)
            serviceIntent.action = Constant.ACTION_STOP_Walking_Tracking
            startService(serviceIntent)
        }
    }

    private fun goHome() {
        val backIntent = Intent(this@WalkingActivity, MainActivity::class.java)
        startActivity(backIntent)
        finish()
    }

    private fun startCamera() {
        if(checkPermission(cameraPermission, cameraCode) && checkPermission(storegePermission, storageCode)) {
            takePhoto(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        }
    }

    private fun takePhoto(intent: Intent) {
        val parentDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absoluteFile
        val subDir = File(parentDir, "털뭉치")
        if (!subDir.exists()) {
            subDir.mkdir()
        }
        val photoFile: File? = try {
            createImageFile(subDir)
        } catch (ex: IOException) {
            // Error occurred while creating the File
            ex.printStackTrace()
            null
        }
        photoFile?.also {
            uri = FileProvider.getUriForFile(
                this,
                "walkingdogapp.provider",
                it
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            getResultCamera.launch(intent)
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(storageDir: File?): File {
        // Create an image file name
        val timeStamp: String = RandomFileName()
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpeg", /* suffix */
            storageDir /* directory */
        ).apply {
            Log.i("syTest", "Created File AbsolutePath : $absolutePath")
            currentPhotoPath = absolutePath
            this.deleteOnExit()
        }
    }

    private fun galleryAddPic() {
        MediaScannerConnection.scanFile(
            this,
            arrayOf(currentPhotoPath),
            null
        ) { path, uri ->
            Log.d("testsave", "Scanned file path: $path")
            Log.d("testsave", "Scanned file URI: $uri")
        }
    }

    private fun checkPermission(permissions : Array<out String>, code: Int) : Boolean{
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, permissions, code)
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            storageCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle("사진 저장을 위해 권한을 \n허용으로 해주세요!")
                    val listener = DialogInterface.OnClickListener { _, ans ->
                        when (ans) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.flags = FLAG_ACTIVITY_NEW_TASK
                                intent.data = Uri.fromParts("package", packageName, null)
                                startActivity(intent)
                            }
                        }
                    }
                    builder.setPositiveButton("네", listener)
                    builder.setNegativeButton("아니오", null)
                    builder.show()
                } else {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        startCamera()
                    }
                }
            }
            cameraCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle("촬영을 위해 권한을 \n허용으로 해주세요!")
                    val listener = DialogInterface.OnClickListener { _, ans ->
                        when (ans) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.flags = FLAG_ACTIVITY_NEW_TASK
                                intent.data = Uri.fromParts("package", packageName, null)
                                startActivity(intent)
                            }
                        }
                    }
                    builder.setPositiveButton("네", listener)
                    builder.setNegativeButton("아니오", null)
                    builder.show()
                } else {
                    startCamera()
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}