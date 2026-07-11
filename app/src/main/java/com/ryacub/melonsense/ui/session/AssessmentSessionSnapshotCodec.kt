package com.ryacub.melonsense.ui.session

import com.ryacub.melonsense.domain.audio.KnockAudioAnalyzer
import com.ryacub.melonsense.domain.model.AudioScanResult
import com.ryacub.melonsense.domain.model.KnockCapture
import com.ryacub.melonsense.domain.model.MelonAssessmentResult
import com.ryacub.melonsense.domain.model.PendingTrainingMedia
import com.ryacub.melonsense.domain.model.ResultLabel
import com.ryacub.melonsense.domain.model.TrainingMediaArtifact
import com.ryacub.melonsense.domain.model.TrainingMediaKind
import com.ryacub.melonsense.domain.model.VisualScanResult
import com.ryacub.melonsense.ui.screens.CapturedKnockWindow
import com.ryacub.melonsense.ui.screens.KnockTestPhase
import com.ryacub.melonsense.ui.screens.KnockTestState
import com.ryacub.melonsense.ui.screens.KnockTestWorkflow
import com.ryacub.melonsense.ui.screens.PickedAssessmentSavePhase
import com.ryacub.melonsense.ui.screens.PickedAssessmentSaveState
import com.ryacub.melonsense.ui.screens.ScanAssessmentPhase
import com.ryacub.melonsense.ui.screens.ScanAssessmentState
import com.ryacub.melonsense.ui.screens.ScanAssessmentWorkflow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

