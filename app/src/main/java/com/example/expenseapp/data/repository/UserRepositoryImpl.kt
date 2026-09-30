package com.example.expenseapp.data.repository

import com.example.expenseapp.data.local.AppDatabase
import com.example.expenseapp.data.local.dao.UserDao
import com.example.expenseapp.data.repository.mapper.toDomain
import com.example.expenseapp.data.repository.mapper.toEntity
import com.example.expenseapp.domain.model.User
import com.example.expenseapp.domain.repository.UserRepository
import io.github.jan.supabase.SupabaseClient
import com.example.expenseapp.core.session.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.Flow
import android.util.Log
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val appDatabase: AppDatabase,
    private val supabaseClient: SupabaseClient,
    private val sessionManager: SessionManager
) : UserRepository {

    /**
     * Returns the current user immediately from local Room DB using the userId stored in DataStore.
     * Does NOT wait for Supabase Auth network validation (which can take 15-20s on restrictive
     * networks like GrapheneOS with strict TLS). The Supabase session is only used as a fallback
     * if DataStore has no userId (e.g. first launch / logged out).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getCurrentUser(): Flow<User?> = sessionManager.currentUserFlow
        .distinctUntilChanged()
        .flatMapLatest { prefUserId: String? ->
            if (prefUserId != null) {
                // Fast path: userId already in DataStore → query Room directly, no network needed
                userDao.getUserByIdFlow(prefUserId).map { it?.toDomain() }
            } else {
                // Slow path: no saved session, wait for Supabase Auth to authenticate
                supabaseClient.auth.sessionStatus
                    .map { status -> (status as? SessionStatus.Authenticated)?.session?.user?.id }
                    .distinctUntilChanged()
                    .flatMapLatest { userId ->
                        if (userId != null) userDao.getUserByIdFlow(userId).map { it?.toDomain() }
                        else flowOf(null)
                    }
            }
        }

    override fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getUserById(id: String): User? {
        return userDao.getUserById(id)?.toDomain()
    }

    override suspend fun updateProfile(user: User) {
        userDao.insertUser(user.toEntity())
        
        // Push to Supabase users table
        supabaseClient.postgrest["users"].upsert(
            RemoteUser(
                id = user.id,
                name = user.name,
                email = user.email,
                avatar_url = user.avatarUrl,
                main_currency = user.mainCurrency,
                fcm_token = user.fcmToken
            )
        )
    }

    override suspend fun uploadAvatar(imageBytes: ByteArray): String {
        val authUser = supabaseClient.auth.currentUserOrNull()
            ?: throw Exception("Not authenticated")
        val bucket = supabaseClient.storage["avatars"]
        val fileName = "${authUser.id}/${System.currentTimeMillis()}.jpg"
        bucket.upload(fileName, imageBytes) {
            upsert = true
        }
        return bucket.publicUrl(fileName)
    }

    @Serializable
    private data class RemoteUser(
        val id: String,
        val name: String? = null,
        val email: String? = null,
        val avatar_url: String? = null,
        val main_currency: String? = "EUR",
        val fcm_token: String? = null,
        val created_at: String? = null
    )

    private val syncThrottler = mutableMapOf<String, Long>()

    override suspend fun syncUserFromSupabase(userId: String?, force: Boolean): User? {
        val authUser = supabaseClient.auth.currentUserOrNull()
        val session = supabaseClient.auth.currentSessionOrNull()
        
        val finalUserId = userId 
            ?: authUser?.id 
            ?: session?.user?.id 
            ?: sessionManager.getUserId() 
            ?: return null
        
        val now = System.currentTimeMillis()
        val lastSync = syncThrottler[finalUserId] ?: 0L
        if (!force && now - lastSync < 5 * 60 * 1000) { // 5 minutes throttle
            Log.d("UserRepository", "Throttling sync for user $finalUserId (last sync: ${now - lastSync}ms ago)")
            // Still return local data if available
            return userDao.getUserById(finalUserId)?.toDomain()
        }
        
        Log.d("UserRepository", "Syncing user $finalUserId from Supabase...")
        
        // 1. Try to fetch from Supabase 'users' or 'profiles' table
        val remoteUser: RemoteUser? = try {
            // Try 'users' first
            Log.d("UserRepository", "Attempting fetch from 'users' table...")
            val fromUsers = supabaseClient.postgrest["users"]
                .select() {
                    filter { eq("id", finalUserId) }
                }.decodeSingleOrNull<RemoteUser>()
            
            val result = if (fromUsers != null) {
                Log.d("UserRepository", "Found user in 'users' table: ${fromUsers.name}")
                fromUsers
            } else {
                // Fallback to 'profiles' if 'users' is empty
                Log.d("UserRepository", "User not found in 'users', trying 'profiles'...")
                supabaseClient.postgrest["profiles"]
                    .select() {
                        filter { eq("id", finalUserId) }
                    }.decodeSingleOrNull<RemoteUser>()
            }
            // Mark sync as successful, set the throttle time
            syncThrottler[finalUserId] = now
            result
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to fetch profile for $finalUserId", e)
            null
        }

        val user = if (remoteUser != null) {
            User(
                id = remoteUser.id,
                name = remoteUser.name ?: "",
                email = remoteUser.email ?: "",
                avatarUrl = remoteUser.avatar_url,
                mainCurrency = remoteUser.main_currency ?: "EUR",
                fcmToken = remoteUser.fcm_token
            )
        } else {
            // 2. Fallback: Try Local DB first, then Auth metadata ONLY if we are syncing the current user
            val localUser = userDao.getUserById(finalUserId)
            val isCurrentUser = (finalUserId == authUser?.id || finalUserId == session?.user?.id)
            
            if (localUser != null && localUser.name.isNotBlank() && localUser.name != "Member") {
                // Keep local data if it exists and has a non-generic name
                localUser.toDomain()
            } else {
                val metadata = if (isCurrentUser) (authUser?.userMetadata ?: session?.user?.userMetadata) else null
                
                val nameFallback = if (isCurrentUser) "" else {
                    val email = if (isCurrentUser) (authUser?.email ?: session?.user?.email ?: "") else (localUser?.email ?: "")
                    if (email.isNotBlank()) email.substringBefore("@") else "Member"
                }

                val name = metadata?.get("full_name")?.toString()?.removeSurrounding("\"") 
                    ?: metadata?.get("name")?.toString()?.removeSurrounding("\"")
                    ?: localUser?.name?.takeIf { it.isNotBlank() && it != "Member" }
                    ?: nameFallback
                
                val email = if (isCurrentUser) (authUser?.email ?: session?.user?.email ?: "") else (localUser?.email ?: "")
                val avatarUrl = metadata?.get("avatar_url")?.toString()?.removeSurrounding("\"")

                User(
                    id = finalUserId,
                    name = if (name.isBlank() || name == "Member") {
                        val prefix = email.substringBefore("@")
                        if (prefix.isNotBlank() && prefix != email) prefix else if (isCurrentUser) "" else "Member"
                    } else name,
                    email = email,
                    avatarUrl = avatarUrl ?: localUser?.avatar_url,
                    mainCurrency = localUser?.main_currency ?: "EUR"
                )
            }
        }
        
        // Ensure it's in local DB
        userDao.insertUser(user.toEntity())
        
        // 3. If this is the CURRENT USER, also ensure their profile is in Supabase 'users' table 
        // so others (their group teammates) can see them!
        // ONLY UPSERT if we actually have a name to show!
        if ((userId == null || userId == authUser?.id) && user.name.isNotBlank()) {
            try {
                supabaseClient.postgrest["users"].upsert(
                    RemoteUser(
                        id = user.id,
                        name = user.name,
                        email = user.email,
                        avatar_url = user.avatarUrl,
                        main_currency = user.mainCurrency,
                        fcm_token = user.fcmToken
                    )
                )
            } catch (e: Exception) {
                Log.e("UserRepository", "Failed to push current user's profile to Supabase", e)
            }
        }
        
        // Persist session if we just found it
        sessionManager.saveSession(finalUserId)
        
        return user
    }
    
    override suspend fun updateFcmToken(token: String?) {
    val authUser = supabaseClient.auth.currentUserOrNull()
    val userId = authUser?.id ?: sessionManager.getUserId() ?: return
    
    Log.d("UserRepository", "Updating FCM token for userId: $userId")
    val localUser = userDao.getUserById(userId)?.toDomain()
    
    val userToUpdate = if (localUser != null) {
        localUser.copy(fcmToken = token)
    } else {
        // If not in local DB, create a minimal user to push to Supabase
        User(
            id = userId,
            name = authUser?.userMetadata?.get("name")?.toString() ?: "New User",
            email = authUser?.email ?: "",
            fcmToken = token
        )
    }
    updateProfile(userToUpdate)
}

    override suspend fun signOut() {
        try {
            supabaseClient.auth.signOut()
        } catch (e: Exception) {
            // Log or handle sign out error if necessary
        } finally {
            sessionManager.clearSession()
            try {
                appDatabase.clearAllTables()
                Log.d("UserRepository", "Successfully cleared Room database on signOut")
            } catch (e: Exception) {
                Log.e("UserRepository", "Failed to clear Room database on signOut", e)
            }
        }
    }
}
