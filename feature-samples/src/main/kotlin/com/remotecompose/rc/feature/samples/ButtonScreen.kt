package com.remotecompose.rc.feature.samples

import androidx.compose.remote.core.operations.layout.managers.BoxLayout
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeContext
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcText
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.rsp
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.wrapContentHeight
import androidx.compose.remote.creation.modifiers.RecordingModifier
import com.remotecompose.rc.core.rcDocument

fun RcScope.RemoteJarButton(query: RcText) {
    Row(modifier = Modifier.wrapContentHeight()) {
//        Image(
//            image = remoteBitmapUrl("https://nativeblocks.io/blog/remote-compose-android-server-driven-ui/cover.jpg")
//        )
//        Text("This is button text")

//        val count = remoteNamedFloat("count", 0f)

//        Column(modifier = Modifier.fillMaxSize().padding(24f)) {
//            Text(
//                text = createTextFromFloat(count, 4, 0, 0),
//                fontSize = 48.rsp
//            )
//            Box(
//                modifier = Modifier
//                    .size(120f, 48f)
//                    .background(0xFF6200EE)
//                    .onClick {
//                        // Increment entirely on the player — no network call
//                        setValue(count, count + 1f)
//                    }
//            ) {
//                Text("Tap me", color = 0xFFFFFFFF.toInt())
//            }
//        }

        Column(modifier = Modifier.fillMaxSize().padding(16f)) {
            // The document displays whatever the Android side pushes in
            Text(text = query,  fontSize = 18.rsp)
            // Button triggers a named host action — Android receives it
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48f)
                    .background(0xFF6200EE.toLong())
                    .onClick {
                        hostAction("onSearchClicked")
                    }
            ) {
                Text("Search", color = 0xFFFFFFFF.toInt(), fontSize = 18.rsp)
            }
        }
    }
}

fun ButtonScreen(): ByteArray = rcDocument {
    // Declare the named state at the document ROOT, before the layout tree,
    // so the player's inflater registers it via NamedVariable.apply().
    val query = remoteNamedText("USER:searchQuery", "Hi")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24f)
    ) {
        Column {
            RemoteJarButton(query)
        }
    }
}

fun Dummy(): ByteArray {
    val ctx  = RemoteComposeContext(
        width = 400,
        height = 800,
        contentDescription = "",
        apiLevel = 6,
        profiles = 0,
        platform = JvmRcPlatformServices()
    ) {
        root {
            box(RecordingModifier().fillMaxSize(), BoxLayout.START, BoxLayout.START) {
                text("Hello World", fontSize = 24f)

            }
        }
    }
    return ctx.buffer()
}