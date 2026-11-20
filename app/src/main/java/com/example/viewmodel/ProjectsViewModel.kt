package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppThemeMode
import com.example.data.FontRepository
import com.example.data.ProjectEntity
import com.example.data.ProjectRepository
import com.example.data.StudioPreferences
import com.example.data.ThemeSettings
import com.example.data.TypeframeDatabase
import com.example.model.FontItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = TypeframeDatabase.getInstance(application)
    val projectRepository = ProjectRepository(application, db.projectDao())
    val fontRepository = FontRepository(application, db.fontDao())
    val preferences = StudioPreferences.getInstance(application)

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.observeAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fonts: StateFlow<List<FontItem>> = fontRepository.observeAllFonts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeSettings: StateFlow<ThemeSettings> = preferences.themeSettings
    val favoriteFonts: StateFlow<Set<String>> = preferences.favoriteFonts

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setThemeMode(mode: AppThemeMode) {
        preferences.setThemeMode(mode)
    }

    fun setAccentColor(argb: Int) {
        preferences.setAccentColor(argb)
    }

    fun createBlankProject(
        title: String,
        widthPx: Int,
        heightPx: Int,
        transparentBg: Boolean,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val doc = projectRepository.createBlankProject(title, widthPx, heightPx, transparentBg)
            onCreated(doc.id)
        }
    }

    fun createProjectFromImage(uri: Uri, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            projectRepository.createProjectFromImageUri(uri)
                .onSuccess { onCreated(it.id) }
                .onFailure { _statusMessage.value = it.message ?: "Failed to import photo." }
        }
    }

    fun importFont(uri: Uri) {
        importMultipleFonts(listOf(uri))
    }

    fun importMultipleFonts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            var lastName = ""
            var lastError: String? = null
            for (uri in uris) {
                fontRepository.importFontFromUri(uri)
                    .onSuccess {
                        successCount++
                        lastName = it.displayName
                    }
                    .onFailure {
                        lastError = it.message
                    }
            }
            _statusMessage.value = when {
                successCount == 1 -> "Imported $lastName"
                successCount > 1 -> "Imported $successCount fonts"
                else -> lastError ?: "Could not import selected font files."
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }
}
