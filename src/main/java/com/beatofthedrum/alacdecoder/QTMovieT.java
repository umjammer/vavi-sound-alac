/*
 * Copyright (c) 2011 Peter McQuillan
 *
 * All Rights Reserved.
 *
 * Distributed under the BSD Software License (see license.txt)  
 */

package com.beatofthedrum.alacdecoder;

import java.io.IOException;
import java.util.logging.Logger;


class QTMovieT {

    private static final Logger logger = Logger.getLogger(QTMovieT.class.getName());

    /** */
    MyStream qtStream;

    /** */
    private DemuxResT res;

    /** */
    int savedMDatPos;

    /** */
    public QTMovieT(java.io.DataInputStream file) {
        savedMDatPos = 0;
        qtStream = new MyStream(file);
    }

    /** */
    private static int makeFourCC32(int ch0, int ch1, int ch2, int ch3) {

        int retVal = ch0 << 24;
        int tmp = ch1;

        retVal = retVal | (tmp << 16);
        tmp = ch2;

        retVal = retVal | (tmp << 8);
        tmp = ch3;

        retVal = retVal | tmp;

        return retVal;
    }

    /** */
    private static int makeFourCC(int ch0, int ch1, int ch2, int ch3) {
        return (ch0 << 24) | (ch1 << 16) | (ch2 << 8) | ch3;
    }

    /** */
    static String splitFourCC(int code) {
        char c1 = (char) ((code >> 24) & 0xFF);
        char c2 = (char) ((code >> 16) & 0xFF);
        char c3 = (char) ((code >> 8) & 0xFF);
        char c4 = (char) (code & 0xFF);
        String retstr = c1 + " " + c2 + " " + c3 + " " + c4;

        return retstr;
    }

    /**
     * @return as follows
     * 1 - all ok
     * 2 - do not have valid saved mdat pos
     * 3 - have valid saved mdat pos, but cannot seek there - need to close/reopen stream
     */
    int setSavedMDat() {

        if (this.savedMDatPos == -1) {
            logger.fine("stream contains mdat before moov but is not seekable");
            return 2;
        }

logger.finer("savedMDatPos: " + savedMDatPos);
        if (this.qtStream.position(this.savedMDatPos) != 0) {
            return 3;
        }

        return 1;
    }

    /** */
    void readChunkMDat(int chunkLen, int skipMDat) throws IOException {
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        if (sizeRemaining == 0)
            return;

        this.res.mdatLen = sizeRemaining;
        if (skipMDat != 0) {
            this.savedMDatPos = this.qtStream.position();

            this.qtStream.skip(sizeRemaining);
        }
    }

    /** 'moov' movie atom - contains other atoms */
    int readChunkMoov(int chunkLen) throws IOException {
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        while (sizeRemaining != 0) {
            int subChunkLen;
            int subChunkId = 0;

            try {
                subChunkLen = this.qtStream.readUInt32();
            } catch (IOException e) {
                logger.fine("(readChunkMoov) error reading subChunkLen - possibly number too large");
                subChunkLen = 0;
            }

            if (subChunkLen <= 1 || subChunkLen > sizeRemaining) {
                logger.fine("strange size for chunk inside moov");
                return 0;
            }

            subChunkId = this.qtStream.readUInt32();

            if (subChunkId == makeFourCC32(109, 118, 104, 100)) { // fourcc equals mvhd
                readChunkMvhd(subChunkLen);
            } else if (subChunkId == makeFourCC32(116, 114, 97, 107)) { // fourcc equals trak
                if (readChunkTrak(subChunkLen) == 0)
                    return 0;
            } else if (subChunkId == makeFourCC32(117, 100, 116, 97)) { // fourcc equals udta
                readChunkUdta(subChunkLen);
            } else if (subChunkId == makeFourCC32(101, 108, 115, 116)) { // fourcc equals elst
                readChunkElst(subChunkLen);
            } else if (subChunkId == makeFourCC32(105, 111, 100, 115)) { // fourcc equals iods
                readChunkIods(subChunkLen);
            } else if (subChunkId == makeFourCC32(102, 114, 101, 101)) { // fourcc equals free
                this.qtStream.skip(subChunkLen - 8); // FIXME not 8
            } else {
                logger.fine("(moov) unknown chunk id: " + splitFourCC(subChunkId));
                return 0;
            }

            sizeRemaining -= subChunkLen;
        }

        return 1;
    }

