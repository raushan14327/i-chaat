package com.example.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Success(val user: User) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val repository = AuthRepository(application, database)

    val currentUser: StateFlow<User?> = repository.currentUser

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _recoveryState = MutableStateFlow<String?>(null)
    val recoveryState: StateFlow<String?> = _recoveryState.asStateFlow()

    fun checkUserExists(userId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val exists = repository.checkUserExists(userId)
            onResult(exists)
        }
    }

    fun register(userId: String, username: String, bio: String, password: String, phoneOrEmail: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.register(userId, username, password, bio, phoneOrEmail)
            result.fold(
                onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
                onFailure = { e -> _uiState.value = AuthUiState.Error(e.message ?: "Registration failed") }
            )
        }
    }

    fun login(userId: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.login(userId, password)
            result.fold(
                onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
                onFailure = { e -> _uiState.value = AuthUiState.Error(e.message ?: "Login failed") }
            )
        }
    }

    fun updateProfile(username: String, bio: String, profilePicBase64: String?) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.updateProfile(username, bio, profilePicBase64)
            result.fold(
                onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
                onFailure = { e -> _uiState.value = AuthUiState.Error(e.message ?: "Profile update failed") }
            )
        }
    }

    fun recoverPassword(userId: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.recoverPassword(userId)
            result.fold(
                onSuccess = { message ->
                    _recoveryState.value = message
                    _uiState.value = AuthUiState.Idle
                },
                onFailure = { e ->
                    _uiState.value = AuthUiState.Error(e.message ?: "Failed to send recovery request")
                }
            )
        }
    }

    fun clearRecoveryState() {
        _recoveryState.value = null
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Idle
            repository.logout()
        }
    }
}
