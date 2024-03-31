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
     * @throws IllegalArgumentException maybe {@code is} is not alac
     */
    public Alac(InputStream is) throws IOException {
        context = new AlacContext();

        if (!(is instanceof FileInputStream) && !is.markSupported()) {
            throw new IOException("is must be mark supported or FileInputStream");
        }
        if (is.markSupported()) {
            int whole = is.available();
            is.mark(whole);
        }

        context.setInputStream(is);

        // if QTMovieT#read returns successfully, the stream is up to
        // the movie data, which can be used directly by the decoder
        QTMovieT qtMovie = new QTMovieT(context.inputStream);
        DemuxResT demuxRes = new DemuxResT();
        int headerRead = qtMovie.read(demuxRes);
logger.fine("headerRead: " + headerRead);

        if (headerRead == 0) {
            String errorMessage;
            if (demuxRes.formatRead == 0) {
                errorMessage = "Failed to load the QuickTime movie headers.";
            } else {
                errorMessage = "Error while loading the QuickTime movie headers."
                        + " File type: " + QTMovieT.splitFourCC(demuxRes.format);
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
            throw new IllegalArgumentException(errorMessage);
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

            qtMovie.qtStream.currentPos = 0;
            qtMovie.qtStream.skip(qtMovie.savedMDatPos);
        }

        // initialise the sound converter

        AlacFile file = AlacFile.create(demuxRes.sampleSize, demuxRes.numChannels);

        file.setAlacInfo(demuxRes.codecData);

        context.demuxRes = demuxRes;
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
    public int decode(int[] destBuffer, byte[] pcmBuffer) throws IOException {
        int bytesUnpacked = context.unpackSamples(destBuffer);
        if (bytesUnpacked > 0) {
            formatSamples(pcmBuffer, getFrameSize(), destBuffer, bytesUnpacked);
        }
        return bytesUnpacked;
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
    private static void formatSamples(byte[] dst, int bps, int[] src, int samCount) {
        int counter = 0;
        int counter2 = 0;

        switch (bps) {
        case 1:
            while (samCount > 0) {
                dst[counter] = (byte) (0x00FF & (src[counter] + 128));
                counter++;
                samCount--;
            }
            break;
        case 2:
            while (samCount > 0) {
                int temp = src[counter2];
                dst[counter] = (byte) temp;
                counter++;
                dst[counter] = (byte) (temp >>> 8);
                counter++;
                counter2++;
                samCount = samCount - 2;
            }
            break;
        case 3:
            while (samCount > 0) {
                dst[counter] = (byte) src[counter2];
                counter++;
                counter2++;
                samCount--;
            }
            break;
        }
    }

    /** used by airplay */
    public static int decodeFrame(int[] fmtp, byte[] inBuffer, int[] outBuffer, int outputSize) {

        AlacFile alacFile = new AlacFile();

        alacFile.numChannels = 2;
        alacFile.bytesPerSample = (fmtp[3] / 8) * 2;

        alacFile.setInfo_maxSamplesPerFrame = fmtp[1];
        alacFile.setInfo_7A = fmtp[2];
        alacFile.setInfo_sampleSize = fmtp[3];
        alacFile.setInfo_riceHistoryMult = fmtp[4];
        alacFile.setInfo_riceInitialHistory = fmtp[5];
        alacFile.setInfo_riceKModifier = fmtp[6];
        alacFile.setInfo_7f = fmtp[7];
        alacFile.setInfo_80 = fmtp[8];
        alacFile.setInfo_82 = fmtp[9];
        alacFile.setInfo_86 = fmtp[10];
        alacFile.setInfo_8a_rate = fmtp[11];

        return alacFile.decodeFrame(inBuffer, outBuffer, outputSize);
    }
}