    /** 'iods' */
    void readChunkIods(int chunkLen) throws IOException {
        // don't need anything from here atm, skip
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        this.qtStream.skip(sizeRemaining);
    }

    /** 'udta' user data.. contains tag info */
    void readChunkUdta(int chunkLen) throws IOException {
        // don't need anything from here atm, skip
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        this.qtStream.skip(sizeRemaining);
    }

    /** 'mvhd' movie header atom */
    void readChunkMvhd(int chunkLen) throws IOException {
        // don't need anything from here atm, skip
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        this.qtStream.skip(sizeRemaining);
    }

    /** 'trak' - a movie track - contains other atoms */
    int readChunkTrak(int chunkLen) throws IOException {
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        while (sizeRemaining != 0) {
            int subChunkLen;
            int subChunkId = 0;

            try {
                subChunkLen = this.qtStream.readUInt32();
            } catch (IOException e) {
                logger.fine("(readChunkTrak) error reading subChunkLen - possibly number too large");
                subChunkLen = 0;
            }

            if (subChunkLen <= 1 || subChunkLen > sizeRemaining) {
                logger.fine("strange size for chunk inside trak");
                return 0;
            }

            subChunkId = this.qtStream.readUInt32();

            if (subChunkId == makeFourCC32(116, 107, 104, 100)) { // fourcc equals tkhd
                readChunkTkhd(subChunkLen);
            } else if (subChunkId == makeFourCC32(109, 100, 105, 97)) { // fourcc equals mdia
                if (readChunkMdia(subChunkLen) == 0)
                    return 0;
            } else if (subChunkId == makeFourCC32(101, 100, 116, 115)) { // fourcc equals edts
                readChunkEdts(subChunkLen);
            } else {
                logger.fine("(trak) unknown chunk id: " + splitFourCC(subChunkId));
                return 0;
            }

            sizeRemaining -= subChunkLen;
        }

        return 1;
    }

    int readChunkMdia(int chunkLen) throws IOException {
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        while (sizeRemaining != 0) {
            int subChunkLen;
            int subChunkId = 0;

            try {
                subChunkLen = this.qtStream.readUInt32();
            } catch (IOException e) {
                logger.fine("(readChunkMdia) error reading subChunkLen - possibly number too large");
                subChunkLen = 0;
            }

            if (subChunkLen <= 1 || subChunkLen > sizeRemaining) {
                logger.fine("strange size for chunk inside mdia\n");
                return 0;
            }

            subChunkId = this.qtStream.readUInt32();

            if (subChunkId == makeFourCC32(109, 100, 104, 100)) { // fourcc equals mdhd
                readChunkMdhd(subChunkLen);
            } else if (subChunkId == makeFourCC32(104, 100, 108, 114)) { // fourcc equals hdlr
                readChunkHdlr(subChunkLen);
            } else if (subChunkId == makeFourCC32(109, 105, 110, 102)) { // fourcc equals minf
                if (readChunkMinf(subChunkLen) == 0)
                    return 0;
            } else {
                logger.fine("(mdia) unknown chunk id: " + splitFourCC(subChunkId));
                return 0;
            }

            sizeRemaining -= subChunkLen;
        }

        return 1;
    }

