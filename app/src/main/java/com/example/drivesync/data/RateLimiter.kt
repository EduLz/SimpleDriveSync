package com.example.drivesync.data

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Rate limiter with ultra-conservative delays to avoid Google Drive API bans.
 *
 * Same strategy as the PC Python app:
 * - 2-4s between API listing calls
 * - 15-25s between file downloads
 * - Max 10 requests/minute
 * - Exponential backoff: 30s → 1800s (30 min)
 * - Auto-cooldown after 3 consecutive 403 errors
 */
class RateLimiter(
    private val maxRequestsPerMinute: Int = 1000,
    private val apiDelayMinMs: Long = 0,
    private val apiDelayMaxMs: Long = 0,
    private val downloadDelayMinMs: Long = 0,
    private val downloadDelayMaxMs: Long = 0,
    private val jitterMaxMs: Long = 0,
    private val backoffBaseMs: Long = 1000,
    private val backoffMaxMs: Long = 1800000, // 30 minutes
    val maxRetries: Int = 6,
    private val cooldownAfterNErrors: Int = 3,
    val cooldownDurationMs: Long = 1800000, // 30 minutes
) {
    private val requestTimestamps = mutableListOf<Long>()
    private var consecutive403s = 0
    var totalRequests = 0; private set
    var totalCooldowns = 0; private set

    /**
     * Wait before an API listing call. Returns the delay in seconds for UI display.
     */
    suspend fun waitForApiCall(): Int {
        enforceRateLimit()
        val delay = Random.nextLong(apiDelayMinMs, apiDelayMaxMs)
        val jitter = Random.nextLong(0, (jitterMaxMs * 0.3).toLong())
        val total = delay + jitter
        delay(total)
        totalRequests++
        return (total / 1000).toInt()
    }

    /**
     * Wait before downloading a file. Returns the delay in seconds for UI display.
     */
    suspend fun waitForDownload(): Int {
        enforceRateLimit()
        val delay = Random.nextLong(downloadDelayMinMs, downloadDelayMaxMs)
        val jitter = Random.nextLong(0, jitterMaxMs)
        val total = delay + jitter
        delay(total)
        totalRequests++
        return (total / 1000).toInt()
    }

    /**
     * Calculate exponential backoff delay for a given attempt.
     * @return delay in ms, or null if max retries exceeded.
     */
    fun getBackoffDelay(attempt: Int): Long? {
        if (attempt >= maxRetries) return null
        val delay = minOf(backoffBaseMs * (1L shl attempt), backoffMaxMs)
        val jitter = Random.nextLong(0, (delay * 0.3).toLong())
        return delay + jitter
    }

    /**
     * Report a 403 error. Returns true if cooldown was triggered.
     */
    fun report403(): Boolean {
        consecutive403s++
        if (consecutive403s >= cooldownAfterNErrors) {
            totalCooldowns++
            consecutive403s = 0
            return true // Caller should do cooldown
        }
        return false
    }

    /** Report a successful request. Resets 403 counter. */
    fun reportSuccess() {
        consecutive403s = 0
    }

    private suspend fun enforceRateLimit() {
        val now = System.currentTimeMillis()
        requestTimestamps.removeAll { now - it > 60000 }

        if (requestTimestamps.size >= maxRequestsPerMinute) {
            val oldest = requestTimestamps.first()
            val waitTime = 60000L - (now - oldest) + Random.nextLong(2000, 5000)
            if (waitTime > 0) {
                delay(waitTime)
            }
        }
        requestTimestamps.add(System.currentTimeMillis())
    }
}
