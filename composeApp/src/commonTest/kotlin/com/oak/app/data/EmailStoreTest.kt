package com.oak.app.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmailStoreTest {

    private fun freshStore(): EmailStore = EmailStore(AppSettings(MapSettings()))

    @Test
    fun `getAccounts returns empty initially`() {
        val store = freshStore()
        assertTrue(store.getAccounts().isEmpty())
    }

    @Test
    fun `addAccount stores and retrieves account`() = runTest {
        val store = freshStore()
        val account = EmailAccount(
            id = "test-1",
            email = "test@example.com",
            imapHost = "imap.example.com",
            smtpHost = "smtp.example.com",
        )
        store.addAccount(account)

        val accounts = store.getAccounts()
        assertEquals(1, accounts.size)
        assertEquals("test@example.com", accounts[0].email)
    }

    @Test
    fun `addAccount replaces existing account with same id`() = runTest {
        val store = freshStore()
        val account1 = EmailAccount(
            id = "test-1", email = "old@example.com",
            imapHost = "imap.a.com", smtpHost = "smtp.a.com",
        )
        val account2 = EmailAccount(
            id = "test-1", email = "new@example.com",
            imapHost = "imap.b.com", smtpHost = "smtp.b.com",
        )
        store.addAccount(account1)
        store.addAccount(account2)

        assertEquals(1, store.getAccounts().size)
        assertEquals("new@example.com", store.getAccounts()[0].email)
    }

    @Test
    fun `getAccount returns specific account by id`() = runTest {
        val store = freshStore()
        val account = EmailAccount(
            id = "test-1", email = "test@example.com",
            imapHost = "imap.example.com", smtpHost = "smtp.example.com",
        )
        store.addAccount(account)

        val found = store.getAccount("test-1")
        assertNotNull(found)
        assertEquals("test@example.com", found.email)

        assertNull(store.getAccount("nonexistent"))
    }

    @Test
    fun `removeAccount removes account and its password`() = runTest {
        val store = freshStore()
        val account = EmailAccount(
            id = "test-1", email = "test@example.com",
            imapHost = "imap.example.com", smtpHost = "smtp.example.com",
        )
        store.addAccount(account)
        store.setPassword("test-1", "secret")

        val removed = store.removeAccount("test-1")
        assertTrue(removed)
        assertTrue(store.getAccounts().isEmpty())
        assertEquals("", store.getPassword("test-1"))
    }

    @Test
    fun `password management works correctly`() = runTest {
        val store = freshStore()
        store.setPassword("acc-1", "p@ssword")
        assertEquals("p@ssword", store.getPassword("acc-1"))
    }

    @Test
    fun `sync state defaults to empty state for unknown account`() {
        val store = freshStore()
        val state = store.getSyncState("acc-1")
        assertEquals("acc-1", state.accountId)
        assertEquals(0L, state.lastSeenUid)
        assertEquals(0, state.unreadCount)
    }

    @Test
    fun `updateSyncState persists the state`() = runTest {
        val store = freshStore()
        val state = EmailSyncState(accountId = "acc-1", lastSeenUid = 100, unreadCount = 5)
        store.updateSyncState(state)

        val loaded = store.getSyncState("acc-1")
        assertEquals(100, loaded.lastSeenUid)
        assertEquals(5, loaded.unreadCount)
    }

    @Test
    fun `getAllSyncStates returns states for all accounts`() = runTest {
        val store = freshStore()
        val account = EmailAccount(
            id = "acc-1", email = "test@example.com",
            imapHost = "imap.example.com", smtpHost = "smtp.example.com",
        )
        store.addAccount(account)
        store.updateSyncState(EmailSyncState(accountId = "acc-1", unreadCount = 3))

        val states = store.getAllSyncStates()
        assertEquals(1, states.size)
        assertEquals(3, states["acc-1"]?.unreadCount)
    }

    @Test
    fun `pending emails FIFO queue with max cap`() = runTest {
        val store = freshStore()
        val emails = (1..105).map { i ->
            EmailMessage(
                uid = i.toLong(), accountId = "acc-1",
                from = "sender$i@example.com", subject = "Email $i",
            )
        }
        store.addPending(emails)

        val pending = store.getPending()
        // MAX_PENDING = 100, so only the last 100 are kept
        assertEquals(100, pending.size)
        assertEquals(6, pending[0].uid) // first was trimmed
    }

    @Test
    fun `removePending removes specified emails`() = runTest {
        val store = freshStore()
        val email1 = EmailMessage(
            uid = 1L, accountId = "acc-1",
            from = "a@example.com", subject = "First",
        )
        val email2 = EmailMessage(
            uid = 2L, accountId = "acc-1",
            from = "b@example.com", subject = "Second",
        )
        store.addPending(listOf(email1, email2))
        assertEquals(2, store.getPending().size)

        store.removePending(listOf(email1))
        assertEquals(1, store.getPending().size)
        assertEquals(2L, store.getPending()[0].uid)
    }
}