    /** */
    int readChunkMinf(int chunkLen) throws IOException {
        int dinfSize;
        int stblSize;
        int sizeRemaining = chunkLen - 8; // FIXME WRONG
        int mediaInfoSize;

        // SOUND HEADER CHUNK

        try {
            mediaInfoSize = this.qtStream.readUInt32();
        } catch (IOException e) {
            logger.fine("(readChunkMinf) error reading mediaInfoSize - possibly number too large");
            mediaInfoSize = 0;
        }

        if (mediaInfoSize != 16) {
            logger.fine("unexpected size in media info\n");
            return 0;
        }
        if (this.qtStream.readUInt32() != makeFourCC32(115, 109, 104, 100)) { // "smhd" ascii values
            logger.fine("not a sound header! can't handle this.");
            return 0;
        }
        // now skip the rest
        this.qtStream.skip(16 - 8);
        sizeRemaining -= 16;

        //

        // DINF CHUNK
        try {
            dinfSize = this.qtStream.readUInt32();
        } catch (IOException e) {
            logger.fine("(readChunkMinf) error reading dinfSize - possibly number too large");
            dinfSize = 0;
        }

        if (this.qtStream.readUInt32() != makeFourCC32(100, 105, 110, 102)) { // "dinf" ascii values
            logger.fine("expected dinf, didn't get it.");
            return 0;
        }
        // skip it
        this.qtStream.skip(dinfSize - 8);
        sizeRemaining -= dinfSize;

        //

        // SAMPLE TABLE
        try {
            stblSize = (this.qtStream.readUInt32());
        } catch (Exception e) {
            logger.fine("(readChunkMinf) error reading stblSize - possibly number too large");
            stblSize = 0;
        }

        if (this.qtStream.readUInt32() != makeFourCC32(115, 116, 98, 108)) { // "stbl" ascii values
            logger.fine("expected stbl, didn't get it.");
            return 0;
        }
        if (readChunkStbl(stblSize) == 0)
            return 0;
        sizeRemaining -= stblSize;

        if (sizeRemaining != 0) {
            logger.fine("(readChunkMinf) - size remaining?");
            this.qtStream.skip(sizeRemaining);
        }

        return 1;
    }

    /**
     * sample to chunk box
     */
    private void readChunkStsc(int subChunkLen) throws IOException {
        // skip header and size
        MyStream stream = this.qtStream;
        // skip version and other junk
        stream.skip(4);
        int numEntries = stream.readUInt32();
        this.res.stsc = new DemuxResT.ChunkInfo[numEntries];
        for (int i = 0; i < numEntries; i++) {
            DemuxResT.ChunkInfo entry = new DemuxResT.ChunkInfo();
            entry.firstChunk = stream.readUInt32();
            entry.samplesPerChunk = stream.readUInt32();
            entry.sampleDescIndex = stream.readUInt32();
            this.res.stsc[i] = entry;
        }
    }

    /**
     * chunk to offset box
     */
    private void readChunkStco(int subChunkLen) throws IOException {
        // skip header and size
        MyStream stream = this.qtStream;
        stream.skip(4);

        int numEntries = stream.readUInt32();

        this.res.stco = new int[numEntries];
        for (int i = 0; i < numEntries; i++) {
            this.res.stco[i] = stream.readUInt32();
        }
    }

    int readChunkStbl(int chunkLen) throws IOException {
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        while (sizeRemaining != 0) {
            int subChunkLen;
            int subChunkId = 0;

            try {
                subChunkLen = this.qtStream.readUInt32();
            } catch (IOException e) {
                logger.fine("(readChunkStbl) error reading subChunkLen - possibly number too large");
                subChunkLen = 0;
            }

            if (subChunkLen <= 1 || subChunkLen > sizeRemaining) {
                logger.fine("strange size for chunk inside stbl " + subChunkLen + " (remaining: " + sizeRemaining + ")");
                return 0;
            }

            subChunkId = this.qtStream.readUInt32();

            if (subChunkId == makeFourCC32(115, 116, 115, 100)) { // fourcc equals stsd
                if (readChunkStsd(subChunkLen) == 0)
                    return 0;
            } else if (subChunkId == makeFourCC32(115, 116, 116, 115)) { // fourcc equals stts
                readChunkStts(subChunkLen);
            } else if (subChunkId == makeFourCC32(115, 116, 115, 122)) { // fourcc equals stsz
                readChunkStsz(subChunkLen);
            } else if (subChunkId == makeFourCC32(115, 116, 115, 99)) { // fourcc equals stsc
                readChunkStsc(subChunkLen);
            } else if (subChunkId == makeFourCC32(115, 116, 99, 111)) { // fourcc equals stco
                readChunkStco(subChunkLen);
            } else {
                logger.fine("(stbl) unknown chunk id: " + splitFourCC(subChunkId));
                return 0;
            }

            sizeRemaining -= subChunkLen;
        }

        return 1;
    }

