package com.jebkit.connecttogoogleapisonandroid

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.calendar.CalendarScopes
import com.jebkit.connecttogoogleapisonandroid.data.calendar.GoogleCalendarEventModel
import com.jebkit.connecttogoogleapisonandroid.data.googleAuth.GoogleAuthConstants
import com.jebkit.connecttogoogleapisonandroid.data.googleAuth.GoogleAuthConstants.PREF_ACCOUNT_NAME
import com.jebkit.connecttogoogleapisonandroid.data.googleAuth.GoogleAuthConstants.REQUEST_GOOGLE_PLAY_SERVICES
import com.jebkit.connecttogoogleapisonandroid.ui.calendar.GoogleCalendarUiState
import com.jebkit.connecttogoogleapisonandroid.ui.calendar.GoogleCalendarViewModel
import com.jebkit.connecttogoogleapisonandroid.ui.calendar.GoogleCalendarViewModelFactory
import com.jebkit.connecttogoogleapisonandroid.ui.googleAuth.GoogleAuthViewModel
import com.jebkit.connecttogoogleapisonandroid.ui.googleAuth.GoogleAuthViewModelFactory
import com.jebkit.connecttogoogleapisonandroid.ui.theme.ConnectToGoogleApisOnAndroidTheme
import pub.devrel.easypermissions.EasyPermissions


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val googleAuthViewModelFactory = GoogleAuthViewModelFactory(initCredentials(this))
        val googleAuthViewModel = ViewModelProvider(this, googleAuthViewModelFactory)[GoogleAuthViewModel::class.java]
        val googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode == Activity.RESULT_OK && it.data!!.extras != null) {
                val accountName =
                    it.data!!.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                if (accountName != null) {
                    val settings = this.getPreferences(Context.MODE_PRIVATE)
                    val editor = settings?.edit()
                    editor?.putString(PREF_ACCOUNT_NAME, accountName)
                    editor?.apply()
                    googleAuthViewModel.setAccountName(accountName)
                }
            }
        }
        var googleCalendarExceptionFlag: Boolean by mutableStateOf(false)
        val setGoogleCalendarFlag: (Boolean) -> Unit = {
            googleCalendarExceptionFlag = it
        }

        val userRecoverableLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if(it.resultCode == Activity.RESULT_OK)
                setGoogleCalendarFlag(false)
        }


        setContent {
            ConnectToGoogleApisOnAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if(googleAuthViewModel.googleAccountCredential.selectedAccountName == null)
                        SignIn(this, this, googleAuthViewModel, googleSignInLauncher, setGoogleCalendarFlag)

                    if(googleAuthViewModel.gmail != "user") {
                        val googleCalendarViewModelFactory =
                            GoogleCalendarViewModelFactory(googleAuthViewModel.googleAccountCredential)
                        val googleCalendarViewModel = ViewModelProvider(
                            viewModelStore,
                            googleCalendarViewModelFactory)[GoogleCalendarViewModel::class.java]

                        Calendar(
                            gmail = googleAuthViewModel.gmail,
                            googleCalendarViewModel = googleCalendarViewModel,
                            userRecoverableLauncher = userRecoverableLauncher,
                            exceptionFlag = googleCalendarExceptionFlag,
                            setExceptionFlag = setGoogleCalendarFlag,
                            logoutButtonOnClick = {
                                val settings = this.getPreferences(Context.MODE_PRIVATE)
                                val editor = settings?.edit()
                                editor?.clear()
                                editor?.apply()
                                googleAuthViewModel.logout(initCredentials(this))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Calendar(
    gmail: String,
    googleCalendarViewModel: GoogleCalendarViewModel,
    userRecoverableLauncher: ActivityResultLauncher<Intent>,
    exceptionFlag: Boolean,
    setExceptionFlag: (Boolean) -> Unit,
    logoutButtonOnClick: () -> Unit
) {
    val calendarUiState = googleCalendarViewModel.googleCalendarUiState
    if(!exceptionFlag) googleCalendarViewModel.refresh()

    Box {
        Column {
            Text(
                text = "Hello ${gmail}!"
            )

            when(calendarUiState) {
                is GoogleCalendarUiState.Error -> {
                    setExceptionFlag(true)
                    when(calendarUiState.exception) {
                        is GooglePlayServicesAvailabilityIOException -> { acquireGooglePlayServices(LocalContext.current as Activity, setExceptionFlag) }
                        is UserRecoverableAuthIOException -> {
                            SideEffect {
                                userRecoverableLauncher.launch(calendarUiState.exception.intent)
                            }
                        }
                    }
                }

                is GoogleCalendarUiState.Success -> {
                    CalendarBody(googleEvents = calendarUiState.events)
                }
            }
        }

        FloatingActionButton(
            onClick = { googleCalendarViewModel.refresh() },
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 16.dp),
            content = {
                Text("refresh")
            }
        )

        FloatingActionButton(
            onClick = { logoutButtonOnClick() },
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.TopEnd)
                .padding(top = 120.dp, end = 16.dp),
            content = {
                Text(text = "logout")
            }
        )
    }
}

@Composable
fun CalendarBody(googleEvents: List<GoogleCalendarEventModel>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(googleEvents) {event ->
            Card {
                Text(text = event.startDate)

                Spacer(modifier = Modifier.width(32.dp))

                event.summary?.let { Text(text = it) }
            }
        }
    }
}

@Composable
private fun SignIn(
    activity: MainActivity,
    context: Context,
    viewModel: GoogleAuthViewModel,
    googleSignInLauncher: ActivityResultLauncher<Intent>,
    setGoogleCalendarExceptionFlag: (Boolean) -> Unit
) {
    if (GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS
    ) {
        //show message in a dialog
        acquireGooglePlayServices(activity, setGoogleCalendarExceptionFlag)
    }

    var havePermissionToGetAccounts by rememberSaveable {
        mutableStateOf(
            havePermissionToGetAccounts(activity, context)
        )
    }

    if (!havePermissionToGetAccounts) {
        AlertDialog(
            title = { Text(text = "We need to be able to sign in to Google!") },
            text = { Text(text = "How else are we going to get things from your Google Calendar and Tasks?") },
            onDismissRequest = {
                havePermissionToGetAccounts = havePermissionToGetAccounts(activity, context)
            },
            confirmButton = {
                Button(
                    onClick = {
                        havePermissionToGetAccounts =
                            havePermissionToGetAccounts(activity, context)
                    }
                ) {
                    Text(text = "I've given the app permission to sign in to Google.")
                }
            }
        )
    }

    var isNetworkAvailable by rememberSaveable { mutableStateOf(isNetworkAvailable(context)) }
    if (!isNetworkAvailable) {
        AlertDialog(
            title = { Text(text = "Network unavailable.") },
            text = { Text(text = "A network connection is needed to pull your calendars and tasks.") },
            onDismissRequest = { isNetworkAvailable = !isNetworkAvailable(context) },
            confirmButton = {
                Button(
                    onClick = { isNetworkAvailable = !isNetworkAvailable(context) }
                ) {
                    Text(text = "I've connected to the internet.")
                }
            }
        )
    }

    getGoogleAccountFromSharedPreferences(activity)?.let { viewModel.setAccountName(it) }
    if (viewModel.googleAccountCredential!!.selectedAccountName == null && viewModel.googleAccountCredential != null) {
        SideEffect {
            googleSignInLauncher.launch(viewModel.googleAccountCredential!!.newChooseAccountIntent())
        }
    }
}

private fun initCredentials(context: Context): GoogleAccountCredential {
    return GoogleAccountCredential.usingOAuth2(
        context,
        arrayListOf(CalendarScopes.CALENDAR)
    ).setBackOff(ExponentialBackOff())
}

private fun acquireGooglePlayServices(
    activity: Activity,
    setGoogleCalendarExceptionFlag: (Boolean) -> Unit
) {
    val apiAvailability = GoogleApiAvailability.getInstance()
    val connectionStatusCode =
        apiAvailability.isGooglePlayServicesAvailable(activity.applicationContext)
    if (apiAvailability.isUserResolvableError(connectionStatusCode))
        showGooglePlayServicesAvailabilityErrorDialog(
            activity,
            connectionStatusCode,
            setGoogleCalendarExceptionFlag
        )
}

fun showGooglePlayServicesAvailabilityErrorDialog(
    activity: Activity,
    connectionStatusCode: Int,
    setGoogleCalendarExceptionFlag: (Boolean) -> Unit
) {
    val apiAvailability = GoogleApiAvailability.getInstance()
    val dialog = apiAvailability.getErrorDialog(
        activity,
        connectionStatusCode,
        REQUEST_GOOGLE_PLAY_SERVICES
    )
    dialog?.setOnDismissListener { setGoogleCalendarExceptionFlag(false) }
    dialog?.setOnCancelListener { setGoogleCalendarExceptionFlag(false) }
    dialog?.show()

}

private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val nw = connectivityManager.activeNetwork ?: return false
    val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
    return when {
        actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        //for other device how are able to connect with Ethernet
        actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        //for check internet over Bluetooth
        actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
        else -> false
    }
}

private fun havePermissionToGetAccounts(activity: MainActivity, context: Context): Boolean {
    var permissions = retrievePermissions(context)
    return if (permissions.contains(GoogleAuthConstants.PERMISSION_GET_ACCOUNTS)) {
        true
    } else {
        EasyPermissions.requestPermissions(
            activity,
            "This app needs to access your Google account (via Contacts).",
            GoogleAuthConstants.REQUEST_PERMISSION_GET_ACCOUNTS,
            GoogleAuthConstants.PERMISSION_GET_ACCOUNTS
        )

        permissions = retrievePermissions(context)
        permissions.contains(GoogleAuthConstants.PERMISSION_GET_ACCOUNTS)
    }
}

private fun getGoogleAccountFromSharedPreferences(activity: MainActivity): String? {
    return activity
        .getPreferences(Context.MODE_PRIVATE)
        ?.getString(PREF_ACCOUNT_NAME, null)
}

fun retrievePermissions(context: Context): Array<String?> {
    val pkgName = context.packageName
    return try {
        context
            .packageManager
            .getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS).requestedPermissions
    } catch (e: PackageManager.NameNotFoundException) {
        arrayOfNulls(0)
    }
}