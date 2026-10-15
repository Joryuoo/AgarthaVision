package com.agarthavision.domain.sync

/**
 * The reason the database gives when it refuses a write from an account that is no longer an
 * active member of an active laboratory: deactivated by an org admin, its laboratory
 * deactivated, or never added to one (`supabase/migrations/0018_writes_need_active_lab_member.sql`).
 *
 * Matched as text in the error's message so the check holds whichever client library carried
 * the error (D7).
 */
const val INACTIVE_MEMBER_REFUSAL = "not_active_lab_member"

private const val MAX_CAUSE_DEPTH = 8

/** True when this failure, or a cause behind it, is the database's [INACTIVE_MEMBER_REFUSAL]. */
fun Throwable.isInactiveMemberRefusal(): Boolean =
    generateSequence(this) { it.cause?.takeIf { cause -> cause !== it } }
        .take(MAX_CAUSE_DEPTH)
        .any { it.message?.contains(INACTIVE_MEMBER_REFUSAL) == true }
