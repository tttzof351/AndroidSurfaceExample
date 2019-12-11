package com.example.surfaces.helpers

import android.app.Activity
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat

class PermissionHelper(
    var activity: Activity
) {
    private var callbacks = HashMap<String, () -> Unit>()

    fun checkOrAsk(permission: String, success: () -> Unit) {
        callbacks[permission] = success

        val checkPerm = ContextCompat.checkSelfPermission(activity, permission)

        if (checkPerm == PERMISSION_GRANTED) {
            success.invoke()
        } else {
            askPermission(activity, permission)
        }
    }

    fun bindTOonReqResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
                    permissions.getOrNull(0)?.let { permission ->
                        callbacks[permission]?.invoke()
                        callbacks.remove(permission)
                    }
                }
                return
            }

            else -> {
                // Ignore all other requests.
            }
        }
    }

    private fun askPermission(activity: Activity, permission: String) {
        val shouldPermission = shouldShowRequestPermissionRationale(activity, permission)

        if (shouldPermission) {
        } else {
            requestPermissions(activity, arrayOf(permission), PERMISSION)
        }
    }

    companion object {
        const val PERMISSION = 1
    }
}