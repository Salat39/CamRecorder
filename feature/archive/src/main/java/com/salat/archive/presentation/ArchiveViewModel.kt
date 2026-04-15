package com.salat.archive.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.salat.archive.domain.entity.ArchiveContent
import com.salat.archive.domain.usecases.GetArchiveContentUseCase
import com.salat.archive.presentation.entity.DisplayArchiveDay
import com.salat.archive.presentation.entity.DisplayArchiveSelectedSegment
import com.salat.archive.presentation.mappers.toDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import presentation.BaseViewModel
import presentation.mvi.MviAction
import presentation.mvi.MviViewState

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val getArchiveContentUseCase: GetArchiveContentUseCase,
) : BaseViewModel<ArchiveViewModel.ViewState, ArchiveViewModel.Action>(ViewState()) {

    init {
        sendAction(Action.LoadArchive)
    }

    override fun onReduceState(viewAction: Action): ViewState = when (viewAction) {
        Action.LoadArchive -> {
            viewModelScope.launch(Dispatchers.IO) {
                sendAction(Action.SetLoading(true))
                val content = runCatching { getArchiveContentUseCase.execute() }
                    .getOrElse { ArchiveContent(days = emptyList(), invalidFiles = emptyList()) }
                sendAction(
                    Action.SetArchiveData(
                        content = content,
                        days = content.toDisplay(),
                    )
                )
            }
            state.value.copy(isLoading = true)
        }

        is Action.SetLoading -> state.value.copy(isLoading = viewAction.value)

        is Action.SetArchiveData -> state.value.copy(
            isLoading = false,
            archiveContent = viewAction.content,
            days = viewAction.days,
        )

        is Action.OnSegmentClick -> state.value.copy(selectedSegment = viewAction.segment)

        Action.DismissSegment -> state.value.copy(selectedSegment = null)
    }

    @Immutable
    data class ViewState(
        val isLoading: Boolean = true,
        val archiveContent: ArchiveContent? = null,
        val days: List<DisplayArchiveDay> = emptyList(),
        val selectedSegment: DisplayArchiveSelectedSegment? = null,
    ) : MviViewState {
        val showEmptyState: Boolean
            get() = !isLoading && days.isEmpty()
    }

    sealed class Action : MviAction {
        data object LoadArchive : Action()
        data class SetLoading(val value: Boolean) : Action()
        data class SetArchiveData(
            val content: ArchiveContent,
            val days: List<DisplayArchiveDay>,
        ) : Action()
        data class OnSegmentClick(val segment: DisplayArchiveSelectedSegment) : Action()
        data object DismissSegment : Action()
    }
}
