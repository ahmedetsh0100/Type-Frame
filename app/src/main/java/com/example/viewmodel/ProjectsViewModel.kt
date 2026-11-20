package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FontRepository
import com.example.data.ProjectEntity
import com.example.data.ProjectRepository
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

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.observeAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fonts: StateFlow<List<FontItem>> = fontRepository.observeAllFonts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
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
        viewModelScope.launch {
            fontRepository.importFontFromUri(uri)
                .onSuccess { _statusMessage.value = "Imported ${it.displayName}" }
                .onFailure { _statusMessage.value = it.message ?: "Could not import font." }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }
}
