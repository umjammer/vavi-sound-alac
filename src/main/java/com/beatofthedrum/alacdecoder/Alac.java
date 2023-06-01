/*
 * Copyright (c) 2011 Peter McQuillan All Rights Reserved.
 *
 * Distributed under the BSD Software License (see license.txt)
 */

package com.beatofthedrum.alacdecoder;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;


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
     * @param is accepts only FileInputStream or InputStream which supports mark
     *           if "moov" chank in the alac mp4 container is located at back of data,
     *           mark might not work well.
     */
    public Alac(InputStream is) throws IOException {
        context = new AlacContext();

        if (!(is instanceof FileInputStream) && !is.markSupported()) {
            throw new IllegalArgumentException("is must be mark supported or FileInputStream");
        }
        if (is.markSupported()) {
            int whole = is.available();
            is.mark(whole);
        }

        context.setInputStream(is);

        // if qtmovie_read returns successfully, the stream is up to
        // the movie data, which can be used directly by the decoder
        QTMovieT qtmovie = new QTMovieT(context.input_stream);
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
try {
            if (is.markSupported()) {
                is.reset();
logger.fine("reset: " + is.available());
            } else if (is instanceof FileInputStream) {
                ((FileInputStream) is).getChannel().position(0);
logger.fine("seek: 0");
            }
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

            if (is.markSupported()) {
                is.reset();
logger.fine("reset: " + is.available());
            } else if (is instanceof FileInputStream) {
                ((FileInputStream) is).getChannel().position(0);
logger.fine("seek: 0");
            }

            qtmovie.qtstream.currentPos = 0;
            qtmovie.qtstream.skip(qtmovie.saved_mdat_pos);
        }

        // initialise the sound converter

        AlacFile file = AlacFile.create(demux_res.sample_size, demux_res.num_channels);

        file.alac_set_info(demux_res.codecdata);

        context.demux_res = demux_res;
        context.file = file;
    }

    @Override
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
            formatSamples(pcmBuffer, getFrameSize(), pDestBuffer, bytes_unpacked);
        }
        return bytes_unpacked;
    }

    /**
     * Returns the sample rate of the specified ALAC file
     * @see javax.sound.sampled.AudioFormat#getSampleRate()
     */
    public int getSampleRate() {
        return context.getSampleRate();
    }

    /** @see javax.sound.sampled.AudioFormat#getChannels() */
    public int getChannels() {
        return context.getNumChannels();
    }

    /** @see AudioFormat#getSampleSizeInBits() */
    public int getSampleSizeInBits() {
        return context.getBitsPerSample();
    }

    /** @see javax.sound.sampled.AudioFormat#getFrameSize() */
    public int getFrameSize() {
        return context.getBytesPerSample();
    }

    /** @see javax.sound.sampled.AudioFormat#getFrameRate() */
    public int getFrameRate() {
        return context.getSampleRate();
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
