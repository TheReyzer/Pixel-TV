package com.gathin.tvhome;

import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory;
import androidx.media3.exoplayer.upstream.ParsingLoadable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class TrailerPlaylistParserFactory implements HlsPlaylistParserFactory {
    private final DefaultHlsPlaylistParserFactory delegate = new DefaultHlsPlaylistParserFactory();
    private final String originalLanguage;

    TrailerPlaylistParserFactory(String originalLanguage) {
        this.originalLanguage = originalLanguage;
    }

    @Override public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser() {
        ParsingLoadable.Parser<HlsPlaylist> parser = delegate.createPlaylistParser();
        return (uri, input) -> {
            long started = android.os.SystemClock.elapsedRealtime();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 2 * 1024 * 1024) throw new java.io.IOException("Trailer playlist too large");
                bytes.write(buffer, 0, count);
            }
            String filtered = TrailerManifest.filter(bytes.toString("UTF-8"), originalLanguage);
            HlsPlaylist result = parser.parse(uri, new ByteArrayInputStream(filtered.getBytes(StandardCharsets.UTF_8)));
            android.util.Log.d("TVHomeTrailer", "PLAYLIST bytes=" + bytes.size() + "->" + filtered.length()
                    + " parseMs=" + (android.os.SystemClock.elapsedRealtime() - started));
            return result;
        };
    }

    @Override public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser(
            HlsMultivariantPlaylist master, HlsMediaPlaylist previous) {
        return delegate.createPlaylistParser(master, previous);
    }
}
