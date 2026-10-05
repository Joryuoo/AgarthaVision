package com.agarthavision.domain.usecase.sync

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.sync.FetchOutcomeStore
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.data.inference.encodePredictions
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.ColleagueDao
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.PatientUserEntity
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.local.mapper.toFramePredictionsOrNull
import com.agarthavision.data.local.species.SpeciesSuggestionSeeder
import com.agarthavision.data.supabase.PatientRemoteDataSource
import com.agarthavision.data.supabase.ProfileRemoteDataSource
import com.agarthavision.data.supabase.ReportRemoteDataSource
import com.agarthavision.data.supabase.SampleRemoteDataSource
import com.agarthavision.data.supabase.SessionRemoteDataSource
import com.agarthavision.domain.model.PatientSyncStatus
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.repository.AuthRepository
import com.google.gson.Gson
import javax.inject.Inject

/**
 * Outcome of a [FetchRemoteDataUseCase] pass.
 */
sealed interface FetchSummary {
    /** Skipped because the medtech is signed out or the device is offline. */
    data object Skipped : FetchSummary

    /**
     * Ran the pass. Counts are rows inserted or updated locally from the remote.
     *
     * [failed] is the point of this type. Each entity pulls under its own `runCatching`, so a
     * count of zero means "nothing new" and "threw four times" alike, and for a long while the
     * pass reported success while every pull was failing. Carrying the failures makes the
     * caller able to tell those apart - and lets the worker ask WorkManager to try again.
     */
    data class Ran(
        val patientsFetched: Int,
        val sessionsFetched: Int,
        val samplesFetched: Int,
        val reportsFetched: Int,
        val failed: Set<FetchType> = emptySet(),
        /** Sample frames brought onto the device by this pass (86d4by5n9). */
        val imagesFetched: Int = 0,
    ) : FetchSummary {
        /** True when every entity type pulled without throwing and no frame is still missing. */
        val isComplete: Boolean get() = failed.isEmpty()
    }
}

/** The entity types a pull pass covers, so a failure can name itself. */
enum class FetchType {
    PATIENTS,
    SESSIONS,
    SAMPLES,
    REPORTS,

    /**
     * The sample frames themselves (86d4by5n9).
     *
     * Present in [FetchSummary.Ran.failed] whenever the device still lacks a frame it should
     * hold — a download that failed, or one the per-pass ceiling deferred. Rows without frames
     * is not a synced device: a sample whose image is missing cannot be opened and therefore
     * cannot be corrected, which is the whole point of holding it.
     */
    SAMPLE_IMAGES,
}

/**
 * Pulls all remote rows from Supabase into the local Room database for the signed-in
 * medtech. Run in FK-safe order (patients → sessions → samples+detections+findings →
 * reports).
 *
 * **What "all" means is decided here, not by RLS** (14zcqntjt3p). The device holds the patients
 * linked to the signed-in user in `patient_users`, and for those patients their full history:
 * colleagues' sessions, samples and reports included (14zcqntjph5). It also holds the user's own
 * rows. That is exactly what a medtech's policies return (0001's author policies plus 0007's
 * `*_select_via_patient`), and every fetch below asks for it explicitly, as "own" plus "under
 * these parents", because the same tables give an org admin their whole laboratory (console
 * `admin/0002`). An org admin may use the phone, often as a laboratory's medtech too, and gets
 * only their own patients here. A super admin does not use the phone. The parents come from
 * the device, read after the step before has written them, so a failed step still leaves the
 * next one a scope.
 * A colleague's row lands `synced` and is never edited here (14zcqntjph6), so the E4 guard lets
 * every later pull refresh it.
 *
 * **A row whose parent is not on the device is skipped, not written.** The server keeps an
 * author's read access to their own sessions after they are unassigned from the patient, so it
 * can return a session whose patient it no longer returns. Room enforces `sessions.patient_id`
 * and `samples.session_id`, and one such row used to fail the whole entity type on every pass.
 *
 * Patients come first because `sessions.patient_id` is a foreign key onto `patients`: a
 * session row arriving before the patient it belongs to violates it locally, and Room does
 * enforce this one.
 *
 * Per the additive-only policy (D4): rows present only on the server are inserted locally;
 * locally-VERIFIED or SYNC_FAILED rows are never overwritten by a server pull (E4 guard).
 * Deleted samples (deleted_at not null) become tombstoned local rows, which existing
 * `deleted_at IS NULL` guards already hide from all lists and counts.
 *
 * [InitialFetchStateStore.markCompleted] is called only when **all four** entity types
 * succeeded; a partial failure leaves the flag unset so the badge stays NOT_YET_SYNCED
 * and the medtech can retry via "Sync now". Per E2. Claiming a complete offline cache the
 * device does not have is the failure this guards: the medtech finds out in a barangay
 * with no signal.
 */
