package com.agarthavision.data.repository

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.PsgcBarangayDao
import com.agarthavision.data.local.entity.PsgcBarangayEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * In-memory Room tests for [PsgcRepositoryImpl.getBarangay].
 *
 * There is no network path here and there must not become one: the PSGC dataset ships in
 * the APK and is Room-seeded precisely so the picker and every barangay label keep working
 * with the radio off, which is the condition medtechs collect in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PsgcReverseLookupTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var dao: PsgcBarangayDao
    private lateinit var repository: PsgcRepositoryImpl

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.psgcBarangayDao()
        repository = PsgcRepositoryImpl(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed() {
        dao.insertAll(
            listOf(
                barangay(
                    code = "0723017001",
                    name = "Lahug",
                    cityMuniName = "City of Cebu",
                    provinceName = null,
                    regionName = "Region VII (Central Visayas)",
                ),
                barangay(
                    code = "0102801001",
                    name = "Adams",
                    cityMuniName = "Adams",
                    provinceName = "Ilocos Norte",
                    regionName = "Region I (Ilocos Region)",
                ),
            ),
        )
    }

    @Test
    fun `a known code resolves to its barangay`() = runTest {
        seed()

        val barangay = repository.getBarangay("0723017001")

        assertEquals("Lahug", barangay?.name)
        assertEquals("City of Cebu", barangay?.cityMuniName)
    }

    @Test
    fun `parentPath is reused rather than recomposed at the call site`() = runTest {
        seed()

        assertEquals(
            "City of Cebu · Region VII (Central Visayas)",
            repository.getBarangay("0723017001")?.parentPath,
        )
        assertEquals("Adams · Ilocos Norte", repository.getBarangay("0102801001")?.parentPath)
    }

    @Test
    fun `an unknown code returns null rather than throwing`() = runTest {
        seed()

        // A PSGC vintage change can retire a code a patient row still carries. The caller
        // renders the raw code; it must not crash and must not show a blank.
        assertNull(repository.getBarangay("9999999999"))
    }

    @Test
    fun `an empty table returns null rather than throwing`() = runTest {
        assertNull(repository.getBarangay("0723017001"))
    }

    private fun barangay(
        code: String,
        name: String,
        cityMuniName: String,
        provinceName: String?,
        regionName: String,
    ) = PsgcBarangayEntity(
        code = code,
        name = name,
        cityMuniCode = code.take(6),
        cityMuniName = cityMuniName,
        provinceCode = provinceName?.let { code.take(4) },
        provinceName = provinceName,
        regionCode = code.take(2),
        regionName = regionName,
        // Region names are deliberately out of the haystack, per PsgcBarangayEntity.
        searchText = "$name $cityMuniName ${provinceName.orEmpty()}".lowercase(),
    )
}
