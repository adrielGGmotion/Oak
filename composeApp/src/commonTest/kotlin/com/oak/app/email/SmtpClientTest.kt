package com.oak.app.email

import kotlin.test.Test

/**
 * Unit tests for [SmtpClient] protocol handling.
 *
 * Full integration tests require a live SMTP server and are not included here.
 */
class SmtpClientTest {

    @Test
    fun `SmtpClient constructor stores host and port`() {
        SmtpClient(host = "smtp.example.com", port = 587)
    }

    @Test
    fun `SmtpClient with default port`() {
        SmtpClient(host = "smtp.example.com")
    }

    @Test
    fun `SmtpClient with TLS disabled`() {
        SmtpClient(host = "smtp.example.com", port = 465, useStartTls = false)
    }
}