    /** */
    void readChunkStsz(int chunkLen) throws IOException {
        int numEntries = 0;
        int uniformSize = 0;
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        // version
        this.qtStream.readUint8();
        sizeRemaining -= 1;
        // flags
        this.qtStream.readUint8();
        this.qtStream.readUint8();
        this.qtStream.readUint8();
        sizeRemaining -= 3;

        // default sample size
        uniformSize = (this.qtStream.readUInt32());
        if (uniformSize != 0) {
            // Normally files have intiable sample sizes, this handles the case where
			// they are all the same size

            int uniformNum = 0;

            uniformNum = (this.qtStream.readUInt32());

            this.res.sampleByteSize = new int[uniformNum];

            for (int i = 0; i < uniformNum; i++) {
                this.res.sampleByteSize[i] = uniformSize;
            }
            sizeRemaining -= 4;
            return;
        }
        sizeRemaining -= 4;

        try {
            numEntries = this.qtStream.readUInt32();
        } catch (IOException e) {
            logger.fine("(readChunkStsz) error reading numEntries - possibly number too large");
            numEntries = 0;
        }

        sizeRemaining -= 4;

        this.res.sampleByteSize = new int[numEntries];

        for (int i = 0; i < numEntries; i++) {
            this.res.sampleByteSize[i] = (this.qtStream.readUInt32());

            sizeRemaining -= 4;
        }

        if (sizeRemaining != 0) {
            logger.fine("(readChunkStsz) size remaining?");
            this.qtStream.skip(sizeRemaining);
        }
    }

    void readChunkStts(int chunkLen) throws IOException {
        int numentries = 0;
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        // version
        this.qtStream.readUint8();
        sizeRemaining -= 1;
        // flags
        this.qtStream.readUint8();
        this.qtStream.readUint8();
        this.qtStream.readUint8();
        sizeRemaining -= 3;

        try {
            numentries = this.qtStream.readUInt32();
        } catch (IOException e) {
            logger.fine("(readChunkStsz) error reading numentries - possibly number too large");
            numentries = 0;
        }

        sizeRemaining -= 4;

        this.res.numTimeToSamples = numentries;

        for (int i = 0; i < numentries; i++) {
            this.res.timeToSample[i].sampleCount = this.qtStream.readUInt32();
            this.res.timeToSample[i].sampleDuration = this.qtStream.readUInt32();
            sizeRemaining -= 8;
        }

        if (sizeRemaining != 0) {
            logger.fine("(readChunkStsz) size remaining?");
            this.qtStream.skip(sizeRemaining);
        }
    }

