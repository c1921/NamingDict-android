package io.github.c1921.namingdict

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.c1921.namingdict.data.DictionaryRepository
import io.github.c1921.namingdict.ui.AppRoot
import io.github.c1921.namingdict.ui.DictViewModel
import io.github.c1921.namingdict.ui.theme.NamingDictTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NamingDictTheme {
                val context = LocalContext.current
                val repository = remember { DictionaryRepository(context) }
                val viewModel: DictViewModel = viewModel(factory = DictViewModel.factory(repository))
                AppRoot(viewModel = viewModel)
            }
        }
    }
}
