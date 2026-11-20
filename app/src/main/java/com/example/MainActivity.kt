package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ProjectsHomeScreen
import com.example.ui.screens.StudioEditorScreen
import com.example.ui.theme.TypeframeTheme
import com.example.viewmodel.ProjectsViewModel
import com.example.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TypeframeTheme {
                var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }

                val currentId = openProjectId
                if (currentId == null) {
                    val projectsViewModel: ProjectsViewModel = viewModel()
                    ProjectsHomeScreen(
                        viewModel = projectsViewModel,
                        onOpenProject = { projectId -> openProjectId = projectId }
                    )
                } else {
                    val studioViewModel: StudioViewModel = viewModel()
                    StudioEditorScreen(
                        projectId = currentId,
                        viewModel = studioViewModel,
                        onNavigateBack = { openProjectId = null }
                    )
                }
            }
        }
    }
}
