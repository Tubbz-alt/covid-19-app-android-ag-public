package uk.nhs.nhsx.covid19.android.app.util.viewutils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import timber.log.Timber

class CodeInputTextWatcher(
    private val editText: EditText
) : TextWatcher {

    var codeInputRegex: Regex = CODE_INPUT_REGEX.toRegex()

    override fun afterTextChanged(s: Editable) {}

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        editText.removeTextChangedListener(this)
        val cleanString = s.toString().replace(codeInputRegex, "")
        if (s.toString() != cleanString) {
            editText.setText(cleanString)
            try {
                editText.setSelection(editText.text.length)
            } catch (e: IndexOutOfBoundsException) {
                Timber.e(e)
            }
        }
        editText.addTextChangedListener(this)
    }

    companion object {
        private const val CODE_INPUT_REGEX = "[^a-z0-9]"
    }
}
