package com.example.chapter_078

// import android.widget.Toast
// import androidx.compose.ui.platform.LocalContext
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.chapter_078.ui.theme.Chapter078Theme

/*
* ComponentActivity 역할은 뭐야? 어떤 종류의 액티비티들이 있지? 각 액티비티들은 어떤 역할을 해?
* */
class MainActivity : ComponentActivity() {
    // TODO: [todos/activity-lifecycle-callbacks-and-bundle.md](../../../../../../../../todos/activity-lifecycle-callbacks-and-bundle.md)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // TODO: [todos/setcontent-and-compose-entry-point.md](../../../../../../../../todos/setcontent-and-compose-entry-point.md)
        setContent {
            Chapter078Theme {
                UnitConverter()
            }
        }
    }
}

@Composable
fun UnitConverter() {
    // TODO: [todos/compose-remember-mutablestate-and-by.md](../../../../../../../../todos/compose-remember-mutablestate-and-by.md)
    var open by remember { mutableStateOf(false) }

    // TODO: [todos/compose-column-and-row-layout.md](../../../../../../../../todos/compose-column-and-row-layout.md)
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
        // TODO: [todos/android-dp-unit.md](../../../../../../../../todos/android-dp-unit.md)
        // padding(10.dp, 75.dp)
    ) {
        Text("Unit Converter")
        // TODO: [todos/compose-padding-vs-spacer.md](../../../../../../../../todos/compose-padding-vs-spacer.md)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = "", onValueChange = {})
        Spacer(modifier = Modifier.height(16.dp))
        // TODO: [todos/compose-column-and-row-layout.md](../../../../../../../../todos/compose-column-and-row-layout.md)
        Row {
            // TODO: [todos/compose-box-usage.md](../../../../../../../../todos/compose-box-usage.md)
            Box {
                Button(onClick = { open = true }) {
                    Text("Select")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Arrow Down")
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    for (index: Int in 0..<4) {
                        // TODO: [todos/compose-list-item-key.md](../../../../../../../../todos/compose-list-item-key.md)
                        DropdownMenuItem(text = { Text("$index") }, onClick = {})
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box {
                Button(onClick = {}) {
                    Text("Select")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Arrow Down")
                }
            }
            // TODO: [todos/compose-localcontext.md](../../../../../../../../todos/compose-localcontext.md)
            // val context = LocalContext.current
            // Button(onClick = {
            //     // TODO: [todos/android-context-and-toast.md](../../../../../../../../todos/android-context-and-toast.md)
            //     Toast.makeText(
            //         context, "Thanks for clicking", Toast.LENGTH_LONG
            //     ).show()
            // }) {
            //     Text("Click Me!")
            // }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Result: ")
    }
}

// TODO: [todos/composable-annotation-and-recomposition.md](../../../../../../../../todos/composable-annotation-and-recomposition.md)
// TODO: [todos/compose-rendering-pipeline.md](../../../../../../../../todos/compose-rendering-pipeline.md)
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!", modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Chapter078Theme {
        Greeting("Android!")
    }
}

@Preview(showBackground = true)
@Composable
fun UnitConverterPreview() {
    Chapter078Theme {
        UnitConverter()
    }
}