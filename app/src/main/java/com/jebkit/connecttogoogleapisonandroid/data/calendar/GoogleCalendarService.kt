package com.jebkit.connecttogoogleapisonandroid.data.calendar

import android.util.Log
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class GoogleCalendarService(private var credential: GoogleAccountCredential) {
    private var calendar: Calendar

    init {
        calendar = initCalendarBuild()
    }

    fun updateCalendarServiceCredentials(newCredential: GoogleAccountCredential) {
        if(credential != newCredential) {
            credential = newCredential
            calendar = initCalendarBuild()
        }
    }

    private fun initCalendarBuild(): Calendar {
        val transport = AndroidHttp.newCompatibleTransport()
        val jsonFactory = JacksonFactory.getDefaultInstance()
        return Calendar.Builder(
            transport, jsonFactory, credential
        )
            .setApplicationName("GetEventCalendar")
            .build()
    }


    suspend fun getDataFromCalendar(): List<GoogleCalendarEventModel> {
        val now = DateTime(System.currentTimeMillis())
        val eventStrings = ArrayList<GoogleCalendarEventModel>()
        try {
            withContext(Dispatchers.IO) {
                val events = calendar.events().list("primary")
                    .setMaxResults(10)
                    .setTimeMin(now)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()
                val items = events.items

                for (event in items) {
                    var start = event.start.dateTime
                    if (start == null) {
                        start = event.start.date
                    }

                    eventStrings.add(
                        GoogleCalendarEventModel(
                            summary = event.summary,
                            startDate = start.toString()
                        )
                    )
                }
            }
        } catch (e: IOException) {
            Log.d("Google", e.message.toString())
            throw e
        }
        return eventStrings
    }
}