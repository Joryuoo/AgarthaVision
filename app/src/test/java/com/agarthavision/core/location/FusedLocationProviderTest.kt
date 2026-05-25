package com.agarthavision.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FusedLocationProviderTest {

    private val context: Context = mock()
    private val client: FusedLocationProviderClient = mock()
    private val provider = FusedLocationProvider(context, client)

    @Test
    fun `both permissions denied - returns null without calling client`() = runTest {
        mockStatic(ContextCompat::class.java).use { ctxStatic ->
            ctxStatic.`when`<Int> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            }.thenReturn(PackageManager.PERMISSION_DENIED)
            ctxStatic.`when`<Int> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            }.thenReturn(PackageManager.PERMISSION_DENIED)

            val result = provider.getCurrentLocation()

            assertNull(result)
            verify(client, never()).getCurrentLocation(any<Int>(), any())
        }
    }

    @Test
    fun `client never resolves within timeout - returns null`() = runTest {
        mockStatic(ContextCompat::class.java).use { ctxStatic ->
            ctxStatic.`when`<Int> {
                ContextCompat.checkSelfPermission(any(), any())
            }.thenReturn(PackageManager.PERMISSION_GRANTED)

            val pendingTask: Task<Location> = mock()
            whenever(pendingTask.addOnSuccessListener(any<OnSuccessListener<Location>>()))
                .thenReturn(pendingTask)
            whenever(pendingTask.addOnFailureListener(any<OnFailureListener>()))
                .thenReturn(pendingTask)
            whenever(client.getCurrentLocation(any<Int>(), any())).thenReturn(pendingTask)

            val result = provider.getCurrentLocation(timeoutMillis = 100L)

            assertNull(result)
        }
    }
}