    int readChunkStsd(int chunkLen) throws IOException {
        int numentries = 0;
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        // version
        this.qtStream.readUint8();
        sizeRemaining -= 1;
        // flags
        this.qtStream.readUint8();
        this.qtStream.readUint8();
        this.qtStream.readUint8();
        sizeRemaining -= 3;

        try {
            numentries = this.qtStream.readUInt32();
        } catch (IOException e) {
            logger.fine("(readChunkStsd) error reading numentries - possibly number too large");
            numentries = 0;
        }

        sizeRemaining -= 4;

        if (numentries != 1) {
            logger.fine("only expecting one entry in sample description atom!");
            return 0;
        }

        for (int i = 0; i < numentries; i++) {
            // parse the file atom contained within the stsd atom
            int entrySize;
            int version;

            int entryRemaining;

            entrySize = this.qtStream.readUInt32();
            this.res.format = this.qtStream.readUInt32();
            entryRemaining = entrySize;
            entryRemaining -= 8;

            if (this.res.format != makeFourCC32(97, 108, 97, 99)) { // "file" ascii values
                logger.fine("(readChunkStsd) error reading description atom - expecting file, got " + splitFourCC(this.res.format));
                return 0;
            }

            // sound info:

            this.qtStream.skip(6); // reserved
            entryRemaining -= 6;

            version = this.qtStream.readUInt16();

            if (version != 1)
                logger.fine("unknown version??");
            entryRemaining -= 2;

            // revision level
            this.qtStream.readUInt16();
            // vendor
            this.qtStream.readUInt32();
            entryRemaining -= 6;

            // EH?? spec doesn't say there's an extra 16 bits here... but there is!
            this.qtStream.readUInt16();
            entryRemaining -= 2;

            // skip 4 - this is the top level num of channels and bits per sample
            this.qtStream.skip(4);
            entryRemaining -= 4;

            // compression id
            this.qtStream.readUInt16();
            // packet size
            this.qtStream.readUInt16();
            entryRemaining -= 4;

            // skip 4 - this is the top level sample rate
            this.qtStream.skip(4);
            entryRemaining -= 4;

            // remaining is codec data

            // 12 = audio format atom, 8 = padding
            this.res.codecDataLen = entryRemaining + 12 + 8;

            if (this.res.codecDataLen > this.res.codecData.length) {
                logger.fine("(readChunkStsd) unexpected codec data length read from atom " + this.res.codecDataLen);
                return 0;
            }

            for (int count = 0; count < this.res.codecDataLen; count++) {
                this.res.codecData[count] = 0;
            }

            // audio format atom
            this.res.codecData[0] = 0x0c000000;
            this.res.codecData[1] = makeFourCC(97, 109, 114, 102); // "amrf" ascii values
            this.res.codecData[2] = makeFourCC(99, 97, 108, 97); // "cala" ascii values

            this.qtStream.read(entryRemaining, this.res.codecData, 12); // codecData buffer should be +12
            entryRemaining -= entryRemaining;

            // We need to read the bits per sample, number of channels and sample rate from the codec data i.e. the file atom within
            // the stsd atom the 'file' atom contains a number of pieces of information which we can skip just now, its processed later
            // in the alac_set_info() method. This atom contains the following information
            //
            // samples_per_frame
            // compatible version
            // bits per sample
            // history multiplier
            // initial history
            // maximum K
            // channels
            // max run
            // max coded frame size
            // bitrate
            // sample rate
            int ptrIndex = 29; // position of bits per sample

            this.res.sampleSize = (this.res.codecData[ptrIndex] & 0xff);

            ptrIndex = 33; // position of num of channels

            this.res.numChannels = (this.res.codecData[ptrIndex] & 0xff);

            ptrIndex = 44; // position of sample rate within codec data buffer

            this.res.sampleRate = (((this.res.codecData[ptrIndex] & 0xff) << 24) | ((this.res.codecData[ptrIndex + 1] & 0xff) << 16) | ((this.res.codecData[ptrIndex + 2] & 0xff) << 8) | (this.res.codecData[ptrIndex + 3] & 0xff));

            if (entryRemaining != 0) // was comparing to null
                this.qtStream.skip(entryRemaining);

            this.res.formatRead = 1;
            if (this.res.format != makeFourCC32(97, 108, 97, 99)) { // "file" ascii values
                return 0;
            }
        }

        return 1;
    }

    /** media handler inside mdia */
    void readChunkHdlr(int chunkLen) throws IOException {
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        // version
        this.qtStream.readUint8();
        sizeRemaining -= 1;
        // flags
        this.qtStream.readUint8();
        this.qtStream.readUint8();
        this.qtStream.readUint8();
        sizeRemaining -= 3;

        // component type
        int compType = this.qtStream.readUInt32();
        int compSubType = this.qtStream.readUInt32();
        sizeRemaining -= 8;

        // component manufacturer
        this.qtStream.readUInt32();
        sizeRemaining -= 4;

        // flags
        this.qtStream.readUInt32();
        this.qtStream.readUInt32();
        sizeRemaining -= 8;

        // name
        int strlen = this.qtStream.readUint8();

        // rewrote this to handle case where we actually read more than required
		// so here we work out how much we need to read first

        sizeRemaining -= 1;

        this.qtStream.skip(sizeRemaining);
    }