class AssessmentSessionSnapshotCodec {
    fun encode(state: AssessmentSessionState): String {
        require(state.knockWorkflow.validWindows.size <= KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT)
        val encoded =
            JSONObject()
                .put(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
                .put(KEY_SCAN, state.scanWorkflow.toJson())
                .put(KEY_KNOCK, state.knockWorkflow.toJson())
                .putNullableObject(KEY_ASSESSMENT, state.assessmentResult?.toJson())
                .put(KEY_SAVE_PHASE, state.pickedAssessmentSaveState.phase.name)
                .toString()
        require(encoded.length <= MAX_SNAPSHOT_CHARS)
        return encoded
    }

    fun decode(encoded: String): AssessmentSessionState? {
        if (encoded.isBlank() || encoded.length > MAX_SNAPSHOT_CHARS) return null
        return runCatching {
            val root = JSONObject(encoded)
            require(root.getInt(KEY_SCHEMA_VERSION) == SCHEMA_VERSION)
            val state =
                AssessmentSessionState(
                    scanWorkflow = root.getJSONObject(KEY_SCAN).toScanWorkflow(),
                    knockWorkflow = root.getJSONObject(KEY_KNOCK).toKnockWorkflow(),
                    assessmentResult = root.nullableObject(KEY_ASSESSMENT)?.toAssessmentResult(),
                    pickedAssessmentSaveState =
                        PickedAssessmentSaveState(
                            PickedAssessmentSavePhase.valueOf(root.getString(KEY_SAVE_PHASE)),
                        ),
                )
            require(state.hasValidInvariants())
            state
        }.getOrNull()
    }

    private fun AssessmentSessionState.hasValidInvariants(): Boolean {
        val activeVisual = scanWorkflow.visualScanResult
        if ((scanWorkflow.state.phase == ScanAssessmentPhase.Complete) != (activeVisual != null)) return false
        if (knockWorkflow.validWindows.any { !it.capture.isValid || it.samples.isEmpty() }) return false
        if (assessmentResult != null && assessmentResult.visualScanResult != activeVisual) return false
        if (activeVisual == null) {
            if (knockWorkflow.state != KnockTestState()) return false
            if (knockWorkflow.lastCapture != null || knockWorkflow.validWindows.isNotEmpty()) return false
            if (assessmentResult != null || pickedAssessmentSaveState != PickedAssessmentSaveState()) return false
        }
        return true
    }

    private fun ScanAssessmentWorkflow.toJson(): JSONObject =
        JSONObject()
            .put(KEY_PHASE, state.phase.name)
            .putNullableObject(KEY_VISUAL, visualScanResult?.toJson())

    private fun JSONObject.toScanWorkflow(): ScanAssessmentWorkflow =
        ScanAssessmentWorkflow(
            state = ScanAssessmentState(ScanAssessmentPhase.valueOf(getString(KEY_PHASE))),
            visualScanResult = nullableObject(KEY_VISUAL)?.toVisualResult(),
        )

    private fun KnockTestWorkflow.toJson(): JSONObject =
        JSONObject()
            .put(KEY_PHASE, state.phase.name)
            .putNullableObject(KEY_LAST_CAPTURE, lastCapture?.toJson())
            .put(
                KEY_WINDOWS,
                JSONArray().apply {
                    validWindows.forEach { put(it.toJson()) }
                },
            )

    private fun JSONObject.toKnockWorkflow(): KnockTestWorkflow {
        val windowsJson = getJSONArray(KEY_WINDOWS)
        require(windowsJson.length() <= KnockAudioAnalyzer.REQUIRED_KNOCK_COUNT)
        val windows =
            buildList {
                repeat(windowsJson.length()) { index ->
                    add(windowsJson.getJSONObject(index).toCapturedWindow())
                }
            }
        return KnockTestWorkflow(
            state = KnockTestState(KnockTestPhase.valueOf(getString(KEY_PHASE))),
            lastCapture = nullableObject(KEY_LAST_CAPTURE)?.toKnockCapture(),
            validWindows = windows,
        )
    }

    private fun CapturedKnockWindow.toJson(): JSONObject {
        require(samples.size <= MAX_SAMPLES_PER_WINDOW)
        val bytes = ByteBuffer.allocate(samples.size * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach(bytes::putShort)
        return JSONObject()
            .put(KEY_CAPTURE, capture.toJson())
            .put(KEY_SAMPLES, Base64.getEncoder().encodeToString(bytes.array()))
            .put(KEY_CAPTURED_AT, capturedAtMillis)
    }

    private fun JSONObject.toCapturedWindow(): CapturedKnockWindow {
        val bytes = Base64.getDecoder().decode(getString(KEY_SAMPLES))
        require(bytes.size % Short.SIZE_BYTES == 0)
        val sampleCount = bytes.size / Short.SIZE_BYTES
        require(sampleCount <= MAX_SAMPLES_PER_WINDOW)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val samples = ShortArray(sampleCount) { buffer.short }
        return CapturedKnockWindow(
            capture = getJSONObject(KEY_CAPTURE).toKnockCapture(),
            samples = samples,
            capturedAtMillis = getLong(KEY_CAPTURED_AT),
        )
    }

    private fun KnockCapture.toJson(): JSONObject =
        JSONObject()
            .put(KEY_PEAK, peakAmplitude)
            .put(KEY_RMS, rmsAmplitude)
            .put(KEY_FREQUENCY, estimatedFrequencyHz)
            .put(KEY_VALID, isValid)

    private fun JSONObject.toKnockCapture(): KnockCapture =
        KnockCapture(
            peakAmplitude = getInt(KEY_PEAK),
            rmsAmplitude = getInt(KEY_RMS),
            estimatedFrequencyHz = getInt(KEY_FREQUENCY),
            isValid = getBoolean(KEY_VALID),
        )

    private fun VisualScanResult.toJson(): JSONObject =
        JSONObject()
            .put(KEY_SCORE, score)
            .put(KEY_CONFIDENCE, confidencePercent)
            .put(KEY_CAPTURED_AT, capturedAtMillis)
            .put(KEY_EVIDENCE, evidence.toJson())
            .putNullableObject(KEY_PHOTO_ARTIFACT, photoArtifact?.toJson())

    private fun JSONObject.toVisualResult(): VisualScanResult =
        VisualScanResult(
            score = getInt(KEY_SCORE),
            confidencePercent = getInt(KEY_CONFIDENCE),
            capturedAtMillis = getLong(KEY_CAPTURED_AT),
            evidence = getJSONArray(KEY_EVIDENCE).toStringList(),
            photoArtifact = nullableObject(KEY_PHOTO_ARTIFACT)?.toArtifact(),
        )

    private fun AudioScanResult.toJson(): JSONObject =
        JSONObject()
            .put(KEY_SCORE, score)
            .put(KEY_CONFIDENCE, confidencePercent)
            .put(KEY_VALID_KNOCKS, validKnocks)
            .put(KEY_FREQUENCY, estimatedFrequencyHz)
            .put(KEY_CAPTURED_AT, capturedAtMillis)
            .put(KEY_EVIDENCE, evidence.toJson())
            .putNullableObject(KEY_AUDIO_ARTIFACT, audioArtifact?.toJson())

    private fun JSONObject.toAudioResult(): AudioScanResult =
        AudioScanResult(
            score = getInt(KEY_SCORE),
            confidencePercent = getInt(KEY_CONFIDENCE),
            validKnocks = getInt(KEY_VALID_KNOCKS),
            estimatedFrequencyHz = getInt(KEY_FREQUENCY),
            capturedAtMillis = getLong(KEY_CAPTURED_AT),
            evidence = getJSONArray(KEY_EVIDENCE).toStringList(),
            audioArtifact = nullableObject(KEY_AUDIO_ARTIFACT)?.toArtifact(),
        )

    private fun MelonAssessmentResult.toJson(): JSONObject =
        JSONObject()
            .putNullableObject(KEY_VISUAL, visualScanResult?.toJson())
            .put(KEY_AUDIO, audioScanResult.toJson())
            .put(KEY_RECOMMENDATION, recommendation)
            .put(KEY_RESULT_LABEL, resultLabel.name)
            .put(KEY_CONFIDENCE, confidencePercent)
            .putNullableObject(KEY_TRAINING_MEDIA, trainingMedia?.toJson())

    private fun JSONObject.toAssessmentResult(): MelonAssessmentResult =
        MelonAssessmentResult(
            visualScanResult = nullableObject(KEY_VISUAL)?.toVisualResult(),
            audioScanResult = getJSONObject(KEY_AUDIO).toAudioResult(),
            recommendation = getString(KEY_RECOMMENDATION),
            resultLabel = ResultLabel.valueOf(getString(KEY_RESULT_LABEL)),
            confidencePercent = getInt(KEY_CONFIDENCE),
            trainingMedia = nullableObject(KEY_TRAINING_MEDIA)?.toPendingTrainingMedia(),
        )

    private fun PendingTrainingMedia.toJson(): JSONObject =
        JSONObject()
            .putNullableObject(KEY_PHOTO_ARTIFACT, photoArtifact?.toJson())
            .putNullableObject(KEY_AUDIO_ARTIFACT, audioArtifact?.toJson())
            .put(KEY_CREATED_AT, createdAtMillis)
            .put(KEY_EXPIRES_AT, expiresAtMillis)

    private fun JSONObject.toPendingTrainingMedia(): PendingTrainingMedia =
        PendingTrainingMedia(
            photoArtifact = nullableObject(KEY_PHOTO_ARTIFACT)?.toArtifact(),
            audioArtifact = nullableObject(KEY_AUDIO_ARTIFACT)?.toArtifact(),
            createdAtMillis = getLong(KEY_CREATED_AT),
            expiresAtMillis = getLong(KEY_EXPIRES_AT),
        )

    private fun TrainingMediaArtifact.toJson(): JSONObject =
        JSONObject()
            .put(KEY_KIND, kind.name)
            .put(KEY_PATH, path)
            .put(KEY_MIME_TYPE, mimeType)
            .put(KEY_BYTE_SIZE, byteSize)
            .put(KEY_CAPTURED_AT, capturedAtMillis)
            .put(KEY_LAST_MODIFIED_AT, lastModifiedAtMillis)
            .putNullableValue(KEY_WIDTH, width)
            .putNullableValue(KEY_HEIGHT, height)
            .putNullableValue(KEY_SAMPLE_RATE, sampleRateHz)
            .putNullableValue(KEY_DURATION, durationMillis)

    private fun JSONObject.toArtifact(): TrainingMediaArtifact =
        TrainingMediaArtifact(
            kind = TrainingMediaKind.valueOf(getString(KEY_KIND)),
            path = getString(KEY_PATH),
            mimeType = getString(KEY_MIME_TYPE),
            byteSize = getLong(KEY_BYTE_SIZE),
            capturedAtMillis = getLong(KEY_CAPTURED_AT),
            lastModifiedAtMillis = getLong(KEY_LAST_MODIFIED_AT),
            width = nullableInt(KEY_WIDTH),
            height = nullableInt(KEY_HEIGHT),
            sampleRateHz = nullableInt(KEY_SAMPLE_RATE),
            durationMillis = nullableLong(KEY_DURATION),
        )

    private fun List<String>.toJson(): JSONArray {
        require(size <= MAX_EVIDENCE_COUNT)
        return JSONArray().apply {
            this@toJson.forEach { value ->
                require(value.length <= MAX_EVIDENCE_LENGTH)
                put(value)
            }
        }
    }

    private fun JSONArray.toStringList(): List<String> {
        require(length() <= MAX_EVIDENCE_COUNT)
        return buildList {
            repeat(length()) { index ->
                val value = getString(index)
                require(value.length <= MAX_EVIDENCE_LENGTH)
                add(value)
            }
        }
    }

    private fun JSONObject.putNullableObject(
        key: String,
        value: JSONObject?,
    ): JSONObject = put(key, value ?: JSONObject.NULL)

    private fun JSONObject.putNullableValue(
        key: String,
        value: Any?,
    ): JSONObject = put(key, value ?: JSONObject.NULL)

    private fun JSONObject.nullableObject(key: String): JSONObject? = if (isNull(key)) null else getJSONObject(key)

    private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)

    companion object {
        const val MAX_SAMPLES_PER_WINDOW = 50_000
        private const val SCHEMA_VERSION = 1
        private const val MAX_SNAPSHOT_CHARS = 800_000
        private const val MAX_EVIDENCE_COUNT = 32
        private const val MAX_EVIDENCE_LENGTH = 512

        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_SCAN = "scan"
        private const val KEY_KNOCK = "knock"
        private const val KEY_ASSESSMENT = "assessment"
        private const val KEY_SAVE_PHASE = "savePhase"
        private const val KEY_PHASE = "phase"
        private const val KEY_VISUAL = "visual"
        private const val KEY_AUDIO = "audio"
        private const val KEY_LAST_CAPTURE = "lastCapture"
        private const val KEY_WINDOWS = "windows"
        private const val KEY_CAPTURE = "capture"
        private const val KEY_SAMPLES = "samples"
        private const val KEY_CAPTURED_AT = "capturedAt"
        private const val KEY_PEAK = "peak"
        private const val KEY_RMS = "rms"
        private const val KEY_FREQUENCY = "frequency"
        private const val KEY_VALID = "valid"
        private const val KEY_SCORE = "score"
        private const val KEY_CONFIDENCE = "confidence"
        private const val KEY_EVIDENCE = "evidence"
        private const val KEY_PHOTO_ARTIFACT = "photoArtifact"
        private const val KEY_AUDIO_ARTIFACT = "audioArtifact"
        private const val KEY_VALID_KNOCKS = "validKnocks"
        private const val KEY_RECOMMENDATION = "recommendation"
        private const val KEY_RESULT_LABEL = "resultLabel"
        private const val KEY_TRAINING_MEDIA = "trainingMedia"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_EXPIRES_AT = "expiresAt"
        private const val KEY_KIND = "kind"
        private const val KEY_PATH = "path"
        private const val KEY_MIME_TYPE = "mimeType"
        private const val KEY_BYTE_SIZE = "byteSize"
        private const val KEY_LAST_MODIFIED_AT = "lastModifiedAt"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_SAMPLE_RATE = "sampleRate"
        private const val KEY_DURATION = "duration"
    }
}
