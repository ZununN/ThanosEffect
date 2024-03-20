package kz.zunun.thanoseffect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kz.zunun.thanoseffect.ui.theme.ThanosEffectTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ThanosEffectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val trigger = remember { MutableSharedFlow<Unit>() }
                    val scope = rememberCoroutineScope()

                    ThanosEffectComponent(trigger, modifier = Modifier.size(200.dp)) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .background(Color.Red)
                                .clickable {
                                    scope.launch {
                                        trigger.emit(Unit)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}
