package pl.zyper.musiccontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.util.Log
import android.widget.Toast
import kotlin.concurrent.thread
import kotlin.math.roundToLong


class MusicService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
    companion object {
        private const val ACTION_PLAY = "pl.zyper.musiccontrol.PLAY"

        fun start(context: Context) {
            Log.d("COMPANION MUSIC SERVICE", "Starting service by $context")
            val intent = Intent(
                    context, MusicService::class.java
            )
            intent.action = ACTION_PLAY
            listener = context as MusicServiceListener
            context.startService(intent)
        }

        fun stop(context: Context) {
            Log.d("COMPANION MUSIC SERVICE", "Stopping service")
            val intent = Intent(
                    context, MusicService::class.java
            )

            if (self != null) {
                self!!.pauseMediaPlayer()
                listener!!.onStatusChanged(false)
                self!!.updateThread = null
                self!!.mediaPlayer!!.release()
                self!!.mediaPlayer = null
            }
            self?.currentSongNumber = -1
            self?.stopSelf()
            listener = null
            self = null
        }

        const val PREF_MUSIC_DIRECTORY = "local_storage_path"
        const val PREF_SCAN_DEPTH = "directory_depth"
        const val SEEK_LENGTH_MS: Long = 10 * 1000
        const val PREF_AUTOPLAY = "song_autoplay"

        const val CHANNEL_ID = "musicplayerpl.zyper.musiccontrol.channel.id123"
        const val NOTIFICATION_ID = 0

        var self: MusicService? = null

        var listener: MusicServiceListener? = null
    }

    private var updateThread: Thread? = null
    private var mediaPlayer: MediaPlayer? = null
    var currentSongNumber: Int = -1
    lateinit var songManager: SongManager

    private var notificationBuilder: NotificationCompat.Builder? = null

    fun setNewListener(l: MusicServiceListener) {
        listener = l
        updateListener(listener)
    }

    override fun onCreate() {
        super.onCreate()
        MusicService.self = this

        // read preferences
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val musicDirectory = preferences.getString(PREF_MUSIC_DIRECTORY, "")
        val depth = preferences.getString(PREF_SCAN_DEPTH, "1").toInt()

        // set up mediaplayer and song manager
        setupMediaPlayer()
        songManager = SongManager()
        thread(start = true) {
            songManager.load(musicDirectory, depth)
            listener?.onSongsLoaded(songManager.songs)
        }

        Log.d("SERVICE", "onCreate")

        // create notification builder
        createNotification()
        //NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notificationBuilder!!.build())
    }

    private fun createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val description = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MusicListActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)


        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
        with(notificationBuilder!!) {
            setSmallIcon(R.drawable.ic_song_play)
            setContentTitle("Music player")
            setContentText("Song")
            priority = NotificationCompat.PRIORITY_DEFAULT
            setContentIntent(pendingIntent)
        }

    }

    private fun updateNotification() {
        if (songManager.songs.size > 0 && currentSongNumber >= 0) {
            if (currentSong() == null) return
            notificationBuilder!!.setContentTitle(currentSong()!!.artist)
            notificationBuilder!!.setContentText(String.format(
                    "%s: %s / %s",
                    currentSong()?.title,
                    SongInfo.timeToString(mediaPlayer?.currentPosition!!.toLong()),
                    currentSong()?.getTimeString()
            ))
            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder!!.build())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        updateThread = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun updateListener(listener: MusicServiceListener?) {
        listener?.onServiceCreated()
        updatePosition()
        mediaPlayer?.isPlaying?.let { listener?.onStatusChanged(it) }

        updateThread = null
        updateThread = thread(start = true) {
            while (true) {
                if (self == null) break
                if (listener == null && mediaPlayer == null) break

                if (listener != null) {
                    if (mediaPlayer?.isPlaying!!) {
                        updatePosition()
                    }
                }
                if (notificationBuilder != null && isPlaying()) {
                    updateNotification()
                }
                Thread.sleep(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SERVICE", "onStartCommand ${intent?.action}")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mediaPlayer?.start()
        updateListener(listener)
    }

    override fun onCompletion(mp: MediaPlayer?) {
        Log.d("MUSIC SERVICE", "Song completed")
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (sharedPrefs.getBoolean(PREF_AUTOPLAY, false)) {
            next()
        } else {
            pauseMediaPlayer()
            listener?.onStatusChanged(false)
            listener?.onPositionChanged(0, 0)
        }
        if (mediaPlayer != null) {
            listener?.onStatusChanged(mediaPlayer!!.isPlaying)
        }
    }

    private fun setupMediaPlayer() {
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnPreparedListener(this)

        mediaPlayer!!.setOnErrorListener { _, what, _ ->
            Toast.makeText(this, getString(R.string.playback_error) + what + "!", Toast.LENGTH_SHORT).show()
            listener?.onStatusChanged(false)
            true
        }

        mediaPlayer?.setOnCompletionListener(this)
    }

    // TODO: refactor songManager code
    private fun play(filePath: String) {
        if (songManager.songs.size <= 0) return

        mediaPlayer?.setDataSource(filePath)
        mediaPlayer?.prepareAsync()
    }

    fun startMediaPlayer() {
        if (currentSongNumber < 0) return
        if (currentSongNumber >= songManager.songs.size) return

        mediaPlayer?.reset()
        play(songManager.files[currentSongNumber])
    }

    fun pauseMediaPlayer() {
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
        }
        updateListener(listener)
    }

    fun toggleMediaPlayer() {
        if (mediaPlayer == null) return
        if (currentSongNumber < 0 && songManager.songs.size > 0) {
            currentSongNumber = 0
            startMediaPlayer()
        }

        if (mediaPlayer?.isPlaying!!) {
            mediaPlayer!!.pause()
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        } else {
            mediaPlayer!!.start()
        }
        listener?.onStatusChanged(mediaPlayer?.isPlaying!!)
        updatePosition()
    }

    fun seek(delta: Long) {
        seekTo(mediaPlayer!!.currentPosition + delta)
    }

    fun seekToPercentage(p: Double) {
        seekTo((p * mediaPlayer?.duration!!).roundToLong())
    }

    // actual seek method
    private fun seekTo(pos: Long) {
        mediaPlayer?.seekTo(pos, MediaPlayer.SEEK_CLOSEST)
        updatePosition()
    }

    fun next() {
        currentSongNumber++
        if (currentSongNumber > songManager.songs.size) currentSongNumber = 0
        startMediaPlayer()
    }

    fun previous() {
        currentSongNumber--
        if (currentSongNumber < 0) currentSongNumber = songManager.songs.size - 1
        if (currentSongNumber < 0) currentSongNumber = 0
        startMediaPlayer()
    }

    fun currentSong(): SongInfo? {
        if (songManager.songs.size <= 0) return null
        if (currentSongNumber < 0) return null
        if (currentSongNumber >= songManager.songs.size) return null
        return songManager.songs[currentSongNumber]
    }

    fun isPlaying(): Boolean {
        if (mediaPlayer == null) return false
        return mediaPlayer?.isPlaying!!
    }

    private fun updatePosition() {
        if (currentSongNumber >= 0) {
            listener?.onPositionChanged(
                    mediaPlayer?.currentPosition!!.toLong(),
                    mediaPlayer?.duration!!.toLong()

            )
        }
    }

    interface MusicServiceListener {
        fun onServiceCreated()
        fun onStatusChanged(playing: Boolean)
        fun onPositionChanged(position: Long, duration: Long)
        fun onSongsLoaded(songs: List<SongInfo>, listPosition: Int = 0)
    }

}