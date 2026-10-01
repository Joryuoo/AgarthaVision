package com.agarthavision.ui.sessions

/** A session card's meta line: date and time, then whose it is when it is a colleague's. */
internal fun sessionMeta(date: String, time: String, recordedBy: String?): String =
    if (recordedBy == null) "$date · $time" else "$date · $time · $recordedBy"

/**
 * A colleague's row opens Session Detail to read; the medtech's own row resumes Capture.
 * A colleague's session is read-only (14zcqntjph6), so capturing into it would add to someone
 * else's record.
 */
internal fun sessionRowClick(isColleagueSession: Boolean, openDetail: () -> Unit, resume: () -> Unit): () -> Unit =
    if (isColleagueSession) openDetail else resume
