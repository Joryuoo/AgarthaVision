package com.agarthavision.domain.usecase.patients

import com.agarthavision.core.util.escapeLike
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PsgcRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** Sort order for the patient list. */
enum class PatientSort {
    RECENT,
    LAST_NAME,
    FIRST_NAME,
}

/** What the list asks for: search filter, sort, category filters, and a page size. */
data class PatientsQuery(
    val query: String = "",
    val sort: PatientSort = PatientSort.RECENT,
    val sex: Sex? = null,
    val barangayCode: String? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val limit: Int = PAGE_SIZE,
) {
    companion object {
        const val PAGE_SIZE = 20
    }
}

/**
 * One row of the patient list: the patient, and the barangay name its stored code
 * resolves to.
 *
 * [barangayName] is null when the code matches no row — possible after a PSGC vintage
 * change — and the row renders the raw code rather than a blank.
 */
data class PatientListItem(
    val patient: Patient,
    val barangayName: String?,
)

/**
 * A page of patients plus the total matching the same filter.
 *
 * The total is what tells the list whether another page exists — it is not a page count.
 * There is no pager: the list is infinite-scroll and grows its limit.
 */
data class PatientsResult(
    val items: List<PatientListItem> = emptyList(),
    val total: Int = 0,
)

/**
 * Observes the signed-in medtech's patients, with their barangay labels resolved.
 *
 * Filtering is not done here. `PatientDao.observePatients` already filters on lastname
 * and firstname in SQL. This use case supplies the query and turns each stored PSGC code
 * into something the row can display.
 *
 * A null [userId] yields nothing rather than everything. Login is mandatory on first run
 * so it should not arise, but defaulting to "show all patients on the device" if it ever
 * did would be the wrong direction to fail in.
 */
class ObservePatientsUseCase @Inject constructor(
    private val patientRepository: PatientRepository,
    private val psgcRepository: PsgcRepository,
) {
    operator fun invoke(userId: String?, query: PatientsQuery): Flow<PatientsResult> {
        if (userId == null) return flowOf(PatientsResult())

        // Escaped here, once, for both the page and the count — they run the same predicate
        // and must not disagree. Without it a surname containing `_` is a wildcard and a
        // query of `%` matches every patient on the device.
        val needle = escapeLike(query.query)
        val page = patientRepository.observePatients(
            userId = userId,
            query = needle,
            limit = query.limit,
            sort = query.sort,
            sex = query.sex,
            barangayCode = query.barangayCode,
            minBirthdate = null,
            maxBirthdate = null,
        )
        val total = patientRepository.observePatientCount(
            userId = userId,
            query = needle,
            sex = query.sex,
            barangayCode = query.barangayCode,
            minBirthdate = null,
            maxBirthdate = null,
        )

        return combine(page, total) { patients, count ->
            PatientsResult(items = patients.mapToItems(), total = count)
        }
    }

    /**
     * Resolves barangay names for one page.
     *
     * Memoised across the page because patients cluster by barangay — a barangay-filtered
     * search is usually one code repeated down the whole list, and looking it up once per
     * row would be that many primary-key reads for one answer. The cache is per emission
     * and deliberately not held: the reference table can be re-seeded under a new PSGC
     * vintage, and a stale name is worse than a second lookup.
     */
    private suspend fun List<Patient>.mapToItems(): List<PatientListItem> {
        val names = mutableMapOf<String, String?>()
        return map { patient ->
            val name = names.getOrPut(patient.psgcBarangayCode) {
                psgcRepository.getBarangay(patient.psgcBarangayCode)?.name
            }
            PatientListItem(patient = patient, barangayName = name)
        }
    }
}
