package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins `observeFilteredReports` and `observeFilteredReportsCount` against the `ESCAPE '\'`
 * fix on their free-text LIKE predicates.
 *
 * Before the fix, a caller that pre-escaped `%`, `_`, or `\` in the needle (as
 * `ObserveReportsUseCase` does) would pass e.g. `\%` or `\_` to the DAO, which without ESCAPE
 * treats `\` as a literal and `%`/`_` as wildcards — matching far too broadly instead of the
 * intended literal character.
 *
 * Each test inserts two sessions/reports: one whose relevant field contains the literal
 * special character and one that does not. The pre-escaped query (mirroring what
 * `ObserveReportsUseCase` passes after its `replace` chain) must match only the first.
 *
 * Session labels are also tested because `observeFilteredReports` joins `sessions` and searches
 * `s.label` — that predicate was missing ESCAPE before the fix too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportDaoFilterEscapeTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var reportDao: ReportDao
    private lateinit var sessionDao: SessionDao
    private lateinit var patientDao: PatientDao

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        reportDao = db.reportDao()
        sessionDao = db.sessionDao()
        patientDao = db.patientDao()
    }

    @After
    fun tearDown() = db.close()

    // ── percent ──────────────────────────────────────────────────────────────

    @Test
    fun `percent in report_id - pre-escaped query matches only the literal-percent report`() = runTest {
        seedBase()
        seedSession("sess-1")
        seedSession("sess-2")
        // "rpt-50%" contains a literal percent; "rpt-50x" does not.
        reportDao.insertReport(report(id = "rpt-50%", sessionId = "sess-1"))
        reportDao.insertReport(report(id = "rpt-50x", sessionId = "sess-2"))

        // ObserveReportsUseCase escapes "%" → "\%"; the DAO receives "\%".
        val results = reportDao.observeFilteredReports(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = """\%""",
            limit = 50,
            offset = 0,
        ).first()

        assertEquals(
            "only the report whose id contains a literal '%' should match",
            listOf("rpt-50%"),
            results.map { it.report.reportId },
        )
    }

    @Test
    fun `percent in report_id - pre-escaped count is 1`() = runTest {
        seedBase()
        seedSession("sess-1")
        seedSession("sess-2")
        reportDao.insertReport(report(id = "rpt-50%", sessionId = "sess-1"))
        reportDao.insertReport(report(id = "rpt-50x", sessionId = "sess-2"))

        val count = reportDao.observeFilteredReportsCount(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = """\%""",
        ).first()

        assertEquals(1, count)
    }

    // ── underscore ───────────────────────────────────────────────────────────

    @Test
    fun `underscore in report_id - pre-escaped query matches only the literal-underscore report`() = runTest {
        seedBase()
        seedSession("sess-1")
        seedSession("sess-2")
        // Without ESCAPE, "\_" in the LIKE pattern would act as "_" (single-char wildcard),
        // and both "rpt_one" and "rpt-two" would match "%\_%". With ESCAPE only "rpt_one" matches.
        reportDao.insertReport(report(id = "rpt_one", sessionId = "sess-1"))
        reportDao.insertReport(report(id = "rpt-two", sessionId = "sess-2"))

        // ObserveReportsUseCase escapes "_" → "\_"; the DAO receives "%\_%".
        val results = reportDao.observeFilteredReports(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = """\_%""",
            limit = 50,
            offset = 0,
        ).first()

        assertEquals(
            "only the report whose id contains a literal '_' should match",
            listOf("rpt_one"),
            results.map { it.report.reportId },
        )
    }

    @Test
    fun `underscore in report_id - pre-escaped count is 1`() = runTest {
        seedBase()
        seedSession("sess-1")
        seedSession("sess-2")
        reportDao.insertReport(report(id = "rpt_one", sessionId = "sess-1"))
        reportDao.insertReport(report(id = "rpt-two", sessionId = "sess-2"))

        val count = reportDao.observeFilteredReportsCount(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = """\_%""",
        ).first()

        assertEquals(1, count)
    }

    // ── backslash ────────────────────────────────────────────────────────────

    @Test
    fun `backslash in positive_species_json - pre-escaped query matches only the literal-backslash report`() = runTest {
        seedBase()
        seedSession("sess-1")
        seedSession("sess-2")
        // One report has a literal backslash in its species JSON; the other does not.
        reportDao.insertReport(
            report(
                id = "rpt-bs",
                sessionId = "sess-1",
                positiveSpeciesJson = """["Ascaris\lumbricoides"]""",
            ),
        )
        reportDao.insertReport(
            report(
                id = "rpt-plain",
                sessionId = "sess-2",
                positiveSpeciesJson = """["Ascaris lumbricoides"]""",
            ),
        )

        // ObserveReportsUseCase escapes "\" → "\\"; the DAO receives "\\".
        val results = reportDao.observeFilteredReports(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = """\\""",
            limit = 50,
            offset = 0,
        ).first()

        assertEquals(
            "only the report whose species JSON contains a literal '\\' should match",
            listOf("rpt-bs"),
            results.map { it.report.reportId },
        )
    }

    @Test
    fun `backslash in positive_species_json - pre-escaped count is 1`() = runTest {
        seedBase()
        seedSession("sess-1")
        seedSession("sess-2")
        reportDao.insertReport(
            report(
                id = "rpt-bs",
                sessionId = "sess-1",
                positiveSpeciesJson = """["Ascaris\lumbricoides"]""",
            ),
        )
        reportDao.insertReport(
            report(
                id = "rpt-plain",
                sessionId = "sess-2",
                positiveSpeciesJson = """["Ascaris lumbricoides"]""",
            ),
        )

        val count = reportDao.observeFilteredReportsCount(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = """\\""",
        ).first()

        assertEquals(1, count)
    }

    // ── session label (joined column) ────────────────────────────────────────

    @Test
    fun `underscore in session label - pre-escaped query matches only the literal-underscore label`() = runTest {
        // The query also searches s.label via the LEFT JOIN; verify ESCAPE is honoured there too.
        seedBase()
        seedSession("sess-1")
        seedSession("sess-2")
        sessionDao.updateSessionLabel("sess-1", "Smear_A")   // literal underscore
        sessionDao.updateSessionLabel("sess-2", "Smear-B")   // hyphen, not underscore
        reportDao.insertReport(report(id = "rpt-us",    sessionId = "sess-1"))
        reportDao.insertReport(report(id = "rpt-plain", sessionId = "sess-2"))

        // Pre-escaped query: the literal underscore in "Smear_A" is escaped to "Smear\_A".
        val results = reportDao.observeFilteredReports(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = """Smear\_A""",
            limit = 50,
            offset = 0,
        ).first()

        assertEquals(
            "only the report whose session label is 'Smear_A' literally should match",
            listOf("rpt-us"),
            results.map { it.report.reportId },
        )
    }

    // ── blank query returns all ──────────────────────────────────────────────

    @Test
    fun `blank query returns all user reports`() = runTest {
        seedBase()
        seedSession("sess-1")
        seedSession("sess-2")
        reportDao.insertReport(report(id = "rpt-a", sessionId = "sess-1"))
        reportDao.insertReport(report(id = "rpt-b", sessionId = "sess-2"))

        val results = reportDao.observeFilteredReports(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = "",
            limit = 50,
            offset = 0,
        ).first()

        assertEquals(2, results.size)
    }

    @Test
    fun `blank query count matches total report count`() = runTest {
        seedBase()
        seedSession("sess-1")
        seedSession("sess-2")
        reportDao.insertReport(report(id = "rpt-a", sessionId = "sess-1"))
        reportDao.insertReport(report(id = "rpt-b", sessionId = "sess-2"))

        val count = reportDao.observeFilteredReportsCount(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = "",
        ).first()

        assertEquals(2, count)
    }

    // ── no-match query ───────────────────────────────────────────────────────

    @Test
    fun `query that matches nothing returns empty list and zero count`() = runTest {
        seedBase()
        seedSession("sess-1")
        reportDao.insertReport(report(id = "rpt-a", sessionId = "sess-1"))

        val results = reportDao.observeFilteredReports(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = "no-such-needle-xyz",
            limit = 50,
            offset = 0,
        ).first()
        val count = reportDao.observeFilteredReportsCount(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = "no-such-needle-xyz",
        ).first()

        assertEquals(0, results.size)
        assertEquals(0, count)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Seeds the single patient row that sessions.patient_id FK requires.
     * Must be called at the start of every test.
     */
    private suspend fun seedBase() {
        patientDao.upsertPatient(
            PatientEntity(
                patientId = PATIENT_ID,
                lastname = "Cruz",
                firstname = "Gerald",
                middleName = null,
                sex = "M",
                birthdate = 0L,
                psgcBarangayCode = "0102801001",
                createdBy = USER_ID,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
    }

    private suspend fun seedSession(sessionId: String) {
        sessionDao.upsertSession(
            SessionEntity(
                sessionId = sessionId,
                userId = USER_ID,
                patientId = PATIENT_ID,
                deviceId = "device-1",
                startedAt = 1_000L,
                label = null,
            ),
        )
    }

    private fun report(
        id: String,
        sessionId: String,
        positiveSpeciesJson: String = """["Ascaris lumbricoides"]""",
    ) = ReportEntity(
        reportId = id,
        sessionId = sessionId,
        userId = USER_ID,
        generatedAt = 3_000L,
        totalSamples = 1,
        totalEggsConfirmed = 1,
        positiveSpeciesJson = positiveSpeciesJson,
        lpfPerSpeciesJson = "{}",
        csvFilePath = null,
        pdfFilePath = null,
        createdAt = 3_000L,
    )

    private companion object {
        const val PATIENT_ID = "patient-1"
        const val USER_ID = "user-a"
    }
}
