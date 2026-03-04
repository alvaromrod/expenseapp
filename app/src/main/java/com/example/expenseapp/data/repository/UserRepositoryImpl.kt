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
import kotlinx.coroutines.flow.Flow
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
