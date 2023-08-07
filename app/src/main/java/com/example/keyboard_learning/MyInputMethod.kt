
package com.example.keyboard_learning

import devandroid.makley.keyboard_api.Keyboard;
import devandroid.makley.keyboard_api.KeyboardView;
import devandroid.makley.keyboard_api.KeyboardView.OnKeyboardActionListener
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.view.KeyEvent
import android.view.View


class MyInputMethod : InputMethodService(), OnKeyboardActionListener {
    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var isCaps = false
    private var isAlt = false
    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard, null) as KeyboardView?

        try {


        keyboard = Keyboard(this, R.xml.qwerty)
        keyboardView!!.keyboard = keyboard
        keyboardView!!.setOnKeyboardActionListener(this)
        return keyboardView!!
        } catch (e: Exception) {
            e.printStackTrace()
            return keyboardView as View
        }
    }



    override fun onKey(primaryCode: Int, keyCodes: IntArray) {

        try {



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
        } catch (e: Exception) {
            e.printStackTrace()
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


    override fun onText(text: CharSequence) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

}



