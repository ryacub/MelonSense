package com.ryacub.melonsense.ui.screens

import com.ryacub.melonsense.data.history.PickHistoryItem
import com.ryacub.melonsense.data.history.SweetnessRating
import com.ryacub.melonsense.data.history.TextureRating
import com.ryacub.melonsense.domain.model.ResultLabel

data class OutcomeEditorState(
    val resultLabel: ResultLabel,
    val sweetness: SweetnessRating?,
    val texture: TextureRating?,
) {
    val canSave: Boolean
        get() = sweetness != null && texture != null

    fun selectResultLabel(value: ResultLabel): OutcomeEditorState = copy(resultLabel = value)

    fun selectSweetness(value: SweetnessRating): OutcomeEditorState = copy(sweetness = value)

    fun selectTexture(value: TextureRating): OutcomeEditorState = copy(texture = value)

    companion object {
        fun from(item: PickHistoryItem): OutcomeEditorState =
            OutcomeEditorState(
                resultLabel = item.resultLabel,
                sweetness = item.sweetness,
                texture = item.texture,
            )
    }
}
