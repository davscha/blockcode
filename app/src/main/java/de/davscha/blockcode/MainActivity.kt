package de.davscha.blockcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import de.davscha.blockcode.ui.BlockcodeTheme
import de.davscha.blockcode.ui.EditorScreen
import de.davscha.blockcode.ui.EditorViewModel

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlockcodeTheme {
                EditorScreen(vm)
            }
        }
    }
}
