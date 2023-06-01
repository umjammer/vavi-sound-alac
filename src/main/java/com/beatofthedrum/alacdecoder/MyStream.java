/*
 * Copyright (c) 2011 Peter McQuillan
 *
 * All Rights Reserved.
 *
 * Distributed under the BSD Software License (see license.txt)
 */

package com.beatofthedrum.alacdecoder;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.logging.Logger;


class MyStream {

    private static final Logger logger = Logger.getLogger(MyStream.class.getName());

    DataInputStream stream;

    int currentPos = 0;

    private byte[] read_buf = new byte[8];

    MyStream(DataInputStream stream) {
        this.stream = stream;
    }

    /** */
    int position(int pos) {
        return -1;
    }

    /** */
    int position() {
        return this.currentPos;
    }

    /** */
    int isEof() {
        // TODO

        return 0;
    }

    /** */
    void skip(int skip) throws IOException {
        int toskip = skip;
        int bytes_read = 0;

        if (toskip < 0) {
			throw new IllegalArgumentException("stream_skip: request to seek backwards in stream - not supported, sorry");
        }

logger.finer("skip: " + toskip);
        bytes_read = this.stream.skipBytes(toskip);
        this.currentPos = this.currentPos + bytes_read;
    }

    /** */
    int read_uint8() throws IOException {
        byte[] bytebuf = this.read_buf;

        int bytes_read = this.stream.read(bytebuf, 0, 1);
        int v = bytebuf[0] & 0xff;
        this.currentPos = this.currentPos + 1;

        return v;
    }

    /** */
    int read_uint16() throws IOException {
        int v = 0;
        int tmp = 0;
        byte[] bytebuf = this.read_buf;
        int bytes_read = 0;

        bytes_read = this.stream.read(bytebuf, 0, 2);
        this.currentPos = this.currentPos + bytes_read;
        tmp = (bytebuf[0] & 0xff);
        v = tmp << 8;
        tmp = (bytebuf[1] & 0xff);

        v = v | tmp;

        return v;
    }

    /** */
    int read_int16() throws IOException {
        int v = this.stream.readShort();
        this.currentPos = this.currentPos + 2;

        return v;
    }

    /** */
    int read_uint32() throws IOException {
        byte[] bytebuf = this.read_buf;

        int bytes_read = this.stream.read(bytebuf, 0, 4);
        this.currentPos = this.currentPos + bytes_read;
        int tmp = (bytebuf[0] & 0xff);

        int v = tmp << 24;
        tmp = (bytebuf[1] & 0xff);

        v = v | (tmp << 16);
        tmp = (bytebuf[2] & 0xff);

        v = v | (tmp << 8);

        tmp = (bytebuf[3] & 0xff);
        v = v | tmp;

        return v;
    }

    /** */
    int read(int size, byte[] buf, int startPos) throws IOException {
        int bytes_read = 0;

        bytes_read = this.stream.read(buf, startPos, size);
        this.currentPos = this.currentPos + bytes_read;
        return bytes_read;
    }

    /** */
    void read(int size, int[] buf, int startPos) throws IOException {
        byte[] byteBuf = new byte[size];
        int bytes_read = this.read(size, byteBuf, 0);
        for (int i = 0; i < bytes_read; i++) {
            buf[startPos + i] = byteBuf[i];
        }
    }
}