    void readChunkElst(int chunkLen) throws IOException {
        // don't need anything from here atm, skip
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        this.qtStream.skip(sizeRemaining);
    }

    void readChunkEdts(int chunkLen) throws IOException {
        // don't need anything from here atm, skip
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        this.qtStream.skip(sizeRemaining);
    }

    void readChunkMdhd(int chunkLen) throws IOException {
        // don't need anything from here atm, skip
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        this.qtStream.skip(sizeRemaining);
    }

    void readChunkTkhd(int chunkLen) throws IOException {
        // don't need anything from here atm, skip
        int sizeRemaining = chunkLen - 8; // FIXME WRONG

        this.qtStream.skip(sizeRemaining);
    }

    /** chunk handlers */
    void readChunkFtyp(int chunkLen) throws IOException {
        int sizeRemaining = chunkLen - 8; // FIXME: can't hardcode 8, size may be 64bit

        int type = this.qtStream.readUInt32();
        sizeRemaining -= 4;

        if (type != makeFourCC32(77, 52, 65, 32)) { // "M4A " ascii values
            logger.fine("not M4A file");
            return;
        }
        int minorVer = this.qtStream.readUInt32();
        sizeRemaining -= 4;

        // compatible brands
        while (sizeRemaining != 0) {
            // unused
            //fourcc_t cbrand =
            this.qtStream.readUInt32();
            sizeRemaining -= 4;
        }
    }

    /**
     * @return 1: normal, 0, 2: error, 3: need to seek
     */
    public int read(DemuxResT demuxRes) throws IOException {
        int foundMoov = 0;
        int foundMdat = 0;

        this.res = demuxRes;

        // reset demuxRes TODO

        // read the chunks
        while (true) {
logger.finer("available: " + this.qtStream.stream.available());
            int chunkLen;
            int chunkId = 0;

            try {
                chunkLen = this.qtStream.readUInt32();
            } catch (IOException e) {
                logger.warning("(top) error reading chunkLen - possibly number too large");
                chunkLen = 1;
            }

            if (this.qtStream.isEof() != 0) {
                return 0;
            }

            if (chunkLen == 1) {
                logger.fine("need 64bit support");
                return 0;
            }
            chunkId = this.qtStream.readUInt32();
logger.finer("fourcc: " + splitFourCC(chunkId) + ", " + chunkLen);

            if (chunkId == makeFourCC32(102, 116, 121, 112)) { // fourcc equals ftyp
                this.readChunkFtyp(chunkLen);
            } else if (chunkId == makeFourCC32(109, 111, 111, 118)) { // fourcc equals moov
                if (this.readChunkMoov(chunkLen) == 0)
                    return 0; // failed to read moov, can't do anything
                if (foundMdat != 0) {
                    return this.setSavedMDat();
                }
                foundMoov = 1;
            }
            // if we hit mdat before we've found moov, record the position
			// and move on. We can then come back to mdat later.
			// This presumes the stream supports seeking backwards.
            else if (chunkId == makeFourCC32(109, 100, 97, 116)) { // fourcc equals mdat
                int notFoundMoov = 0;
                if (foundMoov == 0)
                    notFoundMoov = 1;
                this.readChunkMDat(chunkLen, notFoundMoov);
                if (foundMoov != 0) {
                    return 1;
                }
                foundMdat = 1;
            }
            // these following atoms can be skipped !!!!
            else if (chunkId == makeFourCC32(102, 114, 101, 101)) { // fourcc equals free
                this.qtStream.skip(chunkLen - 8); // FIXME not 8
            } else if (chunkId == makeFourCC32(106, 117, 110, 107)) { // fourcc equals junk
                this.qtStream.skip(chunkLen - 8); // FIXME not 8
            } else {
                logger.fine("(top) unknown chunk id: " + splitFourCC(chunkId));
                return 0;
            }
        }
    }
}
