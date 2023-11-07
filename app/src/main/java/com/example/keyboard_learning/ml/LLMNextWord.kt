package com.example.keyboard_learning.ml

import android.app.Application
import android.text.SpannableStringBuilder
import android.util.JsonReader
import android.util.Log
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.keyboard_learning.tokenization.GPT2Tokenizer
import com.example.keyboard_learning.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.random.Random

private const val SEQUENCE_LENGTH = 64
private const val VOCAB_SIZE = 50257
private const val NUM_HEAD = 12
private const val NUM_LITE_THREADS = 4
private const val MODEL_PATH = "model.tflite"
private const val VOCAB_PATH = "gpt2-vocab.json"
private const val MERGES_PATH = "gpt2-merges.txt"
private const val TAG = "LLMNextWord"

private typealias PredictionsType = Array<Array<FloatArray>>

class LLMNextWord(application: Application) : AndroidViewModel(application) {

    private val initJob: Job
    private var nextTokenJob: Job? = null

    private lateinit var tokenizer: GPT2Tokenizer
    private lateinit var tflite: Interpreter

    private val sampleSpace = 25

    private val _prompt = MutableLiveData("")
    val prompt: LiveData<String> = _prompt

    init {
        initJob = viewModelScope.launch {
            val encoder = loadGPTEncoder()
            val decoder = encoder.entries.associateBy({ it.value }, { it.key })
            val bpeRanks = loadBPERanks()

            tokenizer = GPT2Tokenizer(encoder, decoder, bpeRanks)
            tflite = loadTFModel()
        }
    }

    public override fun onCleared() {
        super.onCleared()
        tflite.close()
    }

    fun promptToken(prompt: String): List<String> {
        var nextToken: List<String> = listOf()
        nextTokenJob = viewModelScope.launch {
            initJob.join()
            nextTokenJob?.cancelAndJoin()
            nextToken = getNextWord(prompt)
        }
        return nextToken
    }

    private fun getNextWord(startPrompt: String, quantityOfTokens: Int = 3): List<String>{
            val tokens = tokenizer.encode(startPrompt)

            // calculate the number of inputted tokens
            val maxTokens = tokens.takeLast(SEQUENCE_LENGTH).toIntArray()
            // calculate the number of tokens needed to fill the input sequence model
            val paddedTokens = maxTokens + IntArray(SEQUENCE_LENGTH - maxTokens.size)
            val inputIds = Array(1) { paddedTokens }

            val predictions: PredictionsType =
                Array(1) { Array(SEQUENCE_LENGTH) { FloatArray(VOCAB_SIZE) } }
            val modelOutputs = mutableMapOf<Int, Any>(0 to predictions)

            tflite.runForMultipleInputsOutputs(arrayOf(inputIds), modelOutputs)
            val outputLogits = predictions[0][maxTokens.size - 1]

            val nextToken: List<Int> = getNextTokens(outputLogits, quantityOfTokens)
            val decodedTokens = tokenizer.decode(nextToken)

            Log.d("decodedTokens", decodedTokens)

            return decodedTokens.split(" ")
        }

    private suspend fun loadTFModel(): Interpreter = withContext(Dispatchers.IO) {
        val assetFileDescriptor = getApplication<Application>().assets.openFd(MODEL_PATH)
        assetFileDescriptor.use {
            val fileChannel = FileInputStream(assetFileDescriptor.fileDescriptor).channel
            val modelBuffer =
                fileChannel.map(FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength)

            val opts = Interpreter.Options()
            opts.numThreads = NUM_LITE_THREADS
            return@use Interpreter(modelBuffer, opts)
        }
    }

    private suspend fun loadBPERanks(): Map<Pair<String, String>, Int> =
        withContext(Dispatchers.IO) {
            hashMapOf<Pair<String, String>, Int>().apply {
                val mergesStream = getApplication<Application>().assets.open(MERGES_PATH)
                mergesStream.use { stream ->
                    val mergesReader = BufferedReader(InputStreamReader(stream))
                    mergesReader.useLines { seq ->
                        seq.drop(1).forEachIndexed { i, s ->
                            val list = s.split(" ")
                            val keyTuple = list[0] to list[1]
                            put(keyTuple, i)
                        }
                    }
                }
            }
        }

    private suspend fun loadGPTEncoder(): Map<String, Int> = withContext(Dispatchers.IO) {
        hashMapOf<String, Int>().apply {
            val vocabStream = getApplication<Application>().assets.open(VOCAB_PATH)
            vocabStream.use {
                val vocabReader = JsonReader(InputStreamReader(it, "UTF-8"))
                vocabReader.beginObject()
                while (vocabReader.hasNext()) {
                    val key = vocabReader.nextName()
                    val value = vocabReader.nextInt()
                    put(key, value)
                }
                vocabReader.close()
            }
        }
    }

    private fun getNextTokens(values: FloatArray, quantityOfTokens: Int): List<Int> {
        val filteredLogitsWithIndexes = values
            .mapIndexed { index, fl -> (index to fl) }
            .sortedByDescending { it.second }
            .take(sampleSpace)

        // Softmax calculation
        val filteredLogits = filteredLogitsWithIndexes.map { it.second }
        val maxValue = filteredLogits.max()
        val expLogits = filteredLogits.map { exp(it - maxValue) }
        val expSum = expLogits.sum()
        val softmax = expLogits.map { it.div(expSum) }

        val logitsIndex = filteredLogitsWithIndexes.map { it.first }
        return nthSamples(logitsIndex, softmax, quantityOfTokens)
    }

    private fun nthSamples(
        logitsIndex: List<Int>,
        softmax: List<Float>,
        quantityOfTokens: Int
    ): MutableList<Int> {
        val indexes = MutableList(quantityOfTokens) { 0 }

        for (i in 0 until quantityOfTokens) {
            val index = randomSample(softmax)
            indexes.add(index)
        }

        return indexes.map { logitsIndex[it] }.toMutableList() // return the
    }

    private fun randomSample(probabilities: List<Float>): Int {
        val rnd = probabilities.sum() * Random.nextFloat()
        var acc = 0f

        probabilities.forEachIndexed { i, fl ->
            acc += fl
            if (rnd < acc) {
                return i
            }
        }

        return probabilities.size - 1
    }
}

@BindingAdapter("prompt")
fun TextView.formatCompletion(prompt: String): Unit {
    text = when {
        prompt.isEmpty() -> "When I was a child, I"
        else -> {
            val str = SpannableStringBuilder(prompt)
            val bgCompletionColor = androidx.core.content.res.ResourcesCompat.getColor(resources, R.color.colorPrimary, context.theme)
            str.setSpan(android.text.style.BackgroundColorSpan(bgCompletionColor), prompt.length, str.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            str
        }
    }
}