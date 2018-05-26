package pl.zyper.musiccontrol

import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

class SongManager {

    val files: ArrayList<String> = ArrayList()
    val songs: ArrayList<SongInfo> = ArrayList()

    fun load(path: String, depth: Int) {
        getFilesFromDirectory(path, depth, files)
        getSongsFromFiles(files)
    }

    private fun getFilesFromDirectory(path: String, depth: Int, fileList: ArrayList<String>) {
        val f = File(path)
        if (!f.exists()) return
        if (f.isDirectory && depth > 0) {
            for (i in 0 until f.listFiles().size) {
                getFilesFromDirectory(f.listFiles()[i].absolutePath, depth - 1, fileList)
            }
        } else {
            Log.d("FILES", "file ${f.absolutePath}")
            // TODO: add better file-extension checking
            if (f.extension != "png" && f.extension != "jpg") {
                fileList.add(f.absolutePath)
            }
        }
    }

    private fun getSongsFromFiles(files: List<String>) {
        val metaRetriever = MediaMetadataRetriever()
        for (file in files) {

            metaRetriever.setDataSource(file)
            songs.add(SongInfo(
                    metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            ))
        }
        Log.d("SONG MANAGER", "Metadata extracted from ${songs.size} files.")
    }
}