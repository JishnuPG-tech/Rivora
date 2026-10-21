package com.rivora.ui.main

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_initialState() = runTest {
    val viewModel = MainScreenViewModel()
    val state = viewModel.uiState.first()
    assertEquals("queue", state.currentView)
    assertEquals(0, state.activeTab)
    assertEquals("MB/s", state.unit)
  }
}
