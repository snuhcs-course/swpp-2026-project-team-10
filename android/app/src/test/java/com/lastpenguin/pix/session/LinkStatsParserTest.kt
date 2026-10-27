// AI-generated with Claude Code, 2026-10-25, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport

/*
 * Partitions for the statistics reader (NFR-5, 6, 12; Design 2.8): the video stream outbound (photographer) or
 * inbound (subject), and the one carrying bytes when a transceiver reports both; the codec entry present or missing;
 * the candidate pair nominated, only succeeded, or absent; the first report (no bitrate) and the next (bytes over the
 * time between them), a report at the same instant; a report without a video stream.
 * The path (Design 2.6.7, NFR-10): the transport's selected pair, preferred over another nominated pair, or missing
 * from the report (the nominated pair instead); a relayed path with its relay protocol and network, a direct one
 * without them; a candidate entry or its type missing (no path).
 */
class LinkStatsParserTest {
    private val parser = LinkStatsParser()

    private fun stats(id: String, type: String, vararg members: Pair<String, Any>) =
        RTCStats(0L, type, id, mapOf(*members))

    private fun report(timestampUs: Long, vararg entries: RTCStats): RTCStatsReport =
        RTCStatsReport(timestampUs, entries.associateBy { it.id })

    private fun outbound(bytes: Long, timestampUs: Long, codecId: String? = "C1") = RTCStats(
        timestampUs,
        "outbound-rtp",
        "OUT",
        listOfNotNull(
            "kind" to "video",
            "bytesSent" to bytes,
            "frameWidth" to 960,
            "frameHeight" to 720,
            "framesPerSecond" to 29.5,
            codecId?.let { "codecId" to it },
        ).toMap(),
    )

    private val codec = stats("C1", "codec", "mimeType" to "video/H264")
    private val nominated = stats("P1", "candidate-pair", "nominated" to true, "currentRoundTripTime" to 0.042)

    @Test
    fun firstReport_hasCodecSizeFpsAndRoundTrip_butNoBitrate() {
        val stats = parser.parse(report(1_000_000, outbound(10_000, 1_000_000), codec, nominated))!!

        assertEquals("video/H264", stats.codec)
        assertEquals(960, stats.width)
        assertEquals(720, stats.height)
        assertEquals(29.5, stats.framesPerSecond!!, 0.0)
        assertEquals(42.0, stats.roundTripMs!!, 1e-9)
        assertNull(stats.bitrateKbps)
    }

    @Test
    fun secondReport_bitrateFromTheBytesSinceTheFirst() {
        parser.parse(report(1_000_000, outbound(10_000, 1_000_000), codec))
        val stats = parser.parse(report(3_000_000, outbound(510_000, 3_000_000), codec))!!

        // 500 000 bytes in 2 s = 2 000 kbps.
        assertEquals(2000.0, stats.bitrateKbps!!, 1e-6)
    }

    @Test
    fun reportAtTheSameInstant_hasNoBitrate() {
        parser.parse(report(1_000_000, outbound(10_000, 1_000_000)))
        val stats = parser.parse(report(1_000_000, outbound(20_000, 1_000_000)))!!

        assertNull(stats.bitrateKbps)
    }

    @Test
    fun theStreamCarryingBytesIsRead_whenBothDirectionsReport() {
        val inbound = RTCStats(
            1_000_000,
            "inbound-rtp",
            "IN",
            mapOf("kind" to "video", "bytesReceived" to 90_000L, "frameWidth" to 1280, "frameHeight" to 720),
        )
        val stats = parser.parse(report(1_000_000, outbound(100, 1_000_000), inbound))!!

        assertEquals(1280, stats.width)
        assertEquals(720, stats.height)
    }

