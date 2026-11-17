package com.crazestudio.stone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.crazestudio.stone.ui.StoneApp
import com.crazestudio.stone.ui.StoneWorkspaceViewModel

class MainActivity : ComponentActivity() {
    private val workspaceViewModel: StoneWorkspaceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            StoneApp(workspaceViewModel)
        }
    }
}
