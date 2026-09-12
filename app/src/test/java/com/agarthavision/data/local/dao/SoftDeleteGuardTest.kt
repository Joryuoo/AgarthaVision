package com.agarthavision.data.local.dao

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Enforces the rule a tombstone creates, so that forgetting it is not possible to ship.
 *
 * > Any DAO method that SELECTs over `samples` must contain `deleted_at is null`, **unless its
 * > name ends in `IncludingDeleted`**.
 *
 * A verified sample is never hard-deleted (C8) — it is tombstoned, and every query that lists
 * or counts samples has to exclude it. Miss one and a deleted duplicate quietly reappears in a
 * report, which is the worst shape a defect can take here: a wrong clinical number with no
 * error anywhere. There are a dozen such queries across three DAOs and more will be written, so
 * the rule is checked rather than remembered.
 *
 * Exactly two methods are exempt, and the suffix announces that at every call site rather than
 * hiding it in an allow-list here that would rot:
 * - `getSampleByIdIncludingDeleted` — the delete path must read a row in order to tombstone it.
 * - `getSamplesPendingSyncIncludingDeleted` — pushing the tombstone is precisely its job. This
 *   is the one that matters most: filter it and tombstones never reach Supabase at all.
 *
 * **Reads source text, not annotations.** `androidx.room.Query` has CLASS retention, so it is
 * not visible to runtime reflection; a reflective version of this test would pass while
 * asserting nothing.
 */
class SoftDeleteGuardTest {

    private val daoDir = File("src/main/java/com/agarthavision/data/local/dao")

    private val daoFiles = listOf(
        "SampleDao.kt",
        "DetectionDao.kt",
        "SessionDao.kt",
    ).map { File(daoDir, it) }

    @Test
    fun `the DAO sources are where this test expects them`() {
        // Guards the guard: unit tests run with the module directory as the working directory,
        // and a moved file would otherwise turn every assertion below into a silent pass.
        daoFiles.forEach { file ->
            assertTrue("Missing ${file.path} — has the DAO package moved?", file.isFile)
        }
    }

    @Test
    fun `every query selecting over samples excludes tombstoned rows`() {
        val offenders = mutableListOf<String>()

        daoFiles.forEach { file ->
            queriesIn(file.readText()).forEach { (name, sql) ->
                val selectsSamples = sql.contains("select", ignoreCase = true) &&
                    Regex("""\bfrom\s+samples\b""", RegexOption.IGNORE_CASE).containsMatchIn(sql)
                if (!selectsSamples) return@forEach
                if (name.endsWith("IncludingDeleted")) return@forEach
                if (!sql.contains("deleted_at is null", ignoreCase = true)) {
                    offenders += "${file.name}: $name"
                }
            }
        }

        if (offenders.isNotEmpty()) {
            fail(
                "These queries select over `samples` without excluding tombstoned rows, so a " +
                    "deleted sample would reappear in whatever they feed:\n" +
                    offenders.joinToString("\n") { "  - $it" } +
                    "\n\nAdd `AND deleted_at is null`, or rename the method to end in " +
                    "`IncludingDeleted` if seeing tombstones is genuinely the point.",
            )
        }
    }

    @Test
    fun `only the two known methods claim the exemption`() {
        // A third one appearing is not necessarily wrong, but it is a decision that deserves a
        // reviewer rather than a rename.
        val exempt = daoFiles
            .flatMap { file -> queriesIn(file.readText()).map { it.first } }
            .filter { it.endsWith("IncludingDeleted") }
            .sorted()

        assertTrue(
            "Unexpected soft-delete exemptions: $exempt",
            exempt == listOf(
                "getSampleByIdIncludingDeleted",
                "getSamplesPendingSyncIncludingDeleted",
            ),
        )
    }

    /**
     * Pairs each `@Query(...)` block with the function name that follows it.
     *
     * Walks to the balanced closing paren rather than regex-matching the argument, because
     * this file mixes triple-quoted SQL, single-line strings and `+`-concatenated strings, and
     * a pattern that understood only one of those would skip the others **silently** — passing
     * while asserting nothing, which is the failure mode this whole test exists to prevent.
     */
    private fun queriesIn(source: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        var index = source.indexOf(ANNOTATION)
        while (index >= 0) {
            val open = index + ANNOTATION.length - 1
            val close = matchingParen(source, open)
            if (close < 0) break
            val name = FUN_NAME.find(source, close)?.groupValues?.get(1)
            if (name != null) {
                results += name to source.substring(open + 1, close)
            }
            index = source.indexOf(ANNOTATION, close)
        }
        return results
    }

    private fun matchingParen(source: String, openIndex: Int): Int {
        var depth = 0
        var i = openIndex
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private companion object {
        private const val ANNOTATION = "@Query("
        private val FUN_NAME = Regex("""fun\s+(\w+)""")
    }
}
