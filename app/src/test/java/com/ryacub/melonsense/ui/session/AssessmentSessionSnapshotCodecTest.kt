package com.ryacub.melonsense.ui.session

import com.ryacub.melonsense.ui.screens.KnockTestEvent
import com.ryacub.melonsense.ui.screens.KnockTestWorkflow
import com.ryacub.melonsense.ui.screens.sampleKnockWindow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AssessmentSessionSnapshotCodecTest {
    private val codec = AssessmentSessionSnapshotCodec()

    @Test
    fun roundTrip_preservesStableAssessmentAndPcmSamples() {
        val visual = sampleVisualResult()
        val window = sampleKnockWindow()
        val state =
            AssessmentSessionState(
                scanWorkflow = completeScanWorkflow(visual),
                knockWorkflow =
                    KnockTestWorkflow()
                        .reduce(KnockTestEvent.CaptureRequested)
                        .captureFinished(window),
                assessmentResult = sampleAssessment(visual),
            )

        val decoded = codec.decode(codec.encode(state))

        requireNotNull(decoded)
        assertEquals(state.scanWorkflow, decoded.scanWorkflow)
        assertEquals(state.assessmentResult, decoded.assessmentResult)
        assertEquals(1, decoded.knockWorkflow.validWindows.size)
        assertTrue(decoded.knockWorkflow.validWindows.single().samples.contentEquals(window.samples))
    }

    @Test
    fun decode_corruptOrUnsupportedSnapshotFailsClosed() {
        assertNull(codec.decode("not-json"))
        assertNull(codec.decode("{\"schemaVersion\":999}"))
    }

    @Test
    fun decode_semanticallyInvalidSessionFailsClosed() {
        val visual = sampleVisualResult()
        val validState =
            AssessmentSessionState(
                scanWorkflow = completeScanWorkflow(visual),
                knockWorkflow =
                    KnockTestWorkflow()
                        .reduce(KnockTestEvent.CaptureRequested)
                        .captureFinished(sampleKnockWindow()),
                assessmentResult = sampleAssessment(visual),
            )

        val completeWithoutVisual = JSONObject(codec.encode(validState))
        completeWithoutVisual.getJSONObject("scan").put("visual", JSONObject.NULL)

        val invalidAcceptedKnock = JSONObject(codec.encode(validState))
        invalidAcceptedKnock
            .getJSONObject("knock")
            .getJSONArray("windows")
            .getJSONObject(0)
            .getJSONObject("capture")
            .put("valid", false)

        val mismatchedAssessment = JSONObject(codec.encode(validState))
        mismatchedAssessment
            .getJSONObject("assessment")
            .getJSONObject("visual")
            .put("capturedAt", visual.capturedAtMillis + 1)

        assertNull(codec.decode(completeWithoutVisual.toString()))
        assertNull(codec.decode(invalidAcceptedKnock.toString()))
        assertNull(codec.decode(mismatchedAssessment.toString()))
    }

    @Test
    fun decode_rejectsTooManyKnockWindowsAndOversizedPcmWindow() {
        val state =
            AssessmentSessionState(
                scanWorkflow = completeScanWorkflow(sampleVisualResult()),
                knockWorkflow =
                    KnockTestWorkflow()
                        .reduce(KnockTestEvent.CaptureRequested)
                        .captureFinished(sampleKnockWindow()),
            )

        val tooManyWindows = JSONObject(codec.encode(state))
        val windows = tooManyWindows.getJSONObject("knock").getJSONArray("windows")
        repeat(3) { windows.put(JSONObject(windows.getJSONObject(0).toString())) }

        val oversizedPcm = JSONObject(codec.encode(state))
        oversizedPcm
            .getJSONObject("knock")
            .getJSONArray("windows")
            .getJSONObject(0)
            .put(
                "samples",
                Base64.getEncoder().encodeToString(
                    ByteArray((AssessmentSessionSnapshotCodec.MAX_SAMPLES_PER_WINDOW + 1) * Short.SIZE_BYTES),
                ),
            )

        assertNull(codec.decode(tooManyWindows.toString()))
        assertNull(codec.decode(oversizedPcm.toString()))
    }

    @Test
    fun encode_rejectsTooManyKnockWindows() {
        val windows = List(4) { sampleKnockWindow(capturedAtMillis = it.toLong()) }
        val state = AssessmentSessionState(knockWorkflow = KnockTestWorkflow(validWindows = windows))

        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(state)
        }
    }

    @Test
    fun encode_rejectsOversizedPcmWindow() {
        val oversized =
            sampleKnockWindow().copy(
                samples = ShortArray(AssessmentSessionSnapshotCodec.MAX_SAMPLES_PER_WINDOW + 1),
            )
        val state = AssessmentSessionState(knockWorkflow = KnockTestWorkflow(validWindows = listOf(oversized)))

        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(state)
        }
    }
}
