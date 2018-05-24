package pl.zyper.musiccontrol

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog

class AboutFragment : DialogFragment() {
    companion object {
        const val tag = "dialog_about"
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity!!)
        val inflater = activity!!.layoutInflater

        builder.setView(inflater.inflate(R.layout.about, null))
        return builder.create()
    }
}