package pl.zyper.musiccontrol

import java.util.concurrent.TimeUnit

class SongInfo(
        val artist: String,
        val title: String,
        val duration: Long
) {
    companion object {
        fun timeToString(t: Long): String {
            return String.format("%02d:%02d", TimeUnit.MILLISECONDS.toMinutes(t),
                    TimeUnit.MILLISECONDS.toSeconds(t) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(t)))
        }
    }

    fun getTimeString(): String {
        return SongInfo.timeToString(duration)
    }
}
