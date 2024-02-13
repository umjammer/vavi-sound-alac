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

    private byte[] readBuf = new byte[8];

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
        int bytesRead = 0;

        if (toskip < 0) {
			throw new IllegalArgumentException("skip: request to seek backwards in stream - not supported, sorry");
        }

logger.finer("skip: " + toskip);
        bytesRead = this.stream.skipBytes(toskip);
        this.currentPos = this.currentPos + bytesRead;
    }

    /** */
    int readUint8() throws IOException {
        byte[] byteBuf = this.readBuf;

        int bytesRead = this.stream.read(byteBuf, 0, 1);
        int v = byteBuf[0] & 0xff;
        this.currentPos = this.currentPos + 1;

        return v;
    }

    /** */
    int readUInt16() throws IOException {
        int v = 0;
        int tmp = 0;
        byte[] byteBuf = this.readBuf;
        int bytesRead = 0;

        bytesRead = this.stream.read(byteBuf, 0, 2);
        this.currentPos = this.currentPos + bytesRead;
        tmp = (byteBuf[0] & 0xff);
        v = tmp << 8;
        tmp = (byteBuf[1] & 0xff);

        v = v | tmp;

        return v;
    }

    /** */
    int readInt16() throws IOException {
        int v = this.stream.readShort();
        this.currentPos = this.currentPos + 2;

        return v;
    }

    /** */
    int readUInt32() throws IOException {
        byte[] byteBuf = this.readBuf;

        int bytesRead = this.stream.read(byteBuf, 0, 4);
        this.currentPos = this.currentPos + bytesRead;
        int tmp = (byteBuf[0] & 0xff);

        int v = tmp << 24;
        tmp = (byteBuf[1] & 0xff);

        v = v | (tmp << 16);
        tmp = (byteBuf[2] & 0xff);

        v = v | (tmp << 8);

        tmp = (byteBuf[3] & 0xff);
        v = v | tmp;

        return v;
    }

    /** */
    int read(int size, byte[] buf, int startPos) throws IOException {
        int bytesRead = 0;

        bytesRead = this.stream.read(buf, startPos, size);
        this.currentPos = this.currentPos + bytesRead;
        return bytesRead;
    }

    /** */
    void read(int size, int[] buf, int startPos) throws IOException {
        byte[] byteBuf = new byte[size];
        int bytesRead = this.read(size, byteBuf, 0);
        for (int i = 0; i < bytesRead; i++) {
            buf[startPos + i] = byteBuf[i];
        }
    }
}
