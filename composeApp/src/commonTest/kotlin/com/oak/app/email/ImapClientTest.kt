package com.oak.app.email

import kotlin.test.Test

/**
 * Unit tests for [ImapClient] protocol handling.
 *
 * Full integration tests require a live IMAP server and are not included here.
 */
class ImapClientTest {

    @Test
    fun `ImapClient constructor stores host and port`() {
        ImapClient(host = "imap.example.com", port = 993)
    }

    @Test
    fun `ImapClient with default TLS`() {
        ImapClient(host = "imap.example.com")
    }

    @Test
    fun `ImapClient without TLS`() {
        ImapClient(host = "imap.example.com", port = 143, tls = false)
    }
}
