package androidx.media3.session

import android.graphics.Bitmap
import android.media.MediaMetadata as FwkMediaMetadata
import android.media.session.MediaSession as FwkMediaSession
import android.media.session.PlaybackState as FwkPlaybackState
import android.os.SystemClock
import androidx.media3.session.legacy.MediaMetadataCompat
import androidx.media3.session.legacy.MediaSessionCompat
import androidx.media3.session.legacy.PlaybackStateCompat
import android.util.Log

/**
 * Bridge between AndroidX Media3 and the Android platform / MediaSessionCompat.
 *
 * CRITICAL FOR COLOROS / REALME UI AQUA DYNAMICS (FLUID CLOUD) STATUS BAR CAPSULE:
 * ColorOS SystemUI (Pantanal) discovers media playback via MediaSessionManager.getActiveSessions(),
 * which strictly requires:
 * 1. session.isActive == true (Media3 NEVER activates this by default).
 * 2. FLAG_HANDLES_TRANSPORT_CONTROLS flag present.
 * 3. PlaybackStateCompat.STATE_PLAYING with standard transport actions for dancing EQ bars.
 * 4. Synchronous MediaMetadataCompat containing the cover art Bitmap.
 */
object MediaSessionBridge {
    private const val TAG = "MediaSessionBridge"

    private val STANDARD_ACTIONS: Long =
        PlaybackStateCompat.ACTION_PLAY or
        PlaybackStateCompat.ACTION_PAUSE or
        PlaybackStateCompat.ACTION_PLAY_PAUSE or
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
        PlaybackStateCompat.ACTION_SEEK_TO or
        PlaybackStateCompat.ACTION_STOP

    fun getSessionCompat(session: MediaSession): MediaSessionCompat? {
        return try {
            session.impl.mediaSessionLegacyStub?.sessionCompat
        } catch (e: Throwable) {
            Log.w(TAG, "Direct sessionCompat access failed, trying reflection", e)
            try {
                val implMethod = MediaSession::class.java.getDeclaredMethod("getImpl")
                implMethod.isAccessible = true
                val impl = implMethod.invoke(session)
                val stubField = impl.javaClass.getDeclaredField("sessionLegacyStub")
                stubField.isAccessible = true
                val stub = stubField.get(impl)
                val compatMethod = stub.javaClass.getDeclaredMethod("getSessionCompat")
                compatMethod.isAccessible = true
                compatMethod.invoke(stub) as? MediaSessionCompat
            } catch (ex: Throwable) {
                Log.e(TAG, "Failed to get sessionCompat via reflection", ex)
                null
            }
        }
    }

    fun getFrameworkMediaSession(session: MediaSession): FwkMediaSession? {
        return try {
            val compat = getSessionCompat(session) ?: return null
            compat.mediaSession as? FwkMediaSession
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get framework MediaSession", e)
            null
        }
    }

    /**
     * Initializes the platform MediaSession with required flags and active state.
     */
    fun setupPlatformSession(session: MediaSession) {
        try {
            val compat = getSessionCompat(session)
            val flags = MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            compat?.setFlags(flags)
            compat?.isActive = true

            val fwk = getFrameworkMediaSession(session)
            fwk?.setFlags(
                FwkMediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                FwkMediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            fwk?.isActive = true
            Log.i(TAG, "Aqua Dynamics: Platform MediaSession initialized & activated successfully (fwk.isActive=${fwk?.isActive})")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to setup platform session", e)
        }
    }

    fun setActive(session: MediaSession, active: Boolean) {
        try {
            val compat = getSessionCompat(session)
            compat?.isActive = active
            val fwk = getFrameworkMediaSession(session)
            fwk?.isActive = active
            Log.d(TAG, "Aqua Dynamics: setActive($active) - fwk.isActive=${fwk?.isActive}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to setActive($active)", e)
        }
    }

    /**
     * Synchronously publishes PlaybackState to both compat and framework session.
     * Required by ColorOS Pantanal to detect STATE_PLAYING and animate the dancing EQ capsule.
     */
    fun updatePlaybackState(
        session: MediaSession,
        isPlaying: Boolean,
        positionMs: Long,
        playbackSpeed: Float = 1.0f
    ) {
        try {
            val state = if (isPlaying) {
                PlaybackStateCompat.STATE_PLAYING
            } else {
                PlaybackStateCompat.STATE_PAUSED
            }
            val speed = if (isPlaying) playbackSpeed else 0f

            val fwkBuilder = FwkPlaybackState.Builder()
                .setState(state, positionMs, speed, SystemClock.elapsedRealtime())
                .setActions(STANDARD_ACTIONS)

            val fwkState = fwkBuilder.build()
            val fwk = getFrameworkMediaSession(session)
            fwk?.setPlaybackState(fwkState)

            val compat = getSessionCompat(session)
            val compatState = PlaybackStateCompat.Builder()
                .setState(state, positionMs, speed, SystemClock.elapsedRealtime())
                .setActions(STANDARD_ACTIONS)
                .build()
            compat?.setPlaybackState(compatState)

            // Keep session active during playing and paused so SystemUI maintains controls
            setActive(session, true)
            Log.d(TAG, "Aqua Dynamics: PlaybackState updated (state=$state, pos=$positionMs)")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update playback state", e)
        }
    }

    /**
     * Synchronously sets Title, Artist, Album, and Cover Art Bitmap on the framework session.
     * Required for ColorOS Aqua Dynamics punch-hole pill and lock screen carousel card.
     */
    fun updateMetadata(
        session: MediaSession,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        artworkBitmap: Bitmap?
    ) {
        try {
            val fwkBuilder = FwkMediaMetadata.Builder()
                .putString(FwkMediaMetadata.METADATA_KEY_TITLE, title)
                .putString(FwkMediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(FwkMediaMetadata.METADATA_KEY_ALBUM, album)
                .putLong(FwkMediaMetadata.METADATA_KEY_DURATION, durationMs)

            artworkBitmap?.let { bmp ->
                fwkBuilder.putBitmap(FwkMediaMetadata.METADATA_KEY_ALBUM_ART, bmp)
                fwkBuilder.putBitmap(FwkMediaMetadata.METADATA_KEY_ART, bmp)
            }

            val fwkMeta = fwkBuilder.build()
            val fwk = getFrameworkMediaSession(session)
            fwk?.setMetadata(fwkMeta)

            val compat = getSessionCompat(session)
            val compatBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)

            artworkBitmap?.let { bmp ->
                compatBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bmp)
                compatBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bmp)
            }
            compat?.setMetadata(compatBuilder.build())

            Log.d(TAG, "Aqua Dynamics: Metadata updated synchronously for '$title' (hasArt=${artworkBitmap != null})")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update metadata", e)
        }
    }
}
