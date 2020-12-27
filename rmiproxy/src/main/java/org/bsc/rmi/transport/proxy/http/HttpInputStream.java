package org.bsc.rmi.transport.proxy.http;

import lombok.extern.java.Log;

import java.io.*;
import java.util.logging.Level;

import static java.lang.String.format;

/**
 * The HttpInputStream class assists the HttpSendSocket and HttpReceiveSocket
 * classes by filtering out the header for the message as well as any
 * data after its proper content length.
 */
@Log
class HttpInputStream extends FilterInputStream {

    /** bytes remaining to be read from proper content of message */
    protected int bytesLeft;

    /** bytes remaining to be read at time of last mark */
    protected int bytesLeftAtMark;

    /**
     * Create new filter on a given input stream.
     * @param in the InputStream to filter from
     */
    public HttpInputStream(InputStream in) throws IOException
    {
        super(in);

        if (in.markSupported())
            in.mark(0); // prevent resetting back to old marks

        // pull out header, looking for content length

        DataInputStream dis = new DataInputStream(in);
        String key = "Content-length:".toLowerCase();
        boolean contentLengthFound = false;
        String line;
        do {
            line = dis.readLine();

            if( log.isLoggable(Level.FINE)) {
                log.fine(format("received header line: \"%s\"", line ));
            }

            if (line == null)
                throw new EOFException();

            if (line.toLowerCase().startsWith(key)) {
                if (contentLengthFound) {
                    throw new IOException(
                            "Multiple Content-length entries found.");
                } else {
                    bytesLeft =
                        Integer.parseInt(line.substring(key.length()).trim());
                    contentLengthFound = true;
                }
            }

            // The idea here is to go past the first blank line.
            // Some DataInputStream.readLine() documentation specifies that
            // it does include the line-terminating character(s) in the
            // returned string, but it actually doesn't, so we'll cover
            // all cases here...
        } while ((line.length() != 0) &&
                 (line.charAt(0) != '\r') && (line.charAt(0) != '\n'));

        if (!contentLengthFound || bytesLeft < 0) {
            // This really shouldn't happen, but if it does, shoud we fail??
            // For now, just give up and let a whole lot of bytes through...
            bytesLeft = Integer.MAX_VALUE;
        }
        bytesLeftAtMark = bytesLeft;

        if( log.isLoggable(Level.FINE)) {
            log.fine(format("content length: %d", bytesLeft));
        }
    }

    /**
     * Returns the number of bytes that can be read with blocking.
     * Make sure that this does not exceed the number of bytes remaining
     * in the proper content of the message.
     */
    public int available() throws IOException
    {
        int bytesAvailable = in.available();
        if (bytesAvailable > bytesLeft)
            bytesAvailable = bytesLeft;

        return bytesAvailable;
    }

    /**
     * Read a byte of data from the stream.  Make sure that one is available
     * from the proper content of the message, else -1 is returned to
     * indicate to the user that the end of the stream has been reached.
     */
    public int read() throws IOException
    {
        if (bytesLeft > 0) {
            int data = in.read();
            if (data != -1)
                -- bytesLeft;

            if( log.isLoggable(Level.FINE)) {
                log.fine(
                   "received byte: '" +
                    ((data & 0x7F) < ' ' ? " " : String.valueOf((char) data)) +
                    "' " + data);
            }

            return data;
        }
        else {
            log.fine("read past content length");

            return -1;
        }
    }

    public int read(byte b[], int off, int len) throws IOException
    {
        if (bytesLeft == 0 && len > 0) {
            log.fine("read past content length");

            return -1;
        }
        if (len > bytesLeft)
            len = bytesLeft;
        int bytesRead = in.read(b, off, len);
        bytesLeft -= bytesRead;

        if( log.isLoggable(Level.FINE)) {
            log.fine("read " + bytesRead + " bytes, " + bytesLeft + " remaining");
        }

        return bytesRead;
    }

    /**
     * Mark the current position in the stream (for future calls to reset).
     * Remember where we are within the proper content of the message, so
     * that a reset method call can recreate our state properly.
     * @param readlimit how many bytes can be read before mark becomes invalid
     */
    public void mark(int readlimit)
    {
        in.mark(readlimit);
        if (in.markSupported())
            bytesLeftAtMark = bytesLeft;
    }

    /**
     * Repositions the stream to the last marked position.  Make sure to
     * adjust our position within the proper content accordingly.
     */
    public void reset() throws IOException
    {
        in.reset();
        bytesLeft = bytesLeftAtMark;
    }

    /**
     * Skips bytes of the stream.  Make sure to adjust our
     * position within the proper content accordingly.
     * @param n number of bytes to be skipped
     */
    public long skip(long n) throws IOException
    {
        if (n > bytesLeft)
            n = bytesLeft;
        long bytesSkipped = in.skip(n);
        bytesLeft -= bytesSkipped;
        return bytesSkipped;
    }
}
