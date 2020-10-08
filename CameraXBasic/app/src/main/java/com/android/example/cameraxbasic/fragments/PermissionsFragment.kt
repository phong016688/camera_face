/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.cameraxbasic.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.android.example.cameraxbasic.R
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

private const val PERMISSIONS_REQUEST_CODE = 10
private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE)

/**
 * The sole purpose of this fragment is to request permissions and, once granted, display the
 * camera fragment to the user.
 */
class PermissionsFragment : Fragment() {

    private val faceSDKNative = FaceSDKNative.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasPermissions(requireContext())) {
            // Request camera-related permissions
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        } else {
            // If permissions have already been granted, proceed

            try {
                //RFB-320-quant-ADMM-32
                copyBigDataToSD("RFB-320.mnn")
                copyBigDataToSD("RFB-320-quant-ADMM-32.mnn")
                copyBigDataToSD("RFB-320-quant-KL-5792.mnn")
                copyBigDataToSD("slim-320.mnn")
                copyBigDataToSD("slim-320-quant-ADMM-50.mnn")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val sdDir: File = Environment.getExternalStorageDirectory() //get model store dir

            val sdPath: String = sdDir.toString().toString() + "/facesdk/"
            faceSDKNative.FaceDetectionModelInit(sdPath)

            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                    PermissionsFragmentDirections.actionPermissionsToCamera())
        }
    }

    private fun copyBigDataToSD(strOutFileName: String) {
        try {
            Log.i("###", "start copy file $strOutFileName")
            val sdDir = Environment.getExternalStorageDirectory() //get root dir
            val file = File("$sdDir/facesdk/")
            if (!file.exists()) {
                file.mkdir()
            }
            val tmpFile = "$sdDir/facesdk/$strOutFileName"
            val f = File(tmpFile)
            if (f.exists()) {
                Log.i("###", "file exists $strOutFileName")
                return
            }
            val myOutput: OutputStream = FileOutputStream("$sdDir/facesdk/$strOutFileName")
            val myInput: InputStream = activity?.assets?.open(strOutFileName) ?: return
            val buffer = ByteArray(1024)
            var length: Int = myInput.read(buffer)
            while (length > 0) {
                myOutput.write(buffer, 0, length)
                length = myInput.read(buffer)
            }
            myOutput.flush()
            myInput.close()
            myOutput.close()
            Log.i("####", "end copy file $strOutFileName")
        } catch (e: Exception) {
            Log.d("###", e.message.toString())
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Take the user to the success fragment when permission is granted
                Toast.makeText(context, "Permission request granted", Toast.LENGTH_LONG).show()
                Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                        PermissionsFragmentDirections.actionPermissionsToCamera())
            } else {
                Toast.makeText(context, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {

        /** Convenience method used to check if all permissions required by this app are granted */
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