    @Test
    fun succeededPairWithoutNomination_andNoCodecEntry() {
        val succeeded = stats("P2", "candidate-pair", "state" to "succeeded", "currentRoundTripTime" to 0.1)
        val stats = parser.parse(report(1_000_000, outbound(10_000, 1_000_000, codecId = null), succeeded))!!

        assertNull(stats.codec)
        assertEquals(100.0, stats.roundTripMs!!, 1e-9)

        val noPair = LinkStatsParser().parse(report(1_000_000, outbound(10_000, 1_000_000, codecId = "missing")))!!
        assertNull(noPair.codec)
        assertNull(noPair.roundTripMs)
    }

    private val relayLocal = stats(
        "L1",
        "local-candidate",
        "candidateType" to "relay",
        "relayProtocol" to "tcp",
        "networkType" to "cellular",
    )
    private val hostLocal = stats("L2", "local-candidate", "candidateType" to "host", "networkType" to "wifi")
    private val srflxRemote = stats("R1", "remote-candidate", "candidateType" to "srflx")
    private val hostRemote = stats("R2", "remote-candidate", "candidateType" to "host")

    private fun pair(id: String, local: String, remote: String, vararg members: Pair<String, Any>) =
        stats(id, "candidate-pair", "localCandidateId" to local, "remoteCandidateId" to remote, *members)

    @Test
    fun selectedPair_givesARelayedPathWithItsProtocolAndNetwork_overAnotherNominatedPair() {
        val direct = pair("P1", "L2", "R2", "nominated" to true, "currentRoundTripTime" to 0.01)
        val relayed = pair("P2", "L1", "R1", "nominated" to true, "currentRoundTripTime" to 0.08)
        val transport = stats("T", "transport", "selectedCandidatePairId" to "P2")
        val stats = parser.parse(
            report(
                1_000_000,
                outbound(10_000, 1_000_000),
                direct,
                relayed,
                transport,
                relayLocal,
                hostLocal,
                srflxRemote,
            ),
        )!!

        assertEquals(IcePath("relay", "srflx", "cellular", "tcp"), stats.path)
        assertEquals(80.0, stats.roundTripMs!!, 1e-9)
        assertEquals("local=relay/tcp remote=srflx network=cellular", stats.path!!.summary())
    }

    @Test
    fun selectedPairMissingFromTheReport_fallsBackToTheNominatedPair_aDirectPath() {
        val direct = pair("P1", "L2", "R2", "nominated" to true)
        val transport = stats("T", "transport", "selectedCandidatePairId" to "gone")
        val stats = parser.parse(
            report(1_000_000, outbound(10_000, 1_000_000), direct, transport, hostLocal, hostRemote),
        )!!

        assertEquals(IcePath("host", "host", "wifi", null), stats.path)
        assertEquals("local=host remote=host network=wifi", stats.path!!.summary())
    }

    @Test
    fun missingCandidateOrCandidateType_hasNoPath() {
        val noRemote = pair("P1", "L2", "R9", "nominated" to true)
        assertNull(parser.parse(report(1_000_000, outbound(10_000, 1_000_000), noRemote, hostLocal))!!.path)

        val untyped = stats("R3", "remote-candidate", "networkType" to "wifi")
        val noType = pair("P2", "L2", "R3", "nominated" to true)
        assertNull(
            LinkStatsParser().parse(report(1_000_000, outbound(10_000, 1_000_000), noType, hostLocal, untyped))!!.path,
        )

        val noIds = stats("P3", "candidate-pair", "nominated" to true)
        assertNull(LinkStatsParser().parse(report(1_000_000, outbound(10_000, 1_000_000), noIds))!!.path)
    }

    @Test
    fun pathSummary_leavesOutWhatIsUnknown() {
        assertEquals("local=prflx remote=relay", IcePath("prflx", "relay", null, null).summary())
    }

    @Test
    fun noVideoStream_isNull() {
        val audio = stats("A", "outbound-rtp", "kind" to "audio", "bytesSent" to 500L)

        assertNull(parser.parse(report(1_000_000, audio, codec, nominated)))
        assertNull(parser.parse(report(1_000_000)))
    }
}
