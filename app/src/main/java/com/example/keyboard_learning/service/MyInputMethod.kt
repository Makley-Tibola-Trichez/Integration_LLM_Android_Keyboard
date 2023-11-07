
package com.example.keyboard_learning.service

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import com.example.keyboard_learning.R
import com.example.keyboard_learning.ml.LLMNextWord
import devandroid.makley.keyboard_api.Keyboard
import devandroid.makley.keyboard_api.KeyboardView
import devandroid.makley.keyboard_api.KeyboardView.OnKeyboardActionListener


class MyInputMethod : InputMethodService(), OnKeyboardActionListener {
    private var candidateView: CandidateView? = null
    private val composing = StringBuilder()
    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var isCaps = false
    private var isAlt = false

    private var completionOn = false
    private var completions: Array<CompletionInfo>? = null

    private lateinit var llm: LLMNextWord

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        llm.onCleared()
    }

    override fun onCreateInputView(): View {

        llm = LLMNextWord(application)

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

        when (primaryCode) {

            Keyboard.KEYCODE_ALT -> {
                isAlt = !isAlt
                val querty = if (isAlt) R.xml.qwerty_alt else R.xml.qwerty

                keyboard = Keyboard(this, querty)
                keyboardView!!.keyboard = keyboard
                keyboardView!!.invalidateAllKeys()
            }
            Keyboard.KEYCODE_DELETE -> currentInputConnection.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_SHIFT -> {
                isCaps = !isCaps
                keyboard!!.isShifted = isCaps
                keyboardView!!.invalidateAllKeys()
            }

            Keyboard.KEYCODE_DONE -> currentInputConnection.sendKeyEvent(
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
                currentInputConnection.commitText(code.toString(), 1)

                val extractedText = currentInputConnection.getExtractedText(ExtractedTextRequest(), 0);

                Log.d("EXTRACTED", "onKey: ${extractedText.text}")
                val extractedTokens = llm.promptToken(extractedText.text.toString())
                Log.d("EXTRACTED", "onKey: $extractedTokens")


            }
        }
    }

    /**
     * Here we can treate if the prediction must be on or not
     */
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        updateCandidates()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        updateCandidates()
        return super.onKeyUp(keyCode, event)
    }
    override fun onText(text: CharSequence) {
        // Does not execute when digits are pressed
    }
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

        val extractedText = currentInputConnection.getExtractedText(ExtractedTextRequest(), 0)

        val currentText = extractedText.text
        val cursorPosition = extractedText.selectionStart

        // If cursor is at the beginning of the text, just add the suggestion
        if (cursorPosition == 0) {
            currentInputConnection.commitText("$suggestion ", 1)
            return
        }

        val previousChar = currentText[cursorPosition - 1]

        // If char before cursor is whitespace, just add the suggestion
        if (previousChar.isWhitespace()) {
            currentInputConnection.commitText("$suggestion ", 1)
            return
        }

        val charsBeforeCursorUntilWhiteSpace = currentText.subSequence(0, cursorPosition).split(" ").last()

        // Delete chars before cursor until whitespace, and add suggestion
        currentInputConnection.deleteSurroundingText(charsBeforeCursorUntilWhiteSpace.length, 0)
        currentInputConnection.commitText("$suggestion ", 1)
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



