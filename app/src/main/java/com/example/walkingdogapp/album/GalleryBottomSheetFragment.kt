package com.example.walkingdogapp.album

import android.app.Activity.RESULT_OK
import android.content.DialogInterface
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.walkingdogapp.databinding.FragmentGalleryBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import retrofit2.converter.gson.GsonConverterFactory

class GalleryBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentGalleryBottomSheetBinding? = null
    private val binding get() = _binding!!

    fun interface OnDeleteImgListener {
        fun onDeleteImg()
    }

    var onDeleteImgListener: OnDeleteImgListener? = null

    private val launcher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        when(result.resultCode) {
            RESULT_OK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dismiss()
                onDeleteImgListener?.onDeleteImg()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBottomSheetBinding.inflate(inflater, container, false)
        val date = arguments?.getString("date") ?: "2024년 03월 20일 00:00"
        val imgUri = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("uri", Uri::class.java) ?: Uri.EMPTY
        } else {
            arguments?.getParcelable("uri") ?: Uri.EMPTY
        }

        binding.apply {
            day = date
            removeimg.setOnClickListener {
                imgUri?.also {
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intentSender = MediaStore.createDeleteRequest(requireActivity().contentResolver, mutableListOf(it)).intentSender
                        launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                    } else {
                        val builder = AlertDialog.Builder(requireContext())
                        builder.setTitle("사진을 삭제하시겠습니까?")
                        val listener = DialogInterface.OnClickListener { _, ans ->
                            when (ans) {
                                DialogInterface.BUTTON_POSITIVE -> {
                                    requireActivity().contentResolver.delete(imgUri, null, null)
                                    dismiss()
                                    onDeleteImgListener?.onDeleteImg()
                                }
                            }
                        }
                        builder.setPositiveButton("네", listener)
                        builder.setNegativeButton("아니오", null)
                        builder.show()
                    }
                }
            }
        }

        this.dialog?.window!!.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        this.dialog?.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}