package pl.zyper.musiccontrol

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Window
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        with(window) {
            requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
            exitTransition = android.transition.Slide()
        }

        setContentView(R.layout.activity_main)

        supportActionBar?.hide()

        localButton.setOnClickListener {
            MusicListActivity.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        //TODO: add MPD server support
        MusicListActivity.start(this)
    }
}
