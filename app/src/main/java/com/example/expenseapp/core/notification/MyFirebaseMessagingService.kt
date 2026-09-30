package com.example.expenseapp.core.notification

import android.util.Log
import com.example.expenseapp.domain.repository.UserRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var userRepository: UserRepository

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "From: ${message.from}")

        // Check if message contains a data payload
        if (message.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: ${message.data}")
            val title = message.data["title"] ?: message.notification?.title ?: "New Expense"
            val body = message.data["body"] ?: message.notification?.body ?: "An expense was added to your group."
            NotificationHelper.showNotification(applicationContext, title, body)
        }

        // Check if message contains a notification payload
        message.notification?.let {
            Log.d("FCM", "Message Notification Body: ${it.body}")
            NotificationHelper.showNotification(applicationContext, it.title ?: "New Expense", it.body ?: "")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        
        // Update user profile with new token
        CoroutineScope(Dispatchers.IO).launch {
            try {
                userRepository.updateFcmToken(token)
            } catch (e: Exception) {
                Log.e("FCM", "Failed to update token", e)
            }
        }
    }
}
