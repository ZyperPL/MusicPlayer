package pl.zyper.musiccontrol

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_music_list.*
import kotlinx.android.synthetic.main.entry.view.*
import kotlin.math.roundToInt

class MusicListActivity : AppCompatActivity(), SeekBar.OnSeekBarChangeListener, MusicService.MusicServiceListener {

    companion object {
        fun start(context: Context) {
            val intent = Intent(
                    context, MusicListActivity::class.java
            )
            context.startActivity(intent)
        }

        private const val PREF_NO_SEEK_BUTTONS = "no_seek_buttons"
        private const val PREF_NO_BACKGROUND_SERVICE = "no_background_service"
        private const val SONGS_LOADED = "are_songs_loaded"
        private const val SONGS_POSITION = "song_position"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_list)

        title = getString(R.string.music)

        // reading recyclerview scroll position
        if (savedInstanceState != null && MusicService.self != null) {
            if (savedInstanceState.getBoolean(SONGS_LOADED, true)) {
                onSongsLoaded(
                        MusicService.self!!.songManager.songs,
                        savedInstanceState.getInt(SONGS_POSITION, 0)
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // saving recyclerview scroll position
        outState.putBoolean(SONGS_LOADED, MusicService.self!!.songManager.songs.size > 0)
        if (songList.layoutManager != null) {
            outState.putInt(SONGS_POSITION, (songList.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
        }

        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()

        // hiding seek buttons if preferred
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        toggleSeekButtons(sharedPrefs.getBoolean(PREF_NO_SEEK_BUTTONS, false))

        // starting service and setting title and seekbar
        Log.d("MUSIC LIST", "Service is ${MusicService.self}")
        if (MusicService.self == null) {
            currentTitle.text = ""
            currentTimeLeft.text = ""
            updateSeekbar(0.0)
            MusicService.start(this)
        } else {
            MusicService.self!!.setNewListener(this)
            onSongsLoaded(MusicService.self!!.songManager.songs, 0)
        }

    }


    override fun onStatusChanged(playing: Boolean) {
        // changing playbutton and songlist
        runOnUiThread {
            if (playing) {
                playButton.setImageDrawable(getDrawable(R.drawable.ic_pause))
            } else {
                playButton.setImageDrawable(getDrawable(R.drawable.ic_play))
            }

            val service = MusicService.self
            val song = service?.currentSong()
            if (song != null) {
                currentTitle.text = song.title
            }

            if (songList.adapter != null) {
                songList.adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onPositionChanged(position: Long, duration: Long) {
        // updating seekbar and song title
        runOnUiThread {
            val percentage: Double = position.toDouble() / duration.toDouble() * 100
            updateSeekbar(percentage)

            val service = MusicService.self
            val song = service?.currentSong()
            if (song != null) {
                currentTitle.text = service.currentSong()?.title
                currentTimeLeft.text = String.format(
                        "%s / %s",
                        SongInfo.timeToString(position),
                        song.getTimeString()
                )
            }
        }
    }

    override fun onSongsLoaded(songs: List<SongInfo>, listPosition: Int) {
        // creating recyclerview and scrolling to wanted position
        Log.d("MUSIC LIST", "onSongsLoaded ${songs.size} $listPosition")
        if (songList.adapter != null && songList.adapter.itemCount > 0) return
        runOnUiThread {
            val layoutManager = LinearLayoutManager(this)
            layoutManager.orientation = LinearLayoutManager.VERTICAL
            songList.layoutManager = layoutManager
            songList.adapter = SongAdapter(songs)
            songList.invalidate()

            layoutManager.scrollToPosition(listPosition)
        }
    }


    private fun toggleSeekButtons(hide: Boolean) {
        // hiding/showing seek buttons if preferred
        if (hide) {
            with(forwardButton) {
                visibility = View.INVISIBLE
                (layoutParams as LinearLayout.LayoutParams).weight = 0.0F
            }
            with(backwardButton) {
                visibility = View.INVISIBLE
                (layoutParams as LinearLayout.LayoutParams).weight = 0.0F
            }
        } else {
            with(forwardButton) {
                visibility = View.VISIBLE
                (layoutParams as LinearLayout.LayoutParams).weight = 1.0F
            }
            with(backwardButton) {
                visibility = View.VISIBLE
                (layoutParams as LinearLayout.LayoutParams).weight = 1.0F
            }
        }
        linearLayout.requestLayout()
    }

    override fun onServiceCreated() {
        // setting buttons listeners
        val service = MusicService.self

        playButton.setOnClickListener { service?.toggleMediaPlayer() }
        seekBar.setOnSeekBarChangeListener(this)

        forwardButton.setOnClickListener { service?.seek(MusicService.SEEK_LENGTH_MS) }
        backwardButton.setOnClickListener { service?.seek(-MusicService.SEEK_LENGTH_MS) }

        nextButton.setOnClickListener { service?.next() }
        previousButton.setOnClickListener { service?.previous() }
    }

    override fun onStop() {
        // stopping service if preferred
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (sharedPrefs.getBoolean(PREF_NO_BACKGROUND_SERVICE, false)) {
            MusicService.stop(this)
        }

        super.onStop()
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        // seek song if user touched seekbar
        if (fromUser) {
            System.out.printf("Progress: %d\n", progress)
            val pos: Double = (progress.toLong() / 100.0)
            MusicService.self?.seekToPercentage(pos)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {}

    private fun updateSeekbar(percentage: Double) {
        seekBar.progress = percentage.roundToInt()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.music_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.about -> {
                AboutFragment().show(supportFragmentManager, AboutFragment.tag)
                return true
            }
            R.id.settings -> {
                SettingsActivity.start(this)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        Log.d("MUSIC LIST", "onBackPressed")
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    class SongAdapter(private val songs: List<SongInfo>) : RecyclerView.Adapter<SongAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.entry, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int {
            return songs.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.parent.findViewById<TextView>(R.id.title).text = songs[position].title
            holder.parent.findViewById<TextView>(R.id.artist).text = songs[position].artist
            holder.parent.findViewById<TextView>(R.id.duration).text = songs[position].getTimeString()

            with(holder.parent.playSongButton)
            {
                setOnClickListener {
                    if (MusicService.self?.currentSongNumber != position) {
                        MusicService.self?.currentSongNumber = position
                        Log.d("MUSIC LIST", "Starting new song no. $position")
                        MusicService.self?.startMediaPlayer()
                    } else {
                        MusicService.self?.toggleMediaPlayer()
                    }
                    notifyDataSetChanged()
                }
                setImageDrawable(context.getDrawable(R.drawable.ic_song_play))

                if (MusicService.self?.currentSongNumber == position && MusicService.self?.isPlaying()!!) {
                    setImageDrawable(context.getDrawable(R.drawable.ic_song_pause))
                }
            }
        }

        class ViewHolder(val parent: View) : RecyclerView.ViewHolder(parent)

    }
}