// One pull per entity type plus the per-row reconcilers each one needs; splitting them across
// classes would scatter a single FK-ordered pass that has to be read top to bottom.
@Suppress("LongParameterList", "TooManyFunctions")
class FetchRemoteDataUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val patientRemoteDataSource: PatientRemoteDataSource,
    private val sampleRemoteDataSource: SampleRemoteDataSource,
    private val sessionRemoteDataSource: SessionRemoteDataSource,
    private val reportRemoteDataSource: ReportRemoteDataSource,
    private val patientDao: PatientDao,
    private val sessionDao: SessionDao,
    private val sampleDao: SampleDao,
    private val detectionDao: DetectionDao,
    private val sampleSpeciesFindingDao: SampleSpeciesFindingDao,
    private val reportDao: ReportDao,
    private val initialFetchStateStore: InitialFetchStateStore,
    private val fetchOutcomeStore: FetchOutcomeStore,
    private val speciesSuggestionSeeder: SpeciesSuggestionSeeder,
    private val cacheSampleImages: CacheSampleImagesUseCase,
    private val sampleImageStore: SampleImageStore,
    private val gson: Gson,
    private val profileRemoteDataSource: ProfileRemoteDataSource,
    private val colleagueDao: ColleagueDao,
) {
    /**
     * Runs one fetch pass.
     *
     * @return [Result.success] with a [FetchSummary]; per-entity type failures are caught
     * locally and do not abort the other types. [Result.failure] only on an unexpected
     * error outside the per-type catch blocks.
     */
    suspend operator fun invoke(): Result<FetchSummary> = runCatching {
        val userId = authRepository.currentLocalUserId()
        if (userId == null || !authRepository.isAuthenticated() || !connectivityObserver.currentlyOnline()) {
            return@runCatching FetchSummary.Skipped
        }

        // Each type runs under its own runCatching so one failing type does not abort the
        // others; the *Ok flags gate markCompleted so a partial failure stays NOT_YET_SYNCED.
        // FK-safe order: patients (root) → sessions → samples (+children) → reports.
        var patientsOk = false
        var sessionsOk = false
        var samplesOk = false
        var reportsOk = false
        var patientsFetched = 0
        var sessionsFetched = 0
        var samplesFetched = 0
        var reportsFetched = 0

        runCatching { patientsFetched = pullPatients(userId); patientsOk = true }
            .onFailure { error -> Log.e(TAG, "Fetch patients failed", error) }

        runCatching { sessionsFetched = pullSessions(userId); sessionsOk = true }
            .onFailure { error -> Log.e(TAG, "Fetch sessions failed", error) }

        runCatching { samplesFetched = pullSamples(userId); samplesOk = true }
            .onFailure { error -> Log.e(TAG, "Fetch samples/detections/findings failed", error) }

        runCatching { reportsFetched = pullReports(userId); reportsOk = true }
            .onFailure { error -> Log.e(TAG, "Fetch reports failed", error) }

        // Colleagues' names, so a read-only record can say whose it is offline (14zcqntjph6).
        // Best-effort and outside the completeness sets below: a missing name costs a label,
        // not a record, and must not hold the badge at NOT_YET_SYNCED.
        runCatching { pullColleagues(userId) }
            .onFailure { error -> Log.w(TAG, "Fetch colleague names failed", error) }

        // Frames last: every one of them hangs off a sample row, so there is nothing to cache
        // until the rows are down. It never throws — an unreachable object is counted, not
        // raised — so it needs no runCatching of its own.
        val images = cacheSampleImages(userId)

        // Mark completed only when all four row types succeeded (E2). Listed rather than
        // chained so a fifth entity type is one entry, not a longer boolean expression.
        val rowFailures = buildSet {
            if (!patientsOk) add(FetchType.PATIENTS)
            if (!sessionsOk) add(FetchType.SESSIONS)
            if (!samplesOk) add(FetchType.SAMPLES)
            if (!reportsOk) add(FetchType.REPORTS)
        }

        // **Two different questions, deliberately answered from two different sets.**
        //
        // `markCompleted` asks "did this account's rows finish arriving" - it is what stops the
        // badge reading NOT_YET_SYNCED forever, and it must not be held hostage by images. A
        // frame whose Storage object is gone would otherwise pin the flag shut for good, and a
        // device that has every row would keep claiming it has none.
        //
        // The Settings card asks the harder question: "is this device ready to work offline?"
        // Rows without frames is not ready, so images count there. A pass that fetched every
        // row and no image must not read as All synced.
        val failed = rowFailures + if (images.isComplete) emptySet() else setOf(FetchType.SAMPLE_IMAGES)
        if (rowFailures.isEmpty()) {
            initialFetchStateStore.markCompleted(userId)
        }
        // Recorded rather than only returned: most passes run in the worker now, with no
        // caller on screen to see the result, and the Settings card has to be able to say so
        // afterwards.
        fetchOutcomeStore.record(userId = userId, complete = failed.isEmpty())

        // Fold any species that arrived with this pass into the offline suggestion index, so
        // the two reference caches stay in step (PB-08a). Ungated on purpose: the seeder
        // re-derives from rows the device already holds, so a pass where only samples failed
        // still has work for it, and it never throws, so it cannot fail the pass. The
        // offline/unauthenticated returns are above, which makes this "every pass that ran".
        speciesSuggestionSeeder.refresh()

        FetchSummary.Ran(
            patientsFetched = patientsFetched,
            sessionsFetched = sessionsFetched,
            samplesFetched = samplesFetched,
            reportsFetched = reportsFetched,
            failed = failed,
            imagesFetched = images.downloaded,
        )
    }

    /**
     * Pulls only the patient list (links, patients, revoked-link removal), for the sign-in path
     * that waits on nothing else.
     *
     * Same preconditions as [invoke]; when one fails it returns `Result.success(0)`, as [invoke]
     * reports a skip as a success. Deliberately does **not** call
     * [InitialFetchStateStore.markCompleted] or [FetchOutcomeStore.record]: a patients-only pull
     * says nothing about whether the whole account has arrived, and the background pass that
     * follows sign-in owns those answers.
     *
     * Safe ahead of the push: [pullPatients] writes only absent or SYNCED rows and
     * [removeRevokedLinks] keeps the link of any patient not yet SYNCED (E4).
     *
     * @return rows written locally.
     */
    suspend fun pullPatientsOnly(): Result<Int> = runCatching {
        val userId = authRepository.currentLocalUserId()
        if (userId == null || !authRepository.isAuthenticated() || !connectivityObserver.currentlyOnline()) {
            return@runCatching 0
        }
        pullPatients(userId)
    }

    /**
     * Patients are the FK root — pull them before sessions. Returns rows inserted.
     *
     * The `patient_users` link rows come down in the same pass and are **not** optional:
     * `PatientDao` resolves visibility through that join, so a patient whose link is
     * missing sits on the device invisible to every query that reads it. Links are
     * inserted after the patients they reference, and with `OnConflictStrategy.IGNORE`, so
     * a re-pull is a no-op rather than a duplicate-key failure.
     *
     * E4 guard: only insert when the local row is absent or already SYNCED. A local
     * PENDING or SYNC_FAILED patient is unsynced work the medtech typed in offline, and
     * overwriting it with the server's copy loses a patient by hand.
     *
     * Once every page of links has arrived, [removeRevokedLinks] drops the ones the server no
     * longer holds, so a patient an admin reassigned away from this medtech leaves their list.
     */
    private suspend fun pullPatients(userId: String): Int {
        // The user's own links decide which patients come down at all (14zcqntjt3p).
        val links = mutableListOf<PatientUserEntity>()
        forEachPage({ offset, limit -> patientRemoteDataSource.fetchPatientLinks(userId, offset, limit) }) { page ->
            links += page
        }
        val serverLinkedPatientIds = links.mapTo(mutableSetOf()) { it.patientId }

        var fetched = 0
        serverLinkedPatientIds.chunked(CHILD_BATCH_SIZE).forEach { chunk ->
            for (remote in patientRemoteDataSource.fetchPatients(chunk)) {
                val local = patientDao.getPatientById(remote.patientId)
                if (local == null || local.supabaseStatus == PatientSyncStatus.SYNCED.value) {
                    patientDao.upsertPatient(remote)
                    fetched++
                }
            }
        }

        if (links.isNotEmpty()) {
            patientDao.linkPatientsToUsers(links)
        }
        removeRevokedLinks(userId, serverLinkedPatientIds)
        return fetched
    }

    /**
     * Drops this medtech's local assignments that the server no longer returns.
     *
     * Reached only after every link page arrived — a page that throws aborts [pullPatients]
     * before this — so an absent link means an admin removed it, not that a page was lost.
     *
     * A patient still waiting to push keeps its link. Its creator link is written locally at
     * creation and reaches the server only with the patient's own insert (the
     * `on_patient_created` trigger), so the server has not heard of it yet; removing it would
     * hide an offline patient from the medtech who typed it in.
     *
     * The patient and everything recorded against it stay on the device (C8). Only the access
     * row goes, which is the same thing the admin's reassignment did on the server.
     */
    private suspend fun removeRevokedLinks(userId: String, serverLinkedPatientIds: Set<String>) {
        patientDao.getLinksForUser(userId)
            .filter { it.patientId !in serverLinkedPatientIds }
            .forEach { link ->
                val patient = patientDao.getPatientById(link.patientId)
                if (patient == null || patient.supabaseStatus == PatientSyncStatus.SYNCED.value) {
                    patientDao.unlinkPatientFromUser(link.patientId, userId)
                }
            }
    }

    /**
     * Sessions are pulled after patients, before samples and reports. Returns rows inserted.
     *
     * **Label collision reconciliation** — before the unique `(patient_id, label)` index was
     * added, two offline devices could each mint the same label for the same patient (this was
     * an explicitly accepted case). Production Supabase may therefore already contain duplicate
     * `(patient_id, label)` rows from before this branch's fix. Rather than add a Supabase-side
     * unique constraint now — which would fail to apply against any existing violating data, and
     * which requires a data-cleanup pass on the production table that cannot be done safely from
     * here — we reconcile at pull time instead via [upsertSessionReconcilingLabel].
     *
     * **The server now settles new collisions itself** (14zcqntjph7).
     * `0009_session_label_collisions.sql` renames a label that another session of the same
     * patient already holds, on insert and on rename, with this same suffix scheme, and the
     * pull brings the new label back as an ordinary change to a `synced` row. Two phones minting
     * `S03` offline therefore end with one `S03` and one `S03-XXXX` once both have synced. What is
     * left for this function is the rows written before that migration, and the window before
     * this device's own push lands.
     *
     * **Collisions are settled once every page is in, not inside one.** Pages arrive oldest first, so
     * a colleague's earlier `S03` can be read before this device's own row is updated with the
     * suffixed label the server gave it. Writing it immediately would suffix the colleague's
     * row, which the server left alone, and only the next pass would put it back. Deferring the
     * colliding rows until everything else is written lets the renamed own row move first.
     */
    private suspend fun pullSessions(userId: String): Int {
        var fetched = 0
        val collisions = mutableListOf<SessionEntity>()
        val seen = mutableSetOf<String>()
        forEachScopedPage(
            own = { offset, limit -> sessionRemoteDataSource.fetchOwnSessions(userId, offset, limit) },
            parentIds = linkedPatientIds(userId),
            underParents = { patientIds, offset, limit ->
                sessionRemoteDataSource.fetchSessionsForPatients(patientIds, offset, limit)
            },
        ) { page ->
            for (remote in page) {
                if (seen.add(remote.sessionId) && pullSession(remote, collisions)) fetched++
            }
        }
        collisions.forEach { remote -> upsertSessionReconcilingLabel(remote) }
        return fetched
    }

    /**
     * Writes one pulled session, or defers it to [collisions] when its label clashes locally.
     * Returns whether the row counts as fetched.
     */
    private suspend fun pullSession(remote: SessionEntity, collisions: MutableList<SessionEntity>): Boolean {
        if (!patientDao.patientExists(remote.patientId)) {
            Log.w(TAG, "pullSessions: patient ${remote.patientId} not on device; skipping ${remote.sessionId}")
            return false
        }
        val local = sessionDao.getSessionById(remote.sessionId)
        // E4 guard: only write when absent or already synced; skip pending/sync_failed
        val writable = local == null || local.supabaseStatus == SessionSyncStatus.SYNCED.value
        if (writable) {
            if (collidesLocally(remote)) {
                collisions += remote
            } else {
                upsertSessionReconcilingLabel(remote)
            }
        }
        return writable
    }

    private suspend fun collidesLocally(remote: SessionEntity): Boolean {
        val label = remote.label
        return !label.isNullOrBlank() &&
            sessionDao.countLabelCollisions(remote.patientId, label, remote.sessionId) > 0
    }

    /**
     * Upserts [remote], proactively disambiguating its label if a collision is detected before
     * writing.
     *
     * **Why pre-check, not catch**: Room's `@Upsert` does not throw `SQLiteConstraintException`
     * for the new-row collision case. Internally it tries an INSERT; on a UNIQUE-constraint
     * failure it catches that and falls back to `UPDATE … WHERE session_id = ?` keyed on the
     * PRIMARY KEY. For a brand-new remote session (no matching `session_id` locally yet), that
     * UPDATE matches zero rows and is a silent no-op — the row is never written and no exception
     * surfaces. A try/catch on the upsert call would therefore be dead code for the common
     * cross-device duplicate scenario this method exists to handle.
     *
     * Instead: query `countLabelCollisions` before writing. If a collision exists, build the
     * disambiguated label (same suffix scheme: `"${label}-${sessionId.take(4)}".uppercase()`,
     * which can never itself collide because session IDs are globally unique) and upsert the
     * corrected copy. The try/catch is kept only as a backstop for the `local != null` update
     * path, where a genuine concurrent write could still produce a real constraint violation.
     */
    private suspend fun upsertSessionReconcilingLabel(remote: SessionEntity) {
        val rawLabel = remote.label
        val toWrite = if (!rawLabel.isNullOrBlank() && collidesLocally(remote)) {
            val disambiguated =
                "${rawLabel.trim()}-${remote.sessionId.take(DISAMBIGUATION_SUFFIX_LENGTH)}".uppercase()
            Log.w(
                TAG,
                "pullSessions: label collision for session ${remote.sessionId} " +
                    "(patient ${remote.patientId}); writing with disambiguated " +
                    "label \"$disambiguated\"",
            )
            remote.copy(label = disambiguated)
        } else {
            remote
        }

        try {
            sessionDao.upsertSession(toWrite)
        } catch (e: SQLiteConstraintException) {
            // Backstop only: the pre-check above handles the expected new-row collision case.
            // If we land here it is a genuine concurrent write race — log and rethrow so
            // the per-entity runCatching in invoke() records it as a sessions failure.
            Log.e(TAG, "pullSessions: unexpected constraint on upsert for ${remote.sessionId}", e)
            throw e
        }
    }

    /** Paginated pull of samples plus their detections and findings. Returns samples inserted. */
    private suspend fun pullSamples(userId: String): Int {
        var fetched = 0
        val seen = mutableSetOf<String>()
        forEachScopedPage(
            own = { offset, limit -> sampleRemoteDataSource.fetchOwnSamples(userId, offset, limit) },
            parentIds = sessionDao.getSessionIdsOnLinkedPatients(userId),
            underParents = { sessionIds, offset, limit ->
                sampleRemoteDataSource.fetchSamplesForSessions(sessionIds, offset, limit)
            },
        ) { page ->
            // Collect only the IDs whose parent row was actually written (E4 guard).
            // VERIFIED / SYNC_FAILED samples are skipped here AND their child rows must
            // not be overwritten — fetching detections/findings for them would silently
            // clobber the expert verdict / expertClass payload that hasn't uploaded yet.
            //
            // That guard is now the *only* thing standing between a skipped sample and its
            // children. It used to be doubled by accident: the parent write was a REPLACE, so
            // it deleted the row and cascaded the children away before re-inserting it, and
            // pullChildRowsFor then rebuilt them from the server. `upsertSample` updates in
            // place and touches no child row, which is the point of 86d4bx196 — so what the
            // children end up as is decided below, deliberately, per table.
            val writtenSampleIds = mutableListOf<String>()
            // A sample can come back twice, once as the user's own and once under its session.
            for (remote in page.filter { seen.add(it.sampleId) }) {
                if (!sessionDao.sessionExists(remote.sessionId)) {
                    Log.w(TAG, "pullSamples: session ${remote.sessionId} not on device; skipping ${remote.sampleId}")
                    continue
                }
                // E4 guard: skip if local row is VERIFIED or SYNC_FAILED (in-progress work)
                val local = sampleDao.getSampleByIdIncludingDeleted(remote.sampleId)
                if (local == null || local.status == SampleStatus.SYNCED.value) {
                    sampleDao.upsertSample(remote.withLocalImagePath(userId).withLocalPredictions(local))
                    fetched++
                    writtenSampleIds.add(remote.sampleId)
                }
            }

            pullChildRowsFor(writtenSampleIds)
        }
        return fetched
    }

    /**
     * Keeps a frame this device already holds attached to its row.
     *
     * `SampleRemoteDataSource` maps every pulled sample with `image_path = ""` — correctly, as
     * the server has no notion of this device's disk. Writing that straight through orphaned
     * the JPEG of every sample captured *here*: the row goes SYNCED the moment its push lands,
     * the E4 guard therefore lets the next pull overwrite it, and the file stayed on disk with
     * nothing pointing at it. The capturing device lost its own image after one sync pass and
     * fell back to needing a network for a frame already in its hands.
     *
     * Resolved from the store's derived path rather than from `local.image_path`, so a row
     * whose recorded path is stale — an eviction, a reinstall, a cleared cache — is corrected
     * rather than preserved. The disk is the authority on what the disk holds.
     */
    private suspend fun SampleEntity.withLocalImagePath(userId: String): SampleEntity {
        val cached = sampleImageStore.cachedPathOrNull(userId, sampleId) ?: return this
        return copy(imagePath = cached)
    }

    /**
     * Keeps the model output this device already holds attached to its row.
     *
     * The same fault [withLocalImagePath] fixes, on the column beside it. The server's sample row
     * has no `predictions_json`, so every pulled sample maps it to null, and the upsert wrote that
     * null over the capturing device's own copy the first pass after its push went through.
     * The frame's model output then survived only as whatever the detection rows could
     * reconstruct. The `predictions` rows pulled in [pullChildRowsFor] replace it when the server
     * holds a whole set; until then, the device's copy is the better one and is kept.
     */
    private fun SampleEntity.withLocalPredictions(local: SampleEntity?): SampleEntity =
        if (predictionsJson == null && local?.predictionsJson != null) {
            copy(predictionsJson = local.predictionsJson)
        } else {
            this
        }

    /**
     * Fetch the predictions, detections and findings of the samples whose parent row was
     * actually written,
     * and reconcile them against what is already on the device.
     *
     * Chunked to stay well under server/proxy URL-length limits (E5), and the same chunks are
     * reused for the local writes so the SQLite host-parameter bound is respected too.
     *
     * **The two tables reconcile differently, and that asymmetry is the whole point.** Until
     * 86d4bx196 the parent write was an `@Insert(REPLACE)`, which deleted the sample row and
     * cascaded both child tables away before re-inserting it, so this function always ran
     * against an empty slate and "merge" and "replace" were the same thing. Under `@Upsert`
     * they are not, so each table gets the rule its own writer already uses:
     *
     * - **Predictions fill `predictions_json`.** Room has no table for them; they are folded
     *   back into the column capture writes, so every reader of it is unchanged.
     * - **Detections merge.** They are the retraining corpus C8 protects, and the push side
     *   (`SampleRemoteDataSource.syncSample`) upserts them and never deletes, so the server's
     *   set is a superset of anything this device pushed — a merge keyed on the derived
     *   detection id lands on exactly the rows a REPLACE-and-refetch used to produce, without
     *   ever deleting one. Nothing here is allowed to remove a detection.
     * - **Findings are replaced wholesale**, per sample, which is what both other writers of
     *   this table already do (`SampleSpeciesFindingDao.replaceFindingsForSample` locally, a
     *   delete-then-insert remotely). A per-species count is a *current statement*, not a
     *   record: a species removed on another device has to actually disappear here, and a
     *   merge would leave the stale row behind to inflate the count. This is the one place the
     *   naive swap would have changed behaviour, and it is not a C8 deletion — the detections
     *   and the Storage object C8 covers are untouched.
     *
     * The replace is keyed on [writtenSampleIds] rather than on the ids present in the fetched
     * findings, so a sample the server holds no findings for — a clean field — has its local
     * rows cleared rather than left behind.
     */
    private suspend fun pullChildRowsFor(writtenSampleIds: List<String>) {
        if (writtenSampleIds.isEmpty()) return
        val chunks = writtenSampleIds.chunked(CHILD_BATCH_SIZE)

        // Predictions first, so a failure here stops the pass before any detection lands. A
        // sample with detections but no model output reopens by rebuilding predictions from the
        // detection rows, and a BOX_INCORRECT row with no box is a gap that rebuild cannot fill;
        // a sample with neither simply waits for the next pass.
        //
        // Written only when the server holds a whole set - see toFramePredictionsOrNull for why a
        // partial one is worse than none - and otherwise the device's own copy is left alone.
        val predictions = chunks.flatMap { chunk -> sampleRemoteDataSource.fetchPredictions(chunk) }
        predictions.groupBy { it.sampleId }.forEach { (sampleId, rows) ->
            val json = rows.toFramePredictionsOrNull()?.let { gson.encodePredictions(it) }
            if (json != null) {
                sampleDao.updatePredictionsJson(sampleId, json)
            }
        }

        val detections = chunks.flatMap { chunk -> sampleRemoteDataSource.fetchDetections(chunk) }
        if (detections.isNotEmpty()) {
            detectionDao.insertDetections(detections)
        }

        val findings = chunks.flatMap { chunk -> sampleRemoteDataSource.fetchFindings(chunk) }
        val findingsBySample = findings.groupBy { it.sampleId }
        chunks.forEach { chunk ->
            sampleSpeciesFindingDao.replaceFindingsForSamples(
                sampleIds = chunk,
                findings = chunk.flatMap { sampleId -> findingsBySample[sampleId].orEmpty() },
            )
        }
    }

    /** Reports are FK children of sessions — pull them last. Returns rows inserted. */
    private suspend fun pullReports(userId: String): Int {
        var fetched = 0
        val seen = mutableSetOf<String>()
        forEachScopedPage(
            own = { offset, limit -> reportRemoteDataSource.fetchOwnReports(userId, offset, limit) },
            parentIds = sessionDao.getSessionIdsOnLinkedPatients(userId),
            underParents = { sessionIds, offset, limit ->
                reportRemoteDataSource.fetchReportsForSessions(sessionIds, offset, limit)
            },
        ) { page ->
            for (remote in page.filter { seen.add(it.reportId) }) {
                if (upsertReportIfEligible(remote)) fetched++
            }
        }
        return fetched
    }

    /**
     * Colleagues' names, so a read-only record can say whose it is offline (14zcqntjph6).
     *
     * Asked for by id: the authors of the sessions and reports this device holds for the user's
     * own patients, which is the set `0008_colleague_names.sql` lets a medtech read. Unscoped,
     * the same table names everyone in an org admin's laboratory (14zcqntjt3p).
     */
    private suspend fun pullColleagues(userId: String) {
        val colleagueIds = colleagueDao.getColleagueIdsOnLinkedPatients(userId)
        if (colleagueIds.isEmpty()) return
        val colleagues = colleagueIds.chunked(CHILD_BATCH_SIZE)
            .flatMap { chunk -> profileRemoteDataSource.fetchColleagues(chunk) }
        colleagueDao.upsertColleagues(colleagues)
    }

    /** The patients the user is linked to on this device: the parents sessions are pulled under. */
    private suspend fun linkedPatientIds(userId: String): List<String> =
        patientDao.getLinksForUser(userId).map { it.patientId }

    /** Hands every page of a paged fetch to [onPage], stopping after the first short page. */
    private suspend fun <T> forEachPage(
        fetch: suspend (offset: Long, limit: Long) -> List<T>,
        onPage: suspend (List<T>) -> Unit,
    ) {
        var offset = 0L
        while (true) {
            val page = fetch(offset, PAGE_SIZE.toLong())
            onPage(page)
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE.toLong()
        }
    }

    /**
     * The pages of the user's [own] rows, then of the rows [underParents] each chunk of
     * [parentIds]: the two halves of what a medtech may read (14zcqntjt3p). A row in both
     * halves arrives twice, so callers skip ids they have already handled.
     */
    private suspend fun <T> forEachScopedPage(
        own: suspend (offset: Long, limit: Long) -> List<T>,
        parentIds: List<String>,
        underParents: suspend (parentIds: List<String>, offset: Long, limit: Long) -> List<T>,
        onPage: suspend (List<T>) -> Unit,
    ) {
        forEachPage(own, onPage)
        parentIds.chunked(CHILD_BATCH_SIZE).forEach { chunk ->
            forEachPage({ offset, limit -> underParents(chunk, offset, limit) }, onPage)
        }
    }

    /**
     * Writes one pulled report row, applying the same-report_type and E4 guards, and returns
     * whether it was written.
     *
     * A patient report whose patient row is not yet on this device (a pull that has not reached
     * that patient, or one this account cannot see) fails its FK. That is one bad row, not a
     * reason to drop every report after it in the page — caught here, same as
     * [upsertSessionReconcilingLabel]'s backstop, and it is retried on the next pull once its
     * patient has landed.
     */
    private suspend fun upsertReportIfEligible(remote: ReportEntity): Boolean {
        // A session report on a session this device does not hold (14zcqntjph5) is skipped: the
        // pull brings colleagues' reports down, but not necessarily their sessions.
        val sessionMissing = remote.sessionId != null && !sessionDao.sessionExists(remote.sessionId)
        if (sessionMissing) {
            Log.w(TAG, "pullReports: session ${remote.sessionId} not on device; skipping ${remote.reportId}")
        }
        // E4 guard: skip if local row is pending or sync_failed
        val local = reportDao.getReportById(remote.reportId)
        // An unrecognised report_type is a server-side type this build doesn't know how to
        // render yet — skip it rather than store and later crash decoding it, or silently
        // mislabel it as a session report.
        val eligible = !sessionMissing && ReportType.fromValueOrNull(remote.reportType) != null &&
            (local == null || local.supabaseStatus == ReportSyncStatus.SYNCED.value)

        return eligible && try {
            reportDao.insertReport(local?.let { remote.withLocalFilePaths(it) } ?: remote)
            true
        } catch (e: SQLiteConstraintException) {
            Log.w(TAG, "pullReports: skipping report ${remote.reportId}, FK not satisfied", e)
            false
        }
    }

    /**
     * Keeps the file paths this device already recorded for a report it holds.
     *
     * `pdf_file_path` and `csv_file_path` are device-local — a MediaStore id or an absolute path —
     * and the server's copy is whatever the *generating* device wrote. Writing it straight through
     * undid every restore: `RestoreReportFilesUseCase` repoints the row at the file it just
     * downloaded, the next pull put the generating device's id back, and the report reopened to a
     * file that is not here — prompting another download, and another copy nothing deletes (C8).
     *
     * Unlike [withLocalImagePath] there is no derived path to ask the disk for: a MediaStore id is
     * assigned on write. The local row is the only record of where this device put the file. A
     * format the local row has no path for falls back to the remote value, which is what a first
     * pull would have written anyway.
     */
    private fun ReportEntity.withLocalFilePaths(local: ReportEntity): ReportEntity = copy(
        pdfFilePath = local.pdfFilePath ?: pdfFilePath,
        csvFilePath = local.csvFilePath ?: csvFilePath,
    )

    private companion object {
        const val TAG = "FetchRemoteDataUseCase"
        const val PAGE_SIZE = 500

        /**
         * Maximum number of UUIDs per `isIn` request: patients, sessions, samples, colleagues.
         * Keeps the GET query string well under server/proxy URL-length limits.
         */
        const val CHILD_BATCH_SIZE = 100

        /** Chars of a session's own id used to disambiguate a colliding label on pull. */
        const val DISAMBIGUATION_SUFFIX_LENGTH = 4
    }
}
