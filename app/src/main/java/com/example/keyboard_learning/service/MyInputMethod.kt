package com.example.keyboard_learning.service

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import com.example.keyboard_learning.R
import com.example.keyboard_learning.ml.LLMNextWord
import devandroid.makley.keyboard_api.Keyboard
import devandroid.makley.keyboard_api.KeyboardView
import devandroid.makley.keyboard_api.KeyboardView.OnKeyboardActionListener


class MyInputMethod : InputMethodService(), OnKeyboardActionListener {
    private var candidateView: CandidateView? = null
    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var isCaps = false
    private var isAlt = false

    private lateinit var llm: LLMNextWord

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
            Keyboard.KEYCODE_DELETE -> {
                currentInputConnection.deleteSurroundingText(1, 0)
                updateSuggestions()
            }
            Keyboard.KEYCODE_SHIFT -> {
                isCaps = !isCaps
                keyboard!!.isShifted = isCaps
                keyboardView!!.invalidateAllKeys()
            }

            Keyboard.KEYCODE_DONE -> {
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_ENTER
                    )
                )
            }

            else -> {
                var charCode = primaryCode.toChar()

                if (Character.isLetter(charCode) && isCaps) {
                    charCode = charCode.uppercaseChar()
                }
                currentInputConnection.commitText(charCode.toString(), 1)

                updateSuggestions()

            }
        }
    }


    fun updateSuggestions() {
        val extractedText = currentInputConnection.getExtractedText(ExtractedTextRequest(), 0);



        val cursorPosition = extractedText.selectionStart



        if (cursorPosition != 0 && extractedText.text[cursorPosition - 1].isLetterOrDigit()) {

            // get the last word
            val lastWord = extractedText.text.subSequence(0, cursorPosition).split(" ").last()
            // remove the last word from extractedText
            extractedText.text = extractedText.text.subSequence(0, cursorPosition - lastWord.length)

        }

        if (extractedText.text.isNotEmpty()) {
            val extractedTokens = llm.promptToken(extractedText.text.toString())

            candidateView?.updatePredictions(extractedTokens)
        }
    }


    override fun onText(text: CharSequence?) {    }
    override fun onPress(primaryCode: Int) {    }
    override fun onRelease(primaryCode: Int) {    }
    override fun swipeLeft() {    }
    override fun swipeRight() {    }
    override fun swipeDown() {    }
    override fun swipeUp() {    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        setCandidatesViewShown(true)

        candidateView?.updatePredictions()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        candidateView?.updatePredictions()
        return super.onKeyUp(keyCode, event)
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

}



