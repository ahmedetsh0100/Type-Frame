package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.StudioPreferences
import com.example.ui.screens.ProjectsHomeScreen
import com.example.ui.screens.StudioEditorScreen
import com.example.ui.theme.TypeframeTheme
import com.example.viewmodel.ProjectsViewModel
import com.example.viewmodel.StudioViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = StudioPreferences.getInstance(applicationContext)
        setContent {
            val themeSettings by prefs.themeSettings.collectAsStateWithLifecycle()
            TypeframeTheme(settings = themeSettings) {
                var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(550)
                    showSplash = false
                }

                Box(modifier = Modifier.fillMaxSize()) {
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

                    AnimatedVisibility(
                        visible = showSplash,
                        exit = fadeOut()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0F1219))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.htype_icon_clean),
                                    contentDescription = "HTYPE Splash Logo",
                                    modifier = Modifier
                                        .size(112.dp)
                                        .clip(RoundedCornerShape(26.dp))
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "HTYPE",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 4.sp
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
