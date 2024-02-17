/*
 * Copyright (c) 2011 Peter McQuillan
 *
 * All Rights Reserved.
 *
 * Distributed under the BSD Software License (see license.txt)
 */

package com.beatofthedrum.alacdecoder;


import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;


/**
 * AlacContext.
 */
public class AlacContext {

    private static final Logger logger = Logger.getLogger(AlacContext.class.getName());

    /**
     * @author Denis Tulskiy
     * @since 4/7/11
     */
    private static class AlacInputStream extends DataInputStream {

        int total;

        /**
         * Creates a DataInputStream that uses the specified
         * underlying InputStream.
         *
         * @param in the specified input stream
         */
        public AlacInputStream(InputStream in) throws IOException {
            super(in);
            total = in.available();
logger.fine("total: " + total);
        }

        public void seek(long pos) throws IOException {
            if (in instanceof FileInputStream) {
                FileChannel fc = ((FileInputStream) in).getChannel();
                fc.position(pos);
logger.fine("position: " + fc.position());
            } else if (in.markSupported()) {
                in.reset();
                in.mark(total);
logger.fine("reset: " + in.available());
                if (pos != 0)
                    skipBytes((int) pos);
            }
        }
    }

    DemuxResT demuxRes;
    AlacFile file;
    AlacInputStream inputStream;
    private int currentSampleBlock = 0;
    private int offset;
    /** sample big enough to hold any input for a single file frame */
    private final byte[] readBuffer = new byte[1024 * 80];

    /** old original factory */
    public static AlacContext openFileInput(File inputFile) throws IOException {
        int headerRead;
        DemuxResT demuxRes = new DemuxResT();
        AlacContext context = new AlacContext();
        AlacFile file;

        FileInputStream fistream = new FileInputStream(inputFile);

        context.setInputStream(fistream);

        // if QtMovieT#read returns successfully, the stream is up to
        // the movie data, which can be used directly by the decoder
        QTMovieT qtMovie = new QTMovieT(context.inputStream);
        headerRead = qtMovie.read(demuxRes);

        if (headerRead == 0) {
            String errorMessage;
            if (demuxRes.formatRead == 0) {
                errorMessage = "Failed to load the QuickTime movie headers.";
            } else {
                errorMessage = "Error while loading the QuickTime movie headers."
                        + " File type: " + QTMovieT.splitFourCC(demuxRes.format);
            }
            throw new IOException(errorMessage);
        } else if (headerRead == 3) {
            // This section is used when the stream system being used doesn't support seeking
            // We have kept track within the file where we need to go to, we close the file and
            // skip bytes to go directly to that point

            context.inputStream.close();

            fistream = new FileInputStream(inputFile);
            context.setInputStream(fistream);

            qtMovie.qtStream.stream = context.inputStream;
            qtMovie.qtStream.currentPos = 0;
            qtMovie.qtStream.skip(qtMovie.savedMDatPos);
        }

        // initialise the sound converter

        file = AlacFile.create(demuxRes.sampleSize, demuxRes.numChannels);

        file.setAlacInfo(demuxRes.codecData);

        context.demuxRes = demuxRes;
        context.file = file;

        return context;
    }

    /** */
    public void setInputStream(InputStream is) throws IOException {
        inputStream = new AlacInputStream(is);
    }

