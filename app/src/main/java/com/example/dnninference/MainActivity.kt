package com.example.dnninference

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.dnninference.ui.theme.DNNInferenceTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import com.example.dnninference.Classifier.Backend


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DNNInferenceTheme {
                val context = LocalContext.current
                var selectedBackend by remember { mutableStateOf("CPU") }
                var label by remember { mutableStateOf("No result yet") }
                var imageUri by remember { mutableStateOf<Uri?>(null) }

                val galleryLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    imageUri = uri
                }

                val backends = listOf("CPU", "GPU", "NNAPI")

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text("Select Backend", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    backends.forEach { backend ->
                        Button(onClick = { selectedBackend = backend }, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            Text(backend)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(onClick = {
                        galleryLauncher.launch("image/*")
                    }) {
                        Text("Pick Image from Gallery")
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(onClick = {
                        imageUri?.let { uri ->
                            try {
                                val bitmap: Bitmap = if (Build.VERSION.SDK_INT < 28) {
                                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                                } else {
                                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                                    // Force decode to a SOFTWARE bitmap (not hardware, so can copy/pixel-access)
                                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                        decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                                    }
                                }


                                Log.i("MainActivity", "Running inference on selected image...")

                                // Map backend string to enum
                                val backendEnum = when (selectedBackend) {
                                    "GPU" -> Backend.GPU
                                    else -> Backend.CPU // Default to CPU; add NNAPI if needed
                                }

                                val classifier = Classifier(context, backend = backendEnum)
                                label = classifier.classify(bitmap)
                                classifier.close()
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error during classification", e)
                                label = "Error: ${e.message}"
                            }
                        } ?: run {
                            label = "No image selected"
                        }
                    }) {
                        Text("Run Inference")
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Result: $label")
                }
            }
        }
    }
}
