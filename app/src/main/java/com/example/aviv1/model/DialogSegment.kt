package com.example.aviv1.model

import ai.picovoice.falcon.FalconSegment

/**
 * Data model for a dialog segment, based on Falcon Speaker Diarization results.
 */
data class DialogSegment(
    val speakerTag: Int,       // Unique speaker identifier
    val startTime: Float,      // Start time in seconds
    val endTime: Float,        // End time in seconds
    val text: String = "",     // Transcribed text for this segment (populated after transcription)
    val confidence: Float = 0f // Transcription confidence level (optional)
) {
    /**
     * Segment duration in seconds
     */
    val duration: Float
        get() = endTime - startTime

    companion object {
        /**
         * Converts a Falcon segment to our data model
         */
        fun fromFalconSegment(segment: FalconSegment): DialogSegment {
            return DialogSegment(
                speakerTag = segment.speakerTag,
                startTime = segment.startSec,
                endTime = segment.endSec
            )
        }

        /**
         * Converts a list of Falcon segments to our data model
         */
        fun fromFalconSegments(segments: List<FalconSegment>): List<DialogSegment> {
            return segments.map { fromFalconSegment(it) }
        }
    }
} 