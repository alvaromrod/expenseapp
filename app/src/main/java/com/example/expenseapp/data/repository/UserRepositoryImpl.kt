package com.example.expenseapp.data.repository

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
    private val supabaseClient: SupabaseClient,
    private val sessionManager: SessionManager
) : UserRepository {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getCurrentUser(): Flow<User?> = supabaseClient.auth.sessionStatus
        .map { status -> 
            (status as? SessionStatus.Authenticated)?.session?.user?.id ?: sessionManager.currentUserId 
        }
        .distinctUntilChanged()
        .flatMapLatest { userId: String? ->
            if (userId != null) {
                userDao.getUserByIdFlow(userId).map { it?.toDomain() }
            } else {
                flowOf(null)
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
        try {
            supabaseClient.postgrest["users"].upsert(
                RemoteUser(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    avatar_url = user.avatarUrl,
                    main_currency = user.mainCurrency
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        val name: String,
        val email: String,
        val avatar_url: String? = null,
        val main_currency: String = "EUR",
        val created_at: String? = null
    )

    override suspend fun syncUserFromSupabase(userId: String?): User? {
        val authUser = supabaseClient.auth.currentUserOrNull()
        val session = supabaseClient.auth.currentSessionOrNull()
        
        val finalUserId = userId 
            ?: authUser?.id 
            ?: session?.user?.id 
            ?: sessionManager.getUserId() 
            ?: return null
        
        // 1. Try to fetch from Supabase 'users' or 'profiles' table
        val remoteUser = try {
            // Try 'users' first
            val fromUsers = supabaseClient.postgrest["users"]
                .select(Columns.list("id", "name", "email", "avatar_url", "main_currency", "created_at")) {
                    filter { eq("id", finalUserId) }
                }.decodeSingleOrNull<RemoteUser>()
            
            if (fromUsers != null) fromUsers else {
                // Fallback to 'profiles' if 'users' is empty
                supabaseClient.postgrest["profiles"]
                    .select() {
                        filter { eq("id", finalUserId) }
                    }.decodeSingleOrNull<RemoteUser>()
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to fetch profile for $finalUserId", e)
            null
        }

        val user = if (remoteUser != null) {
            User(
                id = remoteUser.id,
                name = remoteUser.name,
                email = remoteUser.email,
                avatarUrl = remoteUser.avatar_url,
                mainCurrency = remoteUser.main_currency
            )
        } else {
            // 2. Fallback to Auth metadata ONLY if we are syncing the current user
            val isCurrentUser = (finalUserId == authUser?.id || finalUserId == session?.user?.id)
            val metadata = if (isCurrentUser) (authUser?.userMetadata ?: session?.user?.userMetadata) else null
            
            val name = metadata?.get("full_name")?.toString()?.removeSurrounding("\"") 
                ?: metadata?.get("name")?.toString()?.removeSurrounding("\"")
                ?: if (isCurrentUser) "" else "Unknown Member"
            
            val email = if (isCurrentUser) (authUser?.email ?: session?.user?.email ?: "") else ""
            val avatarUrl = metadata?.get("avatar_url")?.toString()?.removeSurrounding("\"")

            User(
                id = finalUserId,
                name = name.ifBlank { if (isCurrentUser) "" else "Unknown Member" },
                email = email,
                avatarUrl = avatarUrl,
                mainCurrency = "EUR"
            )
        }
        
        // Ensure it's in local DB
        userDao.insertUser(user.toEntity())
        
        // 3. If this is the CURRENT USER, also ensure their profile is in Supabase 'users' table 
        // so others (their group teammates) can see them!
        if (userId == null || userId == authUser?.id) {
            try {
                supabaseClient.postgrest["users"].upsert(
                    RemoteUser(
                        id = user.id,
                        name = user.name,
                        email = user.email,
                        avatar_url = user.avatarUrl,
                        main_currency = user.mainCurrency
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
    
    override suspend fun signOut() {
        try {
            supabaseClient.auth.signOut()
        } catch (e: Exception) {
            // Log or handle sign out error if necessary
        } finally {
            sessionManager.clearSession()
        }
    }
}
