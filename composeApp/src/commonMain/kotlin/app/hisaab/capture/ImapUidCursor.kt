package app.hisaab.capture

/**
 * Email history paging by IMAP UID, packed with the folder's UIDVALIDITY into the single Long cursor the
 * email source persists. UIDs only grow within a folder, so unlike a received-date cursor this needs no
 * arrival-order assumption and can't stall on a page of same-day messages.
 */
object ImapUidCursor {
    private const val UID_MASK = 0xFFFFFFFFL

    fun pack(uidValidity: Long, uid: Long): Long = (uidValidity shl 32) or (uid and UID_MASK)

    /**
     * Matched UIDs not yet read, oldest first. A changed UIDVALIDITY (mailbox rebuilt) means the stored UID
     * is meaningless, so it restarts from the top — the pipeline's dedup absorbs anything seen before.
     */
    fun pending(matchedUids: List<Long>, cursor: Long, uidValidity: Long): List<Long> {
        val after = if (cursor ushr 32 == uidValidity) cursor and UID_MASK else 0L
        return matchedUids.filter { it > after }.sorted()
    }
}
