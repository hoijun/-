package com.example.walkingdogapp.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.example.walkingdogapp.MainActivity
import com.example.walkingdogapp.datamodel.AlarmDao
import com.example.walkingdogapp.datamodel.AlarmDataModel
import com.example.walkingdogapp.datamodel.DogInfo
import com.example.walkingdogapp.datamodel.TotalWalkInfo
import com.example.walkingdogapp.datamodel.UserInfo
import com.example.walkingdogapp.datamodel.WalkDateInfo
import com.example.walkingdogapp.datamodel.WalkDateInfoInSave
import com.example.walkingdogapp.datamodel.WalkLatLng
import com.example.walkingdogapp.utils.utils.NetworkManager
import com.example.walkingdogapp.utils.utils.Utils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.getValue
import com.google.firebase.storage.FirebaseStorage
import com.naver.maps.geometry.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class UserInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val storage: FirebaseStorage,
    private val alarmDao: AlarmDao
) {
    private var uid = auth.currentUser?.uid
    private var userRef = database.getReference("Users").child("$uid")
    private var storageRef = storage.getReference("$uid").child("images")
    private lateinit var alarmList: List<AlarmDataModel>

    fun resetUser() {
        uid = auth.currentUser?.uid
        userRef = database.getReference("Users").child("$uid")
        storageRef = storage.getReference("$uid").child("images")
    }

    suspend fun signUp(email: String, successSignUp: MutableLiveData<Boolean>) {
        val userRef = database.getReference("Users")
        val userInfo = UserInfo(email = email)
        val isError = AtomicBoolean(false)

        CoroutineScope(Dispatchers.IO).launch {
            val userInfoJob = async(Dispatchers.IO) {
                try {
                    userRef.child("$uid").child("user")
                        .setValue(userInfo)
                        .await()
                } catch (e: Exception) {
                    Log.d("savepoint", e.message.toString())
                    isError.set(true)
                }
            }

            val totalTotalWalkInfoJob = async(Dispatchers.IO) {
                try {
                    userRef.child("$uid").child("totalWalk")
                        .setValue(TotalWalkInfo()).await()
                } catch (e: Exception) {
                    Log.d("savepoint", e.message.toString())
                    isError.set(true)
                }
            }

            val collectionInfoJob = async(Dispatchers.IO) {
                try {
                    userRef.child("$uid").child("collection")
                        .setValue(Utils.item_whether).await()
                } catch (e: Exception) {
                    Log.d("savepoint", e.message.toString())
                    isError.set(true)
                }
            }

            val termsOfServiceJob = async(Dispatchers.IO) {
                try {
                    userRef.child("$uid").child("termsOfService")
                        .setValue(true).await()
                } catch (e: Exception) {
                    Log.d("savepoint", e.message.toString())
                    isError.set(true)
                }
            }

            userInfoJob.await()
            totalTotalWalkInfoJob.await()
            collectionInfoJob.await()
            termsOfServiceJob.await()

            if (!isError.get()) {
                successSignUp.postValue(true)
            } else {
                successSignUp.postValue(false)
            }
        }
    }

    suspend fun observeUser(
        dogsInfo: MutableLiveData<List<DogInfo>>,
        userInfo: MutableLiveData<UserInfo>,
        totalWalkInfo: MutableLiveData<TotalWalkInfo>,
        walkDates: MutableLiveData<HashMap<String, MutableList<WalkDateInfo>>>,
        collectionInfo: MutableLiveData<HashMap<String, Boolean>>,
        dogsImg: MutableLiveData<HashMap<String, Uri>>,
        successGetData: MutableLiveData<Boolean>
    ) {
        if (!NetworkManager.checkNetworkState(context)) {
            successGetData.postValue(false)
            return
        }

        if (successGetData.value == true) {
            successGetData.postValue(true)
        }

        val isError = AtomicBoolean(false)
        val dogsInfoDeferred = CompletableDeferred<List<DogInfo>>()
        val userDeferred = CompletableDeferred<UserInfo>()
        val totalWalkDeferred = CompletableDeferred<TotalWalkInfo>()
        val walkDateDeferred = CompletableDeferred<HashMap<String, MutableList<WalkDateInfo>>>()
        val collectionDeferred = CompletableDeferred<HashMap<String, Boolean>>()
        val profileUriDeferred = CompletableDeferred<HashMap<String, Uri>>()

        try {
            userRef.child("dog").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val dogsList = mutableListOf<DogInfo>()
                    val dogNames = mutableListOf<String>()
                    if (snapshot.exists()) {
                        for (dogInfo in snapshot.children) {
                            dogsList.add(
                                DogInfo(
                                    dogInfo.child("name").getValue(String::class.java)!!,
                                    dogInfo.child("breed").getValue(String::class.java)!!,
                                    dogInfo.child("gender").getValue(String::class.java)!!,
                                    dogInfo.child("birth").getValue(String::class.java)!!,
                                    dogInfo.child("neutering")
                                        .getValue(String::class.java)!!,
                                    dogInfo.child("vaccination")
                                        .getValue(String::class.java)!!,
                                    dogInfo.child("weight").getValue(String::class.java)!!,
                                    dogInfo.child("feature").getValue(String::class.java)!!,
                                    dogInfo.child("creationTime").getValue(Long::class.java)!!,
                                    dogInfo.child("totalWalkInfo").getValue(TotalWalkInfo::class.java)!!
                                )
                            )
                            dogNames.add(dogInfo.child("name").getValue(String::class.java)!!)
                        }
                    }
                    MainActivity.dogNameList = dogNames
                    dogsInfoDeferred.complete(dogsList.sortedBy { it.creationTime })
                }

                override fun onCancelled(error: DatabaseError) {
                    isError.set(true)
                    dogsInfoDeferred.complete(listOf<DogInfo>())
                }
            })

            userRef.child("user").get().addOnSuccessListener {
                userDeferred.complete(it.getValue(UserInfo::class.java) ?: UserInfo())
            }.addOnFailureListener {
                isError.set(true)
                userDeferred.complete(UserInfo())
            }

            userRef.child("totalWalkInfo").get().addOnSuccessListener {
                totalWalkDeferred.complete(it.getValue(TotalWalkInfo::class.java) ?: TotalWalkInfo())
            }.addOnFailureListener {
                isError.set(true)
                totalWalkDeferred.complete(TotalWalkInfo())
            }

            dogsInfo.postValue(dogsInfoDeferred.await())

            val dogsWalkDateInfo = HashMap<String, MutableList<WalkDateInfo>>()

            if (MainActivity.dogNameList.isEmpty()) {
                walkDateDeferred.complete(dogsWalkDateInfo)
            } else {
                for (dog in MainActivity.dogNameList) {
                    userRef.child("dog").child(dog).child("walkdates")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val walkDateInfos = mutableListOf<WalkDateInfo>()
                                if (snapshot.exists()) {
                                    for (dateData in snapshot.children) {
                                        val walkDay = dateData.key.toString().split(" ")
                                        walkDateInfos.add(
                                            WalkDateInfo(
                                                walkDay[0], walkDay[1], walkDay[2],
                                                dateData.child("distance")
                                                    .getValue(Float::class.java)!!,
                                                dateData.child("time").getValue(Int::class.java)!!,
                                                dateData.child("coords")
                                                    .getValue<List<WalkLatLng>>()
                                                    ?: listOf(),
                                                dateData.child("collections")
                                                    .getValue<List<String>>() ?: listOf()
                                            )
                                        )
                                    }
                                }
                                dogsWalkDateInfo[dog] = walkDateInfos
                                if (dogsWalkDateInfo.size == MainActivity.dogNameList.size) {
                                    walkDateDeferred.complete(dogsWalkDateInfo)
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                isError.set(true)
                                dogsWalkDateInfo[dog] = mutableListOf()
                                if (dogsWalkDateInfo.size == MainActivity.dogNameList.size) {
                                    walkDateDeferred.complete(dogsWalkDateInfo)
                                }
                            }
                        })
                }
            }

            userRef.child("collection").get().addOnSuccessListener {
                collectionDeferred.complete(
                    it.getValue<HashMap<String, Boolean>>() ?: Utils.item_whether
                )
            }.addOnFailureListener {
                isError.set(true)
                collectionDeferred.complete(Utils.item_whether)
            }

            // 강아지 프로필 사진
            val dogImgs = HashMap<String, Uri>()
            var downloadCount = 0
            var imgCount: Int
            storageRef.listAll().addOnSuccessListener { listResult ->
                imgCount = listResult.items.size
                if (imgCount == 0) {
                    profileUriDeferred.complete(HashMap<String, Uri>())
                }
                listResult.items.forEach { item ->
                    item.downloadUrl.addOnSuccessListener { uri ->
                        downloadCount++
                        dogImgs[item.name] = uri
                        if (imgCount == downloadCount) {
                            profileUriDeferred.complete(dogImgs)
                        }
                    }.addOnFailureListener {
                        downloadCount++
                        dogImgs[item.name] = Uri.EMPTY
                        if (imgCount == downloadCount) {
                            profileUriDeferred.complete(dogImgs)
                        }
                    }
                }
            }.addOnFailureListener {
                isError.set(true)
                profileUriDeferred.complete(HashMap<String, Uri>())
            }

            userInfo.postValue(userDeferred.await())
            totalWalkInfo.postValue(totalWalkDeferred.await())
            walkDates.postValue(walkDateDeferred.await())
            collectionInfo.postValue(collectionDeferred.await())
            dogsImg.postValue(profileUriDeferred.await())

            if (!isError.get()) {
                successGetData.postValue(true)
            } else {
                successGetData.postValue(false)
            }


        } catch (e: Exception) {
            successGetData.postValue(false)
        }
    }

    fun add(alarm: AlarmDataModel) {
        alarmDao.addAlarm(alarm)
    }

    fun delete(alarm: AlarmDataModel) {
        alarmDao.deleteAlarm(alarm.alarm_code)
    }

    fun getAll(): List<AlarmDataModel> {
        alarmList = alarmDao.getAlarmsList()
        return alarmList
    }

    fun onOffAlarm(alarmCode: Int, alarmOn: Boolean) {
        alarmDao.updateAlarmStatus(alarmCode, alarmOn)
    }

    fun updateAlarmTime(alarmCode: Int, time: Long) {
        alarmDao.updateAlarmTime(alarmCode, time)
    }

    suspend fun updateUserInfo(userInfo: UserInfo) {
        val userInfoUpdateJob = CoroutineScope(Dispatchers.IO).launch {
            val nameDeferred = async(Dispatchers.IO) {
                try {
                    userRef.child("user").child("name").setValue(userInfo.name).await()
                } catch (e: Exception) {
                    return@async
                }
            }

            val genderDeferred = async(Dispatchers.IO) {
                try {
                    userRef.child("user").child("gender").setValue(userInfo.gender).await()
                } catch (e: Exception) {
                    return@async
                }
            }

            val birthDeferred = async(Dispatchers.IO) {
                try {
                    userRef.child("user").child("birth").setValue(userInfo.birth).await()
                } catch (e: Exception) {
                    return@async
                }
            }

            nameDeferred.await()
            genderDeferred.await()
            birthDeferred.await()
        }

        userInfoUpdateJob.join()
    }

    suspend fun updateDogInfo(
        dogInfo: DogInfo,
        beforeName: String,
        imgUri: Uri?,
        walkDateInfos: ArrayList<WalkDateInfo>
    ): Boolean {
        var error = false
        val result = CoroutineScope(Dispatchers.IO).launch {
            val dogInfoJob = async(Dispatchers.IO) {
                try {
                    if (beforeName != "") { // 수정하는 경우
                        userRef.child("dog").child(beforeName).removeValue().await()
                    }
                    userRef.child("dog").child(dogInfo.name).setValue(dogInfo).await()
                } catch (e: Exception) {
                    error = true
                    return@async
                }
            }

            dogInfoJob.await()

            if (error) {
                return@launch
            }

            val walkRecordJob = async(Dispatchers.IO) {
                try {
                    for (walkRecord in walkDateInfos) {
                        val day = walkRecord.day + " " + walkRecord.startTime + " " + walkRecord.endTime
                        userRef.child("dog").child(dogInfo.name).child("walkdates").child(day)
                            .setValue(
                                WalkDateInfoInSave(
                                    walkRecord.distance,
                                    walkRecord.time,
                                    walkRecord.coords,
                                    walkRecord.collections
                                )
                            ).await()
                    }
                } catch (e: Exception) {
                    error = true
                    return@async
                }
            }

            val uploadJob = launch(Dispatchers.IO) upload@ {
                try {
                    if (imgUri != null) {
                        storageRef.child(dogInfo.name)
                            .putFile(imgUri).await()
                    } else {
                        if (MainActivity.dogUriList[beforeName] != null) {
                            val tempUri = suspendCoroutine { continuation ->
                                val tempFile = File.createTempFile(
                                    "temp",
                                    ".jpg",
                                    context.cacheDir
                                )
                                storage.getReferenceFromUrl(MainActivity.dogUriList[beforeName].toString())
                                    .getStream { _, inputStream ->
                                        val outputStream =
                                            FileOutputStream(tempFile)
                                        inputStream.copyTo(outputStream)
                                        val tempFileUri = Uri.fromFile(tempFile)
                                        continuation.resume(tempFileUri)
                                    }
                            }
                            storageRef.child(dogInfo.name)
                                .putFile(tempUri).await()
                        }
                    }
                } catch (e: Exception) {
                    error = true
                    return@upload
                }
            }

            uploadJob.join()

            val deleteJob = launch(Dispatchers.IO) delete@ {
                try {
                    if (beforeName == "") {
                        return@delete
                    }

                    if (!MainActivity.dogNameList.contains(dogInfo.name)) {
                        storageRef.child(beforeName).delete().await()
                    }
                } catch (e: Exception) {
                    error = true
                    return@delete
                }
            }

            walkRecordJob.await()
            deleteJob.join()
        }

        result.join()
        return error
    }

    suspend fun removeDogInfo(beforeName: String) {
        val result = CoroutineScope(Dispatchers.IO).launch {
            val removeDogInfoJob = async(Dispatchers.IO) {
                try {
                    userRef.child("dog").child(beforeName).removeValue()
                        .await()
                } catch (e: Exception) {
                    return@async
                }
            }

            val removeDogImgJob = launch(Dispatchers.IO) remove@ {
                try {
                    if (MainActivity.dogUriList[beforeName] != null) {
                        storageRef.child(beforeName).delete()
                            .await()
                    }
                } catch (e: Exception) {
                    return@remove
                }
            }

            removeDogInfoJob.await()
            removeDogImgJob.join()
        }

        result.join()
    }

    suspend fun saveWalkInfo(
        walkDogs: ArrayList<String>,
        startTime: String,
        distance: Float,
        time: Int,
        coords: List<LatLng>,
        collections: List<String>
    ): Boolean {
        var isError = false
        val walkInfoUpdateJob = suspendCoroutine { continuation ->
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val endTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    val walkDateInfo = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " " + startTime + " " + endTime

                    val totalWalk = snapshot.child("totalWalkInfo").getValue(TotalWalkInfo::class.java)
                    val indivisualWalks = hashMapOf<String, TotalWalkInfo?>().also {
                        for (name in walkDogs) {
                            it[name] = snapshot.child("dog").child(name).child("walkInfo").getValue(
                                TotalWalkInfo::class.java
                            )
                        }
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        val totalWalkJob = async(Dispatchers.IO) {
                            try {
                                if (totalWalk != null) {
                                    userRef.child("totalWalkInfo").setValue(
                                        TotalWalkInfo(
                                            totalWalk.distance + distance,
                                            totalWalk.time + time
                                        )
                                    ).await()
                                } else {
                                    userRef.child("totalWalkInfo")
                                        .setValue(TotalWalkInfo(distance, time))
                                        .await()
                                }
                            } catch (e: Exception) {
                                isError = true
                                return@async
                            }
                        }

                        val indivisualWalkJob = async(Dispatchers.IO) {
                            try {
                                for (dogName in walkDogs) {
                                    if (indivisualWalks[dogName] != null) {
                                        userRef.child("dog").child(dogName).child("walkInfo")
                                            .setValue(
                                                TotalWalkInfo(
                                                    indivisualWalks[dogName]!!.distance + distance,
                                                    indivisualWalks[dogName]!!.time + time
                                                )
                                            ).await()
                                    } else {
                                        userRef.child("dog").child(dogName).child("walkInfo")
                                            .setValue(
                                                TotalWalkInfo(distance, time)
                                            ).await()
                                    }
                                }
                            } catch (e: Exception) {
                                isError = true
                                return@async
                            }
                        }

                        val walkDateInfoInSaveJob = async(Dispatchers.IO) {
                            try {
                                val saveCoords = mutableListOf<WalkLatLng>()
                                for (coord in coords) {
                                    saveCoords.add(WalkLatLng(coord.latitude, coord.longitude))
                                }
                                for (dog in walkDogs) {
                                    userRef.child("dog").child(dog).child("walkdates")
                                        .child(walkDateInfo)
                                        .setValue(
                                            WalkDateInfoInSave(
                                                distance,
                                                time,
                                                saveCoords,
                                                collections
                                            )
                                        )
                                        .await()
                                }
                            } catch (e: Exception) {
                                isError = true
                                return@async
                            }
                        }

                        val collectionInfoJob = async(Dispatchers.IO) {
                            try {
                                val update = mutableMapOf<String, Any>()
                                for (item in collections) {
                                    update[item] = true
                                }
                                userRef.child("collection").updateChildren(update).await()
                            } catch (e: Exception) {
                                isError = true
                                return@async
                            }
                        }

                        totalWalkJob.await()
                        indivisualWalkJob.await()
                        walkDateInfoInSaveJob.await()
                        collectionInfoJob.await()

                        continuation.resume(isError)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    continuation.resume(isError)
                }
            })
        }

        return walkInfoUpdateJob
    }

    suspend fun removeAccount(): Boolean {
        var error = false
        val result = CoroutineScope(Dispatchers.IO).async {
            val deleteProfileJob = async(Dispatchers.IO) {
                try {
                    storageRef.listAll().addOnSuccessListener { listResult ->
                        listResult.items.forEach { item ->
                            item.delete()
                        }
                    }
                } catch (e: Exception) {
                    Log.d("savepoint", e.message.toString())
                    error = true
                }
            }

            deleteProfileJob.await()

            if (error) {
                return@async false
            }

            val deleteInfoJob = async(Dispatchers.IO) {
                try {
                    userRef.removeValue().await()
                } catch (e: Exception) {
                    Log.d("savepoint", e.message.toString())
                    error = true
                }
            }

            deleteInfoJob.await()

            return@async !error
        }
        return result.await()
    }
}