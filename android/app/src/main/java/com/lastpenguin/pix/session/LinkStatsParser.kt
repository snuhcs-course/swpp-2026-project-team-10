// AI-generated with Claude Code, 2026-10-25, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport

/**
 * Reads a [LinkStats] out of WebRTC's statistics report: the video RTP stream, its codec, and the selected candidate
 * pair with its two candidates; the bitrate comes from the byte delta since the previous report, so the first report
 * has none. Split out of [WebRtcPeerConnectionClient] because the report objects are plain Java, so this part runs in
 * JVM tests.
 * Owner: Real-time (#8).
 */
internal class LinkStatsParser {
    private var lastBytes: Long? = null
    private var lastStatsTimestampUs = 0.0

    /** null when the report carries no video stream. */
    fun parse(report: RTCStatsReport): LinkStats? {
        val all = report.statsMap.values
        // A transceiver reports both directions; the one carrying bytes is the live video stream.
        val rtp =
            all.filter { (it.type == "outbound-rtp" || it.type == "inbound-rtp") && it.members["kind"] == "video" }
                .maxByOrNull { it.bytes() ?: -1L } ?: return null
        val members = rtp.members
        val bytes = rtp.bytes()
        val codec = (members["codecId"] as? String)?.let { report.statsMap[it]?.members?.get("mimeType") as? String }
        val pair = selectedPair(report)
        val roundTripMs = (pair?.members?.get("currentRoundTripTime") as? Number)?.toDouble()?.times(1000)
        val now: Double = rtp.timestampUs
        val previousBytes = lastBytes
        val bitrateKbps = if (bytes != null && previousBytes != null && now > lastStatsTimestampUs) {
            (bytes - previousBytes) * 8.0 / ((now - lastStatsTimestampUs) / 1_000_000.0) / 1000.0
        } else {
            null
        }
        lastBytes = bytes
        lastStatsTimestampUs = now
        return LinkStats(
            codec = codec,
            width = (members["frameWidth"] as? Number)?.toInt(),
            height = (members["frameHeight"] as? Number)?.toInt(),
            framesPerSecond = (members["framesPerSecond"] as? Number)?.toDouble(),
            bitrateKbps = bitrateKbps,
            roundTripMs = roundTripMs,
            path = pair?.let { path(report, it) },
        )
    }

    /** The pair the transport selected; without that entry, a nominated pair, or else one that succeeded. */
    private fun selectedPair(report: RTCStatsReport): RTCStats? {
        val all = report.statsMap.values
        val selectedId = all.firstOrNull { it.type == "transport" }?.members?.get("selectedCandidatePairId") as? String
        return selectedId?.let { report.statsMap[it] }
            ?: all.firstOrNull { it.type == "candidate-pair" && it.members["nominated"] == true }
            ?: all.firstOrNull { it.type == "candidate-pair" && it.members["state"] == "succeeded" }
    }

    private fun path(report: RTCStatsReport, pair: RTCStats): IcePath? {
        val local = (pair.members["localCandidateId"] as? String)?.let { report.statsMap[it] } ?: return null
        val remote = (pair.members["remoteCandidateId"] as? String)?.let { report.statsMap[it] } ?: return null
        return IcePath(
            local = local.members["candidateType"] as? String ?: return null,
            remote = remote.members["candidateType"] as? String ?: return null,
            network = local.members["networkType"] as? String,
            relayProtocol = local.members["relayProtocol"] as? String,
        )
    }

    private fun RTCStats.bytes(): Long? = ((members["bytesSent"] ?: members["bytesReceived"]) as? Number)?.toLong()
}
