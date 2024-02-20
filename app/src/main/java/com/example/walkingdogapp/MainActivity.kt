package com.example.walkingdogapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.example.walkingdogapp.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis
import kotlin.time.measureTimedValue

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mainviewmodel: userInfoViewModel
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000
    private val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    private var backPressedTime : Long = 0

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.database
    private val storage = FirebaseStorage.getInstance()

    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (System.currentTimeMillis() - backPressedTime < 2500) {
                moveTaskToBack(true)
                finishAndRemoveTask()
                exitProcess(0)
            }
            Toast.makeText(this@MainActivity, "한번 더 클릭 시 종료 됩니다.", Toast.LENGTH_SHORT).show()
            backPressedTime = System.currentTimeMillis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 위치 권한
        ActivityCompat.requestPermissions(this, PERMISSIONS, LOCATION_PERMISSION_REQUEST_CODE)

        this.onBackPressedDispatcher.addCallback(this, callback)

        if (isWalkingServiceRunning()) {
            val walkingIntent = Intent(this, WalkingActivity::class.java)
            startActivity(walkingIntent)
        }
        // 보류
        mainviewmodel = ViewModelProvider(this).get(userInfoViewModel::class.java)

        // 화면 전환
        binding.menuBn.run {
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_home -> {
                        changeFragment(HomeFragment())
                        true
                    }

                    R.id.navigation_book -> {
                        changeFragment(BookFragment())
                        true
                    }

                    R.id.navigation_albummap -> {
                        changeFragment(AlbumMapFragment())
                        true
                    }

                    else -> {
                        changeFragment(MyPageFragment())
                        true
                    }
                }
            }
        }
    }

    private fun changeFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(binding.screenFl.id, fragment)
            .commit()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.d("onRequest", "onRequestPermissionsResult")
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("권한 승인", "권한 승인됨")
                    mainviewmodel.getLastLocation()
                    getUserInfo()
                } else {
                    Log.d("권한 거부", "권한 거부됨")
                    getUserInfo()
                }
                return
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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

    private fun getUserInfo() {
        val uid = auth.currentUser?.uid
        val userRef = db.getReference("Users").child("$uid")
        val storgeRef = storage.getReference("$uid")
        lifecycleScope.launch {
            val dogDefferd = async(Dispatchers.IO) {
                try {
                    userRef.child("dog").get().await().getValue(DogInfo::class.java) ?: DogInfo()
                } catch (e: Exception) {
                    DogInfo()
                }
            }

            val profileUriDeferred = async(Dispatchers.IO) {
                try {
                    storgeRef.child("images").child("profileimg").downloadUrl.await() ?: Uri.EMPTY
                } catch (e: Exception) {
                    Uri.EMPTY
                }
            }

            val profileUri = profileUriDeferred.await()

            val profileDrawable = suspendCoroutine { continuation ->
                Glide.with(applicationContext).asDrawable().load(profileUri).into(object: CustomTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        continuation.resume(resource)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        continuation.resume(ContextCompat.getDrawable(this@MainActivity, R.drawable.waitimage)!!)
                    }
                })}

            val dog = dogDefferd.await()
            mainviewmodel.saveDogName(dog.name)
            mainviewmodel.saveImgDrawble(profileDrawable)

            changeFragment(HomeFragment())
        }
    }
}
