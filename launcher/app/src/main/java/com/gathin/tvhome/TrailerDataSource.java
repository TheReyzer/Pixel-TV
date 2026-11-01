package com.gathin.tvhome;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Serve progressive YouTube streams as one input, using bounded range requests underneath. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class TrailerDataSource implements DataSource {
    private static final int CHUNK_BYTES=512*1024;
    private final DataSource upstream;
    private DataSpec original;
    private boolean chunked;
    private long position,remaining,chunkRemaining;
    TrailerDataSource(DataSource upstream) { this.upstream=upstream; }
    static long contentLength(String url) {
        if (!YoutubeTrailerSource.validStreamUrl(url)) return -1;
        try {
            java.net.URI uri=new java.net.URI(url);
            if (!"/videoplayback".equals(uri.getPath())||uri.getRawQuery()==null) return -1;
            long length=-1;
            for (String part:uri.getRawQuery().split("&")) {
                if (part.startsWith("range=")) return -1;
                if (part.startsWith("clen=")) length=Long.parseLong(part.substring(5));
            }
            return length>0?length:-1;
        } catch (Exception ignored) { return -1; }
    }
    static int chunkLength(long remaining) { return (int)Math.min(CHUNK_BYTES,Math.max(0,remaining)); }
    @Override public void addTransferListener(TransferListener listener) { upstream.addTransferListener(listener); }
    @Override public long open(DataSpec spec) throws IOException {
        original=spec; long length=contentLength(spec.uri.toString());
        chunked=length>0&&spec.httpMethod==DataSpec.HTTP_METHOD_GET;
        if (!chunked) return upstream.open(spec);
        position=spec.position;
        if (position>length) throw new IOException("Outside trailer stream");
        remaining=spec.length==C.LENGTH_UNSET?length-position:Math.min(spec.length,length-position);
        if (remaining>0) openChunk();
        return remaining;
    }
    private void openChunk() throws IOException {
        chunkRemaining=chunkLength(remaining);
        Uri uri=original.uri.buildUpon().appendQueryParameter("range",position+"-"+(position+chunkRemaining-1)).build();
        // The URI selects absolute bytes; the HTTP response itself starts at offset zero.
        upstream.open(original.buildUpon().setUri(uri).setPosition(0).setLength(chunkRemaining).build());
    }
    @Override public int read(byte[] buffer,int offset,int length) throws IOException {
        if (!chunked) return upstream.read(buffer,offset,length);
        if (length==0) return 0;
        if (remaining==0) return C.RESULT_END_OF_INPUT;
        if (chunkRemaining==0) { upstream.close(); openChunk(); }
        int count=upstream.read(buffer,offset,(int)Math.min(length,chunkRemaining));
        if (count==C.RESULT_END_OF_INPUT) throw new java.io.EOFException("Incomplete trailer range");
        position+=count; remaining-=count; chunkRemaining-=count;
        return count;
    }
    @Override public Uri getUri() { return chunked&&original!=null?original.uri:upstream.getUri(); }
    @Override public Map<String,List<String>> getResponseHeaders() { return upstream.getResponseHeaders(); }
    @Override public void close() throws IOException { try { upstream.close(); } finally { original=null; chunked=false; } }
}
