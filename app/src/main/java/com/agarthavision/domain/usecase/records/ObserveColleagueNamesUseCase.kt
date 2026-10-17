package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.repository.ColleagueRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Colleagues' names by user id, for lists that mark a colleague's rows read-only
 * (14zcqntjph6). A `Flow`, the C4 exception every `Observe*` use case takes.
 */
class ObserveColleagueNamesUseCase @Inject constructor(
    private val colleagueRepository: ColleagueRepository,
) {
    operator fun invoke(): Flow<Map<String, String?>> = colleagueRepository.observeNames()
}