    /**
     * sets position in pcm samples
     *
     * @param position position in pcm samples to go to
     * @throws IllegalArgumentException at getSampleInfo
     */
    public void setPosition(long position) throws IOException {
        DemuxResT res = this.demuxRes;

        int currentPosition = 0;
        int currentSample = 0;
        DemuxResT.SampleDuration sampleInfo = new DemuxResT.SampleDuration();
        for (int i = 0; i < res.stsc.length; i++) {
            DemuxResT.ChunkInfo chunkInfo = res.stsc[i];
            int lastChunk;

            if (i < res.stsc.length - 1) {
                lastChunk = res.stsc[i + 1].firstChunk;
            } else {
                lastChunk = res.stco.length;
            }

            for (int chunk = chunkInfo.firstChunk; chunk <= lastChunk; chunk++) {
                int pos = res.stco[chunk - 1];
                int sampleCount = chunkInfo.samplesPerChunk;
                while (sampleCount > 0) {
                    res.getSampleInfo(currentSample, sampleInfo);
                    currentPosition += sampleInfo.sampleDuration;
                    if (position < currentPosition) {
                        this.inputStream.seek(pos);
                        this.currentSampleBlock = currentSample;
                        this.offset = (int) (position - (currentPosition - sampleInfo.sampleDuration)) * getNumChannels();
                        return;
                    }
                    pos += sampleInfo.sampleByteSize;
                    currentSample++;
                    sampleCount--;
                }
            }
        }
    }

    /** Get total number of samples contained in the Apple Lossless file, or -1 if unknown */
    public int getNumSamples() throws IOException {
        // calculate output size
        int numSamples = 0;
        @SuppressWarnings("unused")
        boolean errorFound = false;
        @SuppressWarnings("unused")
        int retVal = 0;

        for (int i = 0; i < this.demuxRes.sampleByteSize.length; i++) {
            int thisSampleDuration = 0;
            int thisSampleByteSize = 0;

            DemuxResT.SampleDuration sampleInfo = new DemuxResT.SampleDuration();
            this.demuxRes.getSampleInfo(i, sampleInfo);
            thisSampleDuration = sampleInfo.sampleDuration;
            thisSampleByteSize = sampleInfo.sampleByteSize;

            numSamples += thisSampleDuration;
        }

        return numSamples;
    }

    public int getBytesPerSample() {
        if (this.demuxRes.sampleSize != 0) {
            return (int) Math.ceil(this.demuxRes.sampleSize / 8d);
        } else {
            return 2;
        }
    }

    public int getBitsPerSample() {
        if (this.demuxRes.sampleSize != 0) {
            return this.demuxRes.sampleSize;
        } else {
            return 16;
        }
    }

    public int getNumChannels() {
        if (this.demuxRes.numChannels != 0) {
            return this.demuxRes.numChannels;
        } else {
            return 2;
        }
    }

    /** Returns the sample rate of the specified ALAC file */
    public int getSampleRate() {
        if (this.demuxRes.sampleRate != 0) {
            return this.demuxRes.sampleRate;
        } else {
            return 44100;
        }
    }

    /**
     * Here's where we extract the actual music data
     * @return -1 finished
     */
    public int unpackSamples(int[] destBuffer) throws IOException {
        DemuxResT.SampleDuration sampleInfo = new DemuxResT.SampleDuration();
        byte[] readBuffer = this.readBuffer;
//        int destBufferSize = 1024 * 24 * 3; // 24kb buffer = 4096 frames = 1 file sample (we support max 24bps)
        int destBufferSize = destBuffer.length;
        MyStream inputStream = new MyStream(this.inputStream);

        // if currentSampleBlock is beyond last block then finished
        if (this.currentSampleBlock >= this.demuxRes.sampleByteSize.length) {
            return -1;
        }

        this.demuxRes.getSampleInfo(this.currentSampleBlock, sampleInfo);

        int sampleByteSize = sampleInfo.sampleByteSize;

        inputStream.read(sampleByteSize, readBuffer, 0);

        // now fetch
        int outputBytes = destBufferSize;

        outputBytes = this.file.decodeFrame(readBuffer, destBuffer, outputBytes);

        this.currentSampleBlock = this.currentSampleBlock + 1;
        outputBytes -= this.offset * this.getBytesPerSample();
        System.arraycopy(destBuffer, this.offset, destBuffer, 0, outputBytes);
        this.offset = 0;
        return outputBytes;
    }

    /** */
    public void close() throws IOException {
        if (null != this.inputStream) {
            this.inputStream.close();
        }
    }
}
