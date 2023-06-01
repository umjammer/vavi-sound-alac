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
    MyStream qtstream;

    /** */
    private DemuxResT res;

    /** */
    int saved_mdat_pos;

    /** */
    public QTMovieT(java.io.DataInputStream file) {
        saved_mdat_pos = 0;
        qtstream = new MyStream(file);
    }

    /** */
    private static int makeFourCC32(int ch0, int ch1, int ch2, int ch3) {

        int retval = ch0 << 24;
        int tmp = ch1;

        retval = retval | (tmp << 16);
        tmp = ch2;

        retval = retval | (tmp << 8);
        tmp = ch3;

        retval = retval | tmp;

        return retval;
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
    int set_saved_mdat() {

        if (this.saved_mdat_pos == -1) {
            logger.fine("stream contains mdat before moov but is not seekable");
            return 2;
        }

logger.finer("saved_mdat_pos: " + saved_mdat_pos);
        if (this.qtstream.position(this.saved_mdat_pos) != 0) {
            return 3;
        }

        return 1;
    }

    /** */
    void read_chunk_mdat(int chunk_len, int skip_mdat) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        if (size_remaining == 0)
            return;

        this.res.mdat_len = size_remaining;
        if (skip_mdat != 0) {
            this.saved_mdat_pos = this.qtstream.position();

            this.qtstream.skip(size_remaining);
        }
    }

    /** 'moov' movie atom - contains other atoms */
    int read_chunk_moov(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;
            int sub_chunk_id = 0;

            try {
                sub_chunk_len = this.qtstream.read_uint32();
            } catch (IOException e) {
                logger.fine("(read_chunk_moov) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                logger.fine("strange size for chunk inside moov");
                return 0;
            }

            sub_chunk_id = this.qtstream.read_uint32();

            if (sub_chunk_id == makeFourCC32(109, 118, 104, 100)) { // fourcc equals mvhd
                read_chunk_mvhd(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC32(116, 114, 97, 107)) { // fourcc equals trak
                if (read_chunk_trak(sub_chunk_len) == 0)
                    return 0;
            } else if (sub_chunk_id == makeFourCC32(117, 100, 116, 97)) { // fourcc equals udta
                read_chunk_udta(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC32(101, 108, 115, 116)) { // fourcc equals elst
                read_chunk_elst(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC32(105, 111, 100, 115)) { // fourcc equals iods
                read_chunk_iods(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC32(102, 114, 101, 101)) { // fourcc equals free
                this.qtstream.skip(sub_chunk_len - 8); // FIXME not 8
            } else {
                logger.fine("(moov) unknown chunk id: " + splitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    /** 'iods' */
    void read_chunk_iods(int chunk_len) throws IOException {
        // don't need anything from here atm, skip
        int size_remaining = chunk_len - 8; // FIXME WRONG

        this.qtstream.skip(size_remaining);
    }

    /** 'udta' user data.. contains tag info */
    void read_chunk_udta(int chunk_len) throws IOException {
        // don't need anything from here atm, skip
        int size_remaining = chunk_len - 8; // FIXME WRONG

        this.qtstream.skip(size_remaining);
    }

    /** 'mvhd' movie header atom */
    void read_chunk_mvhd(int chunk_len) throws IOException {
        // don't need anything from here atm, skip
        int size_remaining = chunk_len - 8; // FIXME WRONG

        this.qtstream.skip(size_remaining);
    }

    /** 'trak' - a movie track - contains other atoms */
    int read_chunk_trak(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;
            int sub_chunk_id = 0;

            try {
                sub_chunk_len = this.qtstream.read_uint32();
            } catch (IOException e) {
                logger.fine("(read_chunk_trak) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                logger.fine("strange size for chunk inside trak");
                return 0;
            }

            sub_chunk_id = this.qtstream.read_uint32();

            if (sub_chunk_id == makeFourCC32(116, 107, 104, 100)) { // fourcc equals tkhd
                read_chunk_tkhd(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC32(109, 100, 105, 97)) { // fourcc equals mdia
                if (read_chunk_mdia(sub_chunk_len) == 0)
                    return 0;
            } else if (sub_chunk_id == makeFourCC32(101, 100, 116, 115)) { // fourcc equals edts
                read_chunk_edts(sub_chunk_len);
            } else {
                logger.fine("(trak) unknown chunk id: " + splitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    int read_chunk_mdia(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;
            int sub_chunk_id = 0;

            try {
                sub_chunk_len = this.qtstream.read_uint32();
            } catch (IOException e) {
                logger.fine("(read_chunk_mdia) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                logger.fine("strange size for chunk inside mdia\n");
                return 0;
            }

            sub_chunk_id = this.qtstream.read_uint32();

            if (sub_chunk_id == makeFourCC32(109, 100, 104, 100)) { // fourcc equals mdhd
                read_chunk_mdhd(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC32(104, 100, 108, 114)) { // fourcc equals hdlr
                read_chunk_hdlr(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC32(109, 105, 110, 102)) { // fourcc equals minf
                if (read_chunk_minf(sub_chunk_len) == 0)
                    return 0;
            } else {
                logger.fine("(mdia) unknown chunk id: " + splitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    /** */
    int read_chunk_minf(int chunk_len) throws IOException {
        int dinf_size;
        int stbl_size;
        int size_remaining = chunk_len - 8; // FIXME WRONG
        int media_info_size;

        // SOUND HEADER CHUNK

        try {
            media_info_size = this.qtstream.read_uint32();
        } catch (IOException e) {
            logger.fine("(read_chunk_minf) error reading media_info_size - possibly number too large");
            media_info_size = 0;
        }

        if (media_info_size != 16) {
            logger.fine("unexpected size in media info\n");
            return 0;
        }
        if (this.qtstream.read_uint32() != makeFourCC32(115, 109, 104, 100)) { // "smhd" ascii values
            logger.fine("not a sound header! can't handle this.");
            return 0;
        }
        // now skip the rest
        this.qtstream.skip(16 - 8);
        size_remaining -= 16;

        //

        // DINF CHUNK
        try {
            dinf_size = this.qtstream.read_uint32();
        } catch (IOException e) {
            logger.fine("(read_chunk_minf) error reading dinf_size - possibly number too large");
            dinf_size = 0;
        }

        if (this.qtstream.read_uint32() != makeFourCC32(100, 105, 110, 102)) { // "dinf" ascii values
            logger.fine("expected dinf, didn't get it.");
            return 0;
        }
        // skip it
        this.qtstream.skip(dinf_size - 8);
        size_remaining -= dinf_size;

        //

        // SAMPLE TABLE
        try {
            stbl_size = (this.qtstream.read_uint32());
        } catch (Exception e) {
            logger.fine("(read_chunk_minf) error reading stbl_size - possibly number too large");
            stbl_size = 0;
        }

        if (this.qtstream.read_uint32() != makeFourCC32(115, 116, 98, 108)) { // "stbl" ascii values
            logger.fine("expected stbl, didn't get it.");
            return 0;
        }
        if (read_chunk_stbl(stbl_size) == 0)
            return 0;
        size_remaining -= stbl_size;

        if (size_remaining != 0) {
            logger.fine("(read_chunk_minf) - size remaining?");
            this.qtstream.skip(size_remaining);
        }

        return 1;
    }

    /**
     * sample to chunk box
     */
    private void read_chunk_stsc(int sub_chunk_len) throws IOException {
        // skip header and size
        MyStream stream = this.qtstream;
        // skip version and other junk
        stream.skip(4);
        int num_entries = stream.read_uint32();
        this.res.stsc = new DemuxResT.ChunkInfo[num_entries];
        for (int i = 0; i < num_entries; i++) {
            DemuxResT.ChunkInfo entry = new DemuxResT.ChunkInfo();
            entry.first_chunk = stream.read_uint32();
            entry.samples_per_chunk = stream.read_uint32();
            entry.sample_desc_index = stream.read_uint32();
            this.res.stsc[i] = entry;
        }
    }

    /**
     * chunk to offset box
     */
    private void read_chunk_stco(int sub_chunk_len) throws IOException {
        // skip header and size
        MyStream stream = this.qtstream;
        stream.skip(4);

        int num_entries = stream.read_uint32();

        this.res.stco = new int[num_entries];
        for (int i = 0; i < num_entries; i++) {
            this.res.stco[i] = stream.read_uint32();
        }
    }

    int read_chunk_stbl(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        while (size_remaining != 0) {
            int sub_chunk_len;
            int sub_chunk_id = 0;

            try {
                sub_chunk_len = this.qtstream.read_uint32();
            } catch (IOException e) {
                logger.fine("(read_chunk_stbl) error reading sub_chunk_len - possibly number too large");
                sub_chunk_len = 0;
            }

            if (sub_chunk_len <= 1 || sub_chunk_len > size_remaining) {
                logger.fine("strange size for chunk inside stbl " + sub_chunk_len + " (remaining: " + size_remaining + ")");
                return 0;
            }

            sub_chunk_id = this.qtstream.read_uint32();

            if (sub_chunk_id == makeFourCC32(115, 116, 115, 100)) { // fourcc equals stsd
                if (read_chunk_stsd(sub_chunk_len) == 0)
                    return 0;
            } else if (sub_chunk_id == makeFourCC32(115, 116, 116, 115)) { // fourcc equals stts
                read_chunk_stts(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC32(115, 116, 115, 122)) { // fourcc equals stsz
                read_chunk_stsz(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC32(115, 116, 115, 99)) { // fourcc equals stsc
                read_chunk_stsc(sub_chunk_len);
            } else if (sub_chunk_id == makeFourCC32(115, 116, 99, 111)) { // fourcc equals stco
                read_chunk_stco(sub_chunk_len);
            } else {
                logger.fine("(stbl) unknown chunk id: " + splitFourCC(sub_chunk_id));
                return 0;
            }

            size_remaining -= sub_chunk_len;
        }

        return 1;
    }

    /** */
    void read_chunk_stsz(int chunk_len) throws IOException {
        int numentries = 0;
        int uniform_size = 0;
        int size_remaining = chunk_len - 8; // FIXME WRONG

        // version
        this.qtstream.read_uint8();
        size_remaining -= 1;
        // flags
        this.qtstream.read_uint8();
        this.qtstream.read_uint8();
        this.qtstream.read_uint8();
        size_remaining -= 3;

        // default sample size
        uniform_size = (this.qtstream.read_uint32());
        if (uniform_size != 0) {
            // Normally files have intiable sample sizes, this handles the case where
			// they are all the same size

            int uniform_num = 0;

            uniform_num = (this.qtstream.read_uint32());

            this.res.sample_byte_size = new int[uniform_num];

            for (int i = 0; i < uniform_num; i++) {
                this.res.sample_byte_size[i] = uniform_size;
            }
            size_remaining -= 4;
            return;
        }
        size_remaining -= 4;

        try {
            numentries = this.qtstream.read_uint32();
        } catch (IOException e) {
            logger.fine("(read_chunk_stsz) error reading numentries - possibly number too large");
            numentries = 0;
        }

        size_remaining -= 4;

        this.res.sample_byte_size = new int[numentries];

        for (int i = 0; i < numentries; i++) {
            this.res.sample_byte_size[i] = (this.qtstream.read_uint32());

            size_remaining -= 4;
        }

        if (size_remaining != 0) {
            logger.fine("(read_chunk_stsz) size remaining?");
            this.qtstream.skip(size_remaining);
        }
    }

    void read_chunk_stts(int chunk_len) throws IOException {
        int numentries = 0;
        int size_remaining = chunk_len - 8; // FIXME WRONG

        // version
        this.qtstream.read_uint8();
        size_remaining -= 1;
        // flags
        this.qtstream.read_uint8();
        this.qtstream.read_uint8();
        this.qtstream.read_uint8();
        size_remaining -= 3;

        try {
            numentries = this.qtstream.read_uint32();
        } catch (IOException e) {
            logger.fine("(read_chunk_stts) error reading numentries - possibly number too large");
            numentries = 0;
        }

        size_remaining -= 4;

        this.res.num_time_to_samples = numentries;

        for (int i = 0; i < numentries; i++) {
            this.res.time_to_sample[i].sample_count = this.qtstream.read_uint32();
            this.res.time_to_sample[i].sample_duration = this.qtstream.read_uint32();
            size_remaining -= 8;
        }

        if (size_remaining != 0) {
            logger.fine("(read_chunk_stts) size remaining?");
            this.qtstream.skip(size_remaining);
        }
    }

    int read_chunk_stsd(int chunk_len) throws IOException {
        int numentries = 0;
        int size_remaining = chunk_len - 8; // FIXME WRONG

        // version
        this.qtstream.read_uint8();
        size_remaining -= 1;
        // flags
        this.qtstream.read_uint8();
        this.qtstream.read_uint8();
        this.qtstream.read_uint8();
        size_remaining -= 3;

        try {
            numentries = this.qtstream.read_uint32();
        } catch (IOException e) {
            logger.fine("(read_chunk_stsd) error reading numentries - possibly number too large");
            numentries = 0;
        }

        size_remaining -= 4;

        if (numentries != 1) {
            logger.fine("only expecting one entry in sample description atom!");
            return 0;
        }

        for (int i = 0; i < numentries; i++) {
            // parse the file atom contained within the stsd atom
            int entry_size;
            int version;

            int entry_remaining;

            entry_size = this.qtstream.read_uint32();
            this.res.format = this.qtstream.read_uint32();
            entry_remaining = entry_size;
            entry_remaining -= 8;

            if (this.res.format != makeFourCC32(97, 108, 97, 99)) { // "file" ascii values
                logger.fine("(read_chunk_stsd) error reading description atom - expecting file, got " + splitFourCC(this.res.format));
                return 0;
            }

            // sound info:

            this.qtstream.skip(6); // reserved
            entry_remaining -= 6;

            version = this.qtstream.read_uint16();

            if (version != 1)
                logger.fine("unknown version??");
            entry_remaining -= 2;

            // revision level
            this.qtstream.read_uint16();
            // vendor
            this.qtstream.read_uint32();
            entry_remaining -= 6;

            // EH?? spec doesn't say there's an extra 16 bits here... but there is!
            this.qtstream.read_uint16();
            entry_remaining -= 2;

            // skip 4 - this is the top level num of channels and bits per sample
            this.qtstream.skip(4);
            entry_remaining -= 4;

            // compression id
            this.qtstream.read_uint16();
            // packet size
            this.qtstream.read_uint16();
            entry_remaining -= 4;

            // skip 4 - this is the top level sample rate
            this.qtstream.skip(4);
            entry_remaining -= 4;

            // remaining is codec data

            // 12 = audio format atom, 8 = padding
            this.res.codecdata_len = entry_remaining + 12 + 8;

            if (this.res.codecdata_len > this.res.codecdata.length) {
                logger.fine("(read_chunk_stsd) unexpected codec data length read from atom " + this.res.codecdata_len);
                return 0;
            }

            for (int count = 0; count < this.res.codecdata_len; count++) {
                this.res.codecdata[count] = 0;
            }

            // audio format atom
            this.res.codecdata[0] = 0x0c000000;
            this.res.codecdata[1] = makeFourCC(97, 109, 114, 102); // "amrf" ascii values
            this.res.codecdata[2] = makeFourCC(99, 97, 108, 97); // "cala" ascii values

            this.qtstream.read(entry_remaining, this.res.codecdata, 12); // codecdata buffer should be +12
            entry_remaining -= entry_remaining;

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

            this.res.sample_size = (this.res.codecdata[ptrIndex] & 0xff);

            ptrIndex = 33; // position of num of channels

            this.res.num_channels = (this.res.codecdata[ptrIndex] & 0xff);

            ptrIndex = 44; // position of sample rate within codec data buffer

            this.res.sample_rate = (((this.res.codecdata[ptrIndex] & 0xff) << 24) | ((this.res.codecdata[ptrIndex + 1] & 0xff) << 16) | ((this.res.codecdata[ptrIndex + 2] & 0xff) << 8) | (this.res.codecdata[ptrIndex + 3] & 0xff));

            if (entry_remaining != 0) // was comparing to null
                this.qtstream.skip(entry_remaining);

            this.res.format_read = 1;
            if (this.res.format != makeFourCC32(97, 108, 97, 99)) { // "file" ascii values
                return 0;
            }
        }

        return 1;
    }

    /** media handler inside mdia */
    void read_chunk_hdlr(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME WRONG

        // version
        this.qtstream.read_uint8();
        size_remaining -= 1;
        // flags
        this.qtstream.read_uint8();
        this.qtstream.read_uint8();
        this.qtstream.read_uint8();
        size_remaining -= 3;

        // component type
        int comptype = this.qtstream.read_uint32();
        int compsubtype = this.qtstream.read_uint32();
        size_remaining -= 8;

        // component manufacturer
        this.qtstream.read_uint32();
        size_remaining -= 4;

        // flags
        this.qtstream.read_uint32();
        this.qtstream.read_uint32();
        size_remaining -= 8;

        // name
        int strlen = this.qtstream.read_uint8();

        // rewrote this to handle case where we actually read more than required
		// so here we work out how much we need to read first

        size_remaining -= 1;

        this.qtstream.skip(size_remaining);
    }

    void read_chunk_elst(int chunk_len) throws IOException {
        // don't need anything from here atm, skip
        int size_remaining = chunk_len - 8; // FIXME WRONG

        this.qtstream.skip(size_remaining);
    }

    void read_chunk_edts(int chunk_len) throws IOException {
        // don't need anything from here atm, skip
        int size_remaining = chunk_len - 8; // FIXME WRONG

        this.qtstream.skip(size_remaining);
    }

    void read_chunk_mdhd(int chunk_len) throws IOException {
        // don't need anything from here atm, skip
        int size_remaining = chunk_len - 8; // FIXME WRONG

        this.qtstream.skip(size_remaining);
    }

    void read_chunk_tkhd(int chunk_len) throws IOException {
        // don't need anything from here atm, skip
        int size_remaining = chunk_len - 8; // FIXME WRONG

        this.qtstream.skip(size_remaining);
    }

    /** chunk handlers */
    void read_chunk_ftyp(int chunk_len) throws IOException {
        int size_remaining = chunk_len - 8; // FIXME: can't hardcode 8, size may be 64bit

        int type = this.qtstream.read_uint32();
        size_remaining -= 4;

        if (type != makeFourCC32(77, 52, 65, 32)) { // "M4A " ascii values
            logger.fine("not M4A file");
            return;
        }
        int minor_ver = this.qtstream.read_uint32();
        size_remaining -= 4;

        // compatible brands
        while (size_remaining != 0) {
            // unused
            //fourcc_t cbrand =
            this.qtstream.read_uint32();
            size_remaining -= 4;
        }
    }

    /**
     * @return 1: normal, 0, 2: error, 3: need to seek
     */
    public int read(DemuxResT demux_res) throws IOException {
        int found_moov = 0;
        int found_mdat = 0;

        this.res = demux_res;

        // reset demux_res TODO

        // read the chunks
        while (true) {
logger.finer("available: " + this.qtstream.stream.available());
            int chunk_len;
            int chunk_id = 0;

            try {
                chunk_len = this.qtstream.read_uint32();
            } catch (IOException e) {
                logger.warning("(top) error reading chunk_len - possibly number too large");
                chunk_len = 1;
            }

            if (this.qtstream.isEof() != 0) {
                return 0;
            }

            if (chunk_len == 1) {
                logger.fine("need 64bit support");
                return 0;
            }
            chunk_id = this.qtstream.read_uint32();
logger.finer("fourcc: " + splitFourCC(chunk_id) + ", " + chunk_len);

            if (chunk_id == makeFourCC32(102, 116, 121, 112)) { // fourcc equals ftyp
                this.read_chunk_ftyp(chunk_len);
            } else if (chunk_id == makeFourCC32(109, 111, 111, 118)) { // fourcc equals moov
                if (this.read_chunk_moov(chunk_len) == 0)
                    return 0; // failed to read moov, can't do anything
                if (found_mdat != 0) {
                    return this.set_saved_mdat();
                }
                found_moov = 1;
            }
            // if we hit mdat before we've found moov, record the position
			// and move on. We can then come back to mdat later.
			// This presumes the stream supports seeking backwards.
            else if (chunk_id == makeFourCC32(109, 100, 97, 116)) { // fourcc equals mdat
                int not_found_moov = 0;
                if (found_moov == 0)
                    not_found_moov = 1;
                this.read_chunk_mdat(chunk_len, not_found_moov);
                if (found_moov != 0) {
                    return 1;
                }
                found_mdat = 1;
            }
            // these following atoms can be skipped !!!!
            else if (chunk_id == makeFourCC32(102, 114, 101, 101)) { // fourcc equals free
                this.qtstream.skip(chunk_len - 8); // FIXME not 8
            } else if (chunk_id == makeFourCC32(106, 117, 110, 107)) { // fourcc equals junk
                this.qtstream.skip(chunk_len - 8); // FIXME not 8
            } else {
                logger.fine("(top) unknown chunk id: " + splitFourCC(chunk_id));
                return 0;
            }
        }
    }
}
