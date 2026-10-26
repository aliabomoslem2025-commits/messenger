/*
 * Copyright 2021-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.lib.multipicker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import im.vector.lib.multipicker.entity.MultiPickerVideoType
import im.vector.lib.multipicker.utils.MediaType
import im.vector.lib.multipicker.utils.createTemporaryMediaFile
import im.vector.lib.multipicker.utils.toMultiPickerVideoType

/**
 * Implementation of taking a video with Camera.
 */
class CameraVideoPicker {

    /**
     * Start camera by using a ActivityResultLauncher.
     * @return Uri of taken photo or null if the operation is cancelled.
     */
    fun startWithExpectingFile(
            context: Context,
            activityResultLauncher: ActivityResultLauncher<Intent>,
            maxDurationSeconds: Int? = null
    ): Uri {
        val videoUri = createVideoUri(context)
        val intent = createIntent().apply {
            putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
            maxDurationSeconds?.takeIf { it > 0 }?.let {
                putExtra(MediaStore.EXTRA_DURATION_LIMIT, it)
            }
        }
        activityResultLauncher.launch(intent)
        return videoUri
    }

    /**
     * Call this function from onActivityResult(int, int, Intent).
     * @return Taken photo or null if request code is wrong
     * or result code is not Activity.RESULT_OK
     * or user cancelled the operation.
     */
    fun getTakenVideo(context: Context, videoUri: Uri): MultiPickerVideoType? {
        return videoUri.toMultiPickerVideoType(context)
    }

    private fun createIntent(): Intent {
        return Intent(MediaStore.ACTION_VIDEO_CAPTURE)
    }

    companion object {
        /**
         * A capture output is shared with the app's recorder Activity so it can write
         * directly to the same FileProvider-backed file consumed by the existing
         * Matrix attachment pipeline.
         */
        data class VideoCaptureOutput(val uri: Uri, val file: File)

        fun createVideoCaptureOutput(context: Context, videoNote: Boolean = false): VideoCaptureOutput {
            val temporaryFile = createTemporaryMediaFile(context, MediaType.VIDEO)
            val file = if (videoNote) {
                val namedFile = File(temporaryFile.parentFile, "video_note_${System.currentTimeMillis()}.mp4")
                if (temporaryFile.renameTo(namedFile)) namedFile else temporaryFile
            } else {
                temporaryFile
            }
            val authority = context.packageName + ".multipicker.fileprovider"
            return VideoCaptureOutput(FileProvider.getUriForFile(context, authority, file), file)
        }

        fun createVideoUri(context: Context): Uri = createVideoCaptureOutput(context).uri
    }
}
