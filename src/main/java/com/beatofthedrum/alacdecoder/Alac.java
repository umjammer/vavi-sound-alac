/*
 * Copyright (c) 2011 Peter McQuillan All Rights Reserved.
 *
 * Distributed under the BSD Software License (see license.txt)
 */

package com.beatofthedrum.alacdecoder;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;


/**
 * Alac. (wrapped AlacContext and utilities for working independently)
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2017/11/21 umjammer initial version <br>
 */
public class Alac implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(Alac.class.getName());

    private AlacContext context;

    /**
     * Creates ALAC decoder.
     */
    public Alac(InputStream is) throws IOException {
        context = new AlacContext();

        if (!is.markSupported()) {
            is = new BufferedInputStream(is);
        }
        is.mark(1024);
        AlacInputStream input_stream = new AlacInputStream(is);

        context.input_stream = input_stream;

        // if qtmovie_read returns successfully, the stream is up to
        // the movie data, which can be used directly by the decoder
        QTMovieT qtmovie = new QTMovieT(input_stream);
        DemuxResT demux_res = new DemuxResT();
        int headerRead = qtmovie.read(demux_res);
logger.fine("headerRead: " + headerRead);

        if (headerRead == 0) {
            String error_message;
            if (demux_res.format_read == 0) {
                error_message = "Failed to load the QuickTime movie headers.";
            } else {
                error_message = "Error while loading the QuickTime movie headers."
                        + " File type: " + QTMovieT.splitFourCC(demux_res.format);
            }
logger.fine("reset");
try {
            is.reset(); // TODO not sure here is fine.
} catch (IOException e) {
 logger.fine(e.getMessage());
}
            throw new IOException(error_message);
        } else if (headerRead == 3) {
            // This section is used when the stream system being used doesn't
            // support seeking
            // We have kept track within the file where we need to go to, we
            // close the file and
            // skip bytes to go directly to that point

            if (!(is instanceof FileInputStream)) {
                context.input_stream.reset();
            } else {
                context.input_stream.seek(0);
            }

            context.input_stream = input_stream;

            qtmovie.qtstream.stream = input_stream;
            qtmovie.qtstream.currentPos = 0;
            qtmovie.qtstream.skip(qtmovie.saved_mdat_pos);
        }

        // initialise the sound converter

        AlacFile file = AlacFile.create(demux_res.sample_size, demux_res.num_channels);

        file.alac_set_info(demux_res.codecdata);

        context.demux_res = demux_res;
        context.file = file;
    }

    /** */
    public void close() throws IOException {
        context.close();
    }

    /**
     * Here's where we extract the actual music data
     * @return -1 finished
     */
    public int decode(int[] pDestBuffer, byte[] pcmBuffer) throws IOException {
        int bytes_unpacked = context.unpackSamples(pDestBuffer);
        if (bytes_unpacked > 0) {
            formatSamples(pcmBuffer, getBytesPerSample(), pDestBuffer, bytes_unpacked);
        }
        return bytes_unpacked;
    }

    /** Returns the sample rate of the specified ALAC file */
    public int getSampleRate() {
        return context.getSampleRate();
    }

    /** */
    public int getNumChannels() {
        return context.getNumChannels();
    }

    /** */
    public int getBitsPerSample() {
        return context.getBitsPerSample();
    }

    /** */
    public int getBytesPerSample() {
        return context.getBytesPerSample();
    }

    /** Get total number of samples contained in the Apple Lossless file, or -1 if unknown */
    public int getNumSamples() throws IOException {
        return context.getNumSamples();
    }

    /** from DecoderDemo */
    private static void formatSamples(byte[] dst, int bps, int[] src, int samcnt) {
        int counter = 0;
        int counter2 = 0;

        switch (bps) {
        case 1:
            while (samcnt > 0) {
                dst[counter] = (byte) (0x00FF & (src[counter] + 128));
                counter++;
                samcnt--;
            }
            break;
        case 2:
            while (samcnt > 0) {
                int temp = src[counter2];
                dst[counter] = (byte) temp;
                counter++;
                dst[counter] = (byte) (temp >>> 8);
                counter++;
                counter2++;
                samcnt = samcnt - 2;
            }
            break;
        case 3:
            while (samcnt > 0) {
                dst[counter] = (byte) src[counter2];
                counter++;
                counter2++;
                samcnt--;
            }
            break;
        }
    }

    /** */
    public static int decodeFrame(int[] fmtp, byte[] inbuffer, int[] outbuffer, int outputsize) {

        AlacFile alacFile = new AlacFile();

        alacFile.numchannels = 2;
        alacFile.bytespersample = (fmtp[3] / 8) * 2;

        alacFile.setinfo_max_samples_per_frame = fmtp[1];
        alacFile.setinfo_7a = fmtp[2];
        alacFile.setinfo_sample_size = fmtp[3];
        alacFile.setinfo_rice_historymult = fmtp[4];
        alacFile.setinfo_rice_initialhistory = fmtp[5];
        alacFile.setinfo_rice_kmodifier = fmtp[6];
        alacFile.setinfo_7f = fmtp[7];
        alacFile.setinfo_80 = fmtp[8];
        alacFile.setinfo_82 = fmtp[9];
        alacFile.setinfo_86 = fmtp[10];
        alacFile.setinfo_8a_rate = fmtp[11];

        return alacFile.decodeFrame(inbuffer, outbuffer, outputsize);
    }
}
