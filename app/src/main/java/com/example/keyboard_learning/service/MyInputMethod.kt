
package com.example.keyboard_learning.service

import devandroid.makley.keyboard_api.Keyboard;
import devandroid.makley.keyboard_api.KeyboardView;
import devandroid.makley.keyboard_api.KeyboardView.OnKeyboardActionListener
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import com.example.keyboard_learning.R


class MyInputMethod : InputMethodService(), OnKeyboardActionListener {
    private var candidateView: CandidateView? = null
    private val composing = StringBuilder()
    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var isCaps = false
    private var isAlt = false

    private var predictionOn = false
    private var completionOn = false
    private var completions: Array<CompletionInfo>? = null


    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard, null) as KeyboardView?

        keyboard = Keyboard(this, R.xml.qwerty)
        keyboardView!!.keyboard = keyboard
        keyboardView!!.setOnKeyboardActionListener(this)
        return keyboardView!!
    }

    override fun onCreateCandidatesView(): View? {
        candidateView = CandidateView(this).also { 
            it.service = this
        }
        Log.d("onCreateCandidatesView", "onCreateCandidatesView: $candidateView")

        return candidateView
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray) {

        val inputConnection = currentInputConnection
        playClick(primaryCode)

        when (primaryCode) {

            Keyboard.KEYCODE_ALT -> {
                isAlt = !isAlt;
                val querty = if (isAlt) R.xml.qwerty_alt else R.xml.qwerty;

                keyboard = Keyboard(this, querty)
                keyboardView!!.keyboard = keyboard
                keyboardView!!.invalidateAllKeys()
            }
            Keyboard.KEYCODE_DELETE -> inputConnection.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_SHIFT -> {
                isCaps = !isCaps
                keyboard!!.isShifted = isCaps
                keyboardView!!.invalidateAllKeys()
            }

            Keyboard.KEYCODE_DONE -> inputConnection.sendKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_ENTER
                )
            )

            else -> {
                var code = primaryCode.toChar()
                if (Character.isLetter(code) && isCaps) {
                    code = code.uppercaseChar()
                }
                inputConnection.commitText(code.toString(), 1)
            }
        }
    }

    private fun playClick(i: Int) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (i) {
            32 -> audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR)
            Keyboard.KEYCODE_DONE, 10 -> audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN)
            Keyboard.KEYCODE_DELETE -> audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE)
            else -> audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    /**
     * Here we can treate if the prediction must be on or not
     */
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d("onStartInput", "onStartInput: $attribute")
        updateCandidates()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        updateCandidates()
        return super.onKeyUp(keyCode, event)
    }
    override fun onText(text: CharSequence) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}


    override fun onDisplayCompletions(completions: Array<CompletionInfo>?) {
        if (completionOn) {
            this.completions = completions
            if (completions == null) {
                setSuggestions(emptyList())
                return
            }

            val stringList = ArrayList<String>()
            for (i in completions.indices) {
                val ci = completions[i]
                stringList.add(ci.text.toString())
            }
            setSuggestions(stringList)
        }
    }


    private fun updateCandidates() {


        Log.d("updateCandidates", "updateCandidates: ${composing.isNotEmpty()} $completionOn")
        if (!completionOn) {
//            if (composing.isNotEmpty()) {
//                val list = getPredictions(composing.toString()).toList()
                setSuggestions(listOf("a", "b", "c"))
//            } else {
//                setSuggestions(emptyList())
//            }
        }

    }
    fun pickSuggestion(suggestion: String) {
        // Find the last space and replace from there with the suggestion
        val spacePos = composing.lastIndexOf(" ")
        if (spacePos > 0) {
            composing.delete(spacePos + 1, composing.length)
        } else {
            composing.setLength(0)
        }
        composing.append(suggestion)
        Log.d("pickSuggestion", "pickSuggestion: $composing")
        currentInputConnection.setComposingText(composing, 1)
    }

    private fun setSuggestions(suggestions: List<String>) {

        Log.d("setSuggestions", "setSuggestions: $suggestions")
        setCandidatesViewShown(suggestions.isNotEmpty() || isExtractViewShown)

        if (candidateView != null) {
            candidateView!!.setSuggestions(suggestions)
        }
    }

//    private fun getPredictions(seed: String): Sequence<String> {
//        // Only interested in the first nPredictions best predictions
//        val candidates = generateInitialCandidates(seed)
//            .entries
//            .sortedByDescending { it.value }
//            .take(N_PREDICTIONS)
//
//        // Build a word for each candidate
//        return candidates.map { buildWord("$seed${it.key}") }.asSequence()
//    }
//    private fun generateInitialCandidates(seed: String = ""): Map<Char, Float> {
//        val initValue = NGrams.START_CHAR.repeat(max(MODEL_ORDER - seed.length, 0))
//        val history = "$initValue$seed"
//
//        return ngrams.generateCandidates(languageModel, MODEL_ORDER, history)
//    }

}



