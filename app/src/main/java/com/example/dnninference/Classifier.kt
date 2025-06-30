package com.example.dnninference

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp

class Classifier(
    private val context: Context,
    private val modelPath: String = "mobilenet_v2_1.0_224.tflite",
    private val labelPath: String = "labels.txt",
    private val backend: Backend = Backend.CPU
) {
    private val TAG = "Classifier"
    private var interpreter: Interpreter? = null
    private var labels: List<String> = listOf()
    private var gpuDelegate: GpuDelegate? = null

    enum class Backend { CPU, GPU }

    init {
        try {
            Log.i(TAG, "Loading model file...")
            val options = Interpreter.Options()
            if (backend == Backend.GPU) {
                Log.i(TAG, "Using GPU backend")
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
            } else {
                Log.i(TAG, "Using CPU backend")
            }
            interpreter = Interpreter(loadModelFile(context, modelPath), options)
            Log.i(TAG, "Model loaded successfully")

            Log.i(TAG, "Loading labels...")
            labels = loadLabels(context, labelPath)
            Log.i(TAG, "Labels loaded: ${labels.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing classifier", e)
        }
    }

    fun classify(bitmap: Bitmap): String {
        Log.i(TAG, "Preparing image for inference...")

        val safeBitmap = toMutableARGB8888(bitmap)
        val resizedBitmap = Bitmap.createScaledBitmap(safeBitmap, 224, 224, true)

        // Modern TFLite Support image pipeline
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(127.5f, 127.5f)) // Normalizes to [-1, 1]
            .build()

        val inputImage = TensorImage(interpreter!!.getInputTensor(0).dataType())
        inputImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(inputImage)
        val inputBuffer = processedImage.buffer

        val outputShape = interpreter!!.getOutputTensor(0).shape()
        val outputType = interpreter!!.getOutputTensor(0).dataType()
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, outputType)

        Log.i(TAG, "Running inference...")
        try {
            interpreter!!.run(inputBuffer, outputBuffer.buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            return "Inference failed: ${e.message}"
        }

        val probabilities = outputBuffer.floatArray
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

        return if (maxIndex == -1) {
            Log.w(TAG, "Could not determine class")
            "Unknown"
        } else {
            val result = "${labels[maxIndex]} (${String.format("%.2f", probabilities[maxIndex] * 100)}%)"
            Log.i(TAG, "Prediction: $result")
            result
        }
    }



    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
    }

    companion object {
        private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
            val assetFileDescriptor = context.assets.openFd(filename)
            FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = assetFileDescriptor.startOffset
                val declaredLength = assetFileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }

        private fun loadLabels(context: Context, filename: String): List<String> {
            return context.assets.open(filename).bufferedReader().readLines()
        }
    }
}

// ----------- FIX: robust bitmap conversion! ---------------
fun toMutableARGB8888(input: Bitmap): Bitmap {
    val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    canvas.drawBitmap(input, 0f, 0f, null)
    return output
}

