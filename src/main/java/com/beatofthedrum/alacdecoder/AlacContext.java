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
    static class AlacInputStream extends DataInputStream {

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

    DemuxResT demux_res;
    AlacFile file;
    AlacInputStream input_stream;
    int current_sample_block = 0;
    int offset;
    /** sample big enough to hold any input for a single file frame */
    byte[] read_buffer = new byte[1024 * 80];

    /** old original factory */
    public static AlacContext openFileInput(File inputfile) throws IOException {
        int headerRead;
        DemuxResT demux_res = new DemuxResT();
        AlacContext context = new AlacContext();
        AlacFile file;

        FileInputStream fistream = new FileInputStream(inputfile);

        context.setInputStream(fistream);

        // if qtmovie_read returns successfully, the stream is up to
        // the movie data, which can be used directly by the decoder
        QTMovieT qtmovie = new QTMovieT(context.input_stream);
        headerRead = qtmovie.read(demux_res);

        if (headerRead == 0) {
            String error_message;
            if (demux_res.format_read == 0) {
                error_message = "Failed to load the QuickTime movie headers.";
            } else {
                error_message = "Error while loading the QuickTime movie headers."
                        + " File type: " + QTMovieT.splitFourCC(demux_res.format);
            }
            throw new IOException(error_message);
        } else if (headerRead == 3) {
            // This section is used when the stream system being used doesn't support seeking
            // We have kept track within the file where we need to go to, we close the file and
            // skip bytes to go directly to that point

            context.input_stream.close();

            fistream = new FileInputStream(inputfile);
            context.setInputStream(fistream);

            qtmovie.qtstream.stream = context.input_stream;
            qtmovie.qtstream.currentPos = 0;
            qtmovie.qtstream.skip(qtmovie.saved_mdat_pos);
        }

        // initialise the sound converter

        file = AlacFile.create(demux_res.sample_size, demux_res.num_channels);

        file.alac_set_info(demux_res.codecdata);

        context.demux_res = demux_res;
        context.file = file;

        return context;
    }

    /** */
    public void setInputStream(InputStream is) throws IOException {
        input_stream = new AlacInputStream(is);
    }

    /**
     * sets position in pcm samples
     *
     * @param position position in pcm samples to go to
     * @throws IllegalArgumentException at get_sample_info
     */
    public void setPosition(long position) throws IOException {
        DemuxResT res = this.demux_res;

        int current_position = 0;
        int current_sample = 0;
        DemuxResT.SampleDuration sample_info = new DemuxResT.SampleDuration();
        for (int i = 0; i < res.stsc.length; i++) {
            DemuxResT.ChunkInfo chunkInfo = res.stsc[i];
            int last_chunk;

            if (i < res.stsc.length - 1) {
                last_chunk = res.stsc[i + 1].first_chunk;
            } else {
                last_chunk = res.stco.length;
            }

            for (int chunk = chunkInfo.first_chunk; chunk <= last_chunk; chunk++) {
                int pos = res.stco[chunk - 1];
                int sample_count = chunkInfo.samples_per_chunk;
                while (sample_count > 0) {
                    res.get_sample_info(current_sample, sample_info);
                    current_position += sample_info.sample_duration;
                    if (position < current_position) {
                        this.input_stream.seek(pos);
                        this.current_sample_block = current_sample;
                        this.offset = (int) (position - (current_position - sample_info.sample_duration)) * getNumChannels();
                        return;
                    }
                    pos += sample_info.sample_byte_size;
                    current_sample++;
                    sample_count--;
                }
            }
        }
    }

    /** Get total number of samples contained in the Apple Lossless file, or -1 if unknown */
    public int getNumSamples() throws IOException {
        // calculate output size
        int num_samples = 0;
        int thissample_duration;
        @SuppressWarnings("unused")
        int thissample_bytesize = 0;
        DemuxResT.SampleDuration sampleinfo = new DemuxResT.SampleDuration();
        int i;
        @SuppressWarnings("unused")
        boolean error_found = false;
        int retval = 0;

        for (i = 0; i < this.demux_res.sample_byte_size.length; i++) {
            thissample_duration = 0;
            thissample_bytesize = 0;

            this.demux_res.get_sample_info(i, sampleinfo);
            thissample_duration = sampleinfo.sample_duration;
            thissample_bytesize = sampleinfo.sample_byte_size;

            num_samples += thissample_duration;
        }

        return num_samples;
    }

    public int getBytesPerSample() {
        if (this.demux_res.sample_size != 0) {
            return (int) Math.ceil(this.demux_res.sample_size / 8d);
        } else {
            return 2;
        }
    }

    public int getBitsPerSample() {
        if (this.demux_res.sample_size != 0) {
            return this.demux_res.sample_size;
        } else {
            return 16;
        }
    }

    public int getNumChannels() {
        if (this.demux_res.num_channels != 0) {
            return this.demux_res.num_channels;
        } else {
            return 2;
        }
    }

    /** Returns the sample rate of the specified ALAC file */
    public int getSampleRate() {
        if (this.demux_res.sample_rate != 0) {
            return this.demux_res.sample_rate;
        } else {
            return 44100;
        }
    }

    /**
     * Here's where we extract the actual music data
     * @return -1 finished
     */
    public int unpackSamples(int[] pDestBuffer) throws IOException {
        DemuxResT.SampleDuration sampleinfo = new DemuxResT.SampleDuration();
        byte[] read_buffer = this.read_buffer;
//        int destBufferSize = 1024 * 24 * 3; // 24kb buffer = 4096 frames = 1 file sample (we support max 24bps)
        int destBufferSize = pDestBuffer.length;
        MyStream inputStream = new MyStream(this.input_stream);

        // if current_sample_block is beyond last block then finished
        if (this.current_sample_block >= this.demux_res.sample_byte_size.length) {
            return -1;
        }

        this.demux_res.get_sample_info(this.current_sample_block, sampleinfo);

        int sample_byte_size = sampleinfo.sample_byte_size;

        inputStream.read(sample_byte_size, read_buffer, 0);

        // now fetch
        int outputBytes = destBufferSize;

        outputBytes = this.file.decodeFrame(read_buffer, pDestBuffer, outputBytes);

        this.current_sample_block = this.current_sample_block + 1;
        outputBytes -= this.offset * this.getBytesPerSample();
        System.arraycopy(pDestBuffer, this.offset, pDestBuffer, 0, outputBytes);
        this.offset = 0;
        return outputBytes;
    }

    /** */
    public void close() throws IOException {
        if (null != this.input_stream) {
            this.input_stream.close();
        }
    }
}
