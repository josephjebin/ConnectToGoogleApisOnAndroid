package com.jebkit.connecttogoogleapisonandroid.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.jebkit.connecttogoogleapisonandroid.data.calendar.GoogleCalendarEventModel
import com.jebkit.connecttogoogleapisonandroid.data.calendar.GoogleCalendarService
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed interface GoogleCalendarUiState {
    data class Success(val events: List<GoogleCalendarEventModel>):GoogleCalendarUiState
    data class Error(val exception: Exception): GoogleCalendarUiState
}

class GoogleCalendarViewModel(credential: GoogleAccountCredential): ViewModel() {
    private val googleCalendarService: GoogleCalendarService = GoogleCalendarService(credential)

    //300000 MS = 300 S = 5 minutes
    private val refreshIntervalMS: Long = 20000
    var googleCalendarUiState: GoogleCalendarUiState by mutableStateOf(GoogleCalendarUiState.Success(listOf()))

    fun refresh() {
        viewModelScope.launch {
            googleCalendarUiState = try {
                val events = googleCalendarService.getDataFromCalendar()
                GoogleCalendarUiState.Success(events)
            } catch (e: Exception) {
                GoogleCalendarUiState.Error(e)
            }
        }
    }

    fun cancel() {
        try {
            viewModelScope.cancel()
        } catch (_: Exception) {}
    }

    fun updateCalendarServiceCredentials(newCredential: GoogleAccountCredential) {
        googleCalendarService.updateCalendarServiceCredentials(newCredential)
    }
}