package com.jebkit.connecttogoogleapisonandroid.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential

@Suppress("UNCHECKED_CAST")
class GoogleCalendarViewModelFactory(private val credential: GoogleAccountCredential): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if(modelClass.isAssignableFrom(GoogleCalendarViewModel::class.java))
            return GoogleCalendarViewModel(credential) as T
        throw IllegalArgumentException("Google Calendar View Model not found.")
    }
}