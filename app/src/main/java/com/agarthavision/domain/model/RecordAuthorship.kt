package com.agarthavision.domain.model

/**
 * Whether a record was written by someone other than the medtech looking at it.
 *
 * Since `0007_patient_shared_history.sql` a medtech holds colleagues' sessions, samples and
 * reports for every patient they are assigned to. The server lets only the author change a row
 * (`samples_update_own`, `detections_update_via_sample`, `findings_update_via_sample`,
 * `sessions_update_own`), so an edit to a colleague's record would be refused on push and the
 * phone and the server would silently disagree. **Such a record is read-only on the phone**
 * (14zcqntjph6), and this is the one definition of "such a record".
 *
 * An unowned row ([authorId] null) predates mandatory login and belongs to whoever holds the
 * device, so it stays editable. With no cached identity ([viewerId] null) there is nobody to
 * compare against; mandatory login means that state holds no shared records.
 */
fun isColleagueRecord(authorId: String?, viewerId: String?): Boolean =
    authorId != null && viewerId != null && authorId != viewerId

/**
 * Thrown when a write is attempted on a colleague's record.
 *
 * The UI hides every such action first; this is the backstop for any path that reaches the
 * write anyway, so the change is refused here rather than failing silently on push.
 */
class ReadOnlyRecordException(recordId: String) :
    IllegalStateException("$recordId was recorded by a colleague and is read-only on this phone.")

/** Who wrote a record, as the medtech looking at it should see it. */
sealed interface RecordAuthor {
    /** The viewer wrote it, or nobody owns it. Editable. */
    data object Viewer : RecordAuthor

    /**
     * A colleague wrote it. Read-only, and the screen says whose it is.
     *
     * [name] is null when the colleague's profile carries none, or the name has not been pulled
     * yet; the screen then says "another medtech" rather than showing nothing.
     */
    data class Colleague(val name: String?) : RecordAuthor
}

/** True for a colleague's record: every editing action on it is hidden (14zcqntjph6). */
val RecordAuthor.isReadOnly: Boolean get() = this is RecordAuthor.Colleague
