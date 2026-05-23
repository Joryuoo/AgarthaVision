package com.agarthavision.domain.usecase.capture

import android.os.Build
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.exifinterface.media.ExifInterface
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.location.LocationProvider
import com.agarthavision.domain.model.Sample
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

import com.agarthavision.domain.repository.SampleRepository
/**
 * Use case to capture a sample with full metadata binding.
 * See docs/03_MOBILE_APP_PLAN.md §1.3.
 */
class CaptureSampleUseCase @Inject constructor(
    private val cameraManager: CameraManager,
    private val locationProvider: LocationProvider,
    private val sampleRepository: SampleRepository,
) {
    suspend operator fun invoke(imageCapture: ImageCapture): Result<Sample> = try {
        // 1. Capture the image file
        val file = cameraManager.captureImage(imageCapture)
        
        // 2. Gather metadata
        val location = locationProvider.getCurrentLocation()
        
        // TODO: Session ID should probably come from a SessionManager or similar
        val sessionId = "SESSION_${System.currentTimeMillis()}" 
        val deviceId = "${Build.MANUFACTURER}_${Build.MODEL}_${Build.ID}"

        val sample = Sample(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            deviceId = deviceId,
            sessionId = sessionId,
            filePath = file.absolutePath,
            latitude = location?.latitude,
            longitude = location?.longitude
        )

        // 3. Embed metadata into the image file
        embedMetadata(file, sample)

        sampleRepository.saveSample(sample)
        Log.d("CaptureSampleUseCase", "Sample saved locally: ${sample.id}")
        Result.success(sample)

        Result.success(sample)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun embedMetadata(file: File, sample: Sample) {
        try {
            val exif = ExifInterface(file.absolutePath)
            
            exif.setAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID, sample.id)
            
            val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateFormat.format(Date(sample.timestamp)))
            
            exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "AgarthaVision")
            
            val customMetadata = "SampleID: ${sample.id}, SessionID: ${sample.sessionId}, DeviceID: ${sample.deviceId}, Status: ${sample.status.value}"
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, customMetadata)

            if (sample.latitude != null && sample.longitude != null) {
                exif.setLatLong(sample.latitude, sample.longitude)
            }

            exif.saveAttributes()
            Log.d("CaptureSampleUseCase", "Metadata embedded successfully for ${file.name}")
        } catch (e: Exception) {
            Log.e("CaptureSampleUseCase", "Failed to embed metadata", e)
        }
    }
}
