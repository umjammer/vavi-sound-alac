/*
 * Copyright (c) 2011 Peter McQuillan
 *
 * All Rights Reserved.
 *
 * Distributed under the BSD Software License (see license.txt)
 */

package com.beatofthedrum.alacdecoder;


import java.util.logging.Logger;


/**
 * AlacFile.
 */
class AlacFile {

    private static final Logger logger = Logger.getLogger(AlacFile.class.getName());

    static int RICE_THRESHOLD = 8;
    byte[] input_buffer;
    int ibIdx = 0;
    /** used so we can do arbitrary bit reads */
    int input_buffer_bitaccumulator = 0;

    int samplesize = 0;
    int numchannels = 0;
    int bytespersample = 0;

    LeadingZeros lz = new LeadingZeros();

    private static final int buffer_size = 16384;

    // buffers
    int[] predicterror_buffer_a = new int[buffer_size];
    int[] predicterror_buffer_b = new int[buffer_size];

    int[] outputsamples_buffer_a = new int[buffer_size];
    int[] outputsamples_buffer_b = new int[buffer_size];

    int[] uncompressed_bytes_buffer_a = new int[buffer_size];
    int[] uncompressed_bytes_buffer_b = new int[buffer_size];

    // stuff from setinfo

    /** max samples per frame? */
    int setinfo_max_samples_per_frame = 0; // 0x1000 = 4096
    int setinfo_7a = 0; // 0x00
    int setinfo_sample_size = 0; // 0x10
    int setinfo_rice_historymult = 0; // 0x28
    int setinfo_rice_initialhistory = 0; // 0x0a
    int setinfo_rice_kmodifier = 0; // 0x0e
    int setinfo_7f = 0; // 0x02
    int setinfo_80 = 0; // 0x00ff
    /** max sample size?? */
    int setinfo_82 = 0; // 0x000020e7
    /** bit rate (avarge)?? */
    int setinfo_86 = 0; // 0x00069fe4
    /** end setinfo stuff */
    int setinfo_8a_rate = 0; // 0x0000ac44

    public int[] predictor_coef_table = new int[1024];
    public int[] predictor_coef_table_a = new int[1024];
    public int[] predictor_coef_table_b = new int[1024];

    // stream reading

    private static int[] predictor_decompress_fir_adapt(int[] error_buffer, int output_size, int readsamplesize, int[] predictor_coef_table, int predictor_coef_num, int predictor_quantitization) {
        int buffer_out_idx = 0;
        int[] buffer_out;
        int bitsmove = 0;

        // first sample always copies
        buffer_out = error_buffer;

        if (predictor_coef_num == 0) {
            if (output_size <= 1)
                return (buffer_out);
            int sizeToCopy = 0;
            sizeToCopy = (output_size - 1) * 4;
            System.arraycopy(error_buffer, 1, buffer_out, 1, sizeToCopy);
            return (buffer_out);
        }

        if (predictor_coef_num == 0x1f) { // 11111 - max value of predictor_coef_num
            // second-best case scenario for fir decompression,
			// error describes a small difference from the previous sample only
            if (output_size <= 1)
                return (buffer_out);

            for (int i = 0; i < (output_size - 1); i++) {
                int prev_value = 0;
                int error_value = 0;

                prev_value = buffer_out[i];
                error_value = error_buffer[i + 1];

                bitsmove = 32 - readsamplesize;
                buffer_out[i + 1] = (((prev_value + error_value) << bitsmove) >> bitsmove);
            }
            return (buffer_out);
        }

        // read warm-up samples
        if (predictor_coef_num > 0) {
            for (int i = 0; i < predictor_coef_num; i++) {
                int val = 0;

                val = buffer_out[i] + error_buffer[i + 1];

                bitsmove = 32 - readsamplesize;

                val = ((val << bitsmove) >> bitsmove);

                buffer_out[i + 1] = val;
            }
        }

        // general case
        if (predictor_coef_num > 0) {
            buffer_out_idx = 0;
            for (int i = predictor_coef_num + 1; i < output_size; i++) {
                int j;
                int sum = 0;
                int outval;
                int error_val = error_buffer[i];

                for (j = 0; j < predictor_coef_num; j++) {
                    sum += (buffer_out[buffer_out_idx + predictor_coef_num - j] - buffer_out[buffer_out_idx]) * predictor_coef_table[j];
                }

                outval = (1 << (predictor_quantitization - 1)) + sum;
                outval = outval >> predictor_quantitization;
                outval = outval + buffer_out[buffer_out_idx] + error_val;
                bitsmove = 32 - readsamplesize;

                outval = ((outval << bitsmove) >> bitsmove);

                buffer_out[buffer_out_idx + predictor_coef_num + 1] = outval;

                if (error_val > 0) {
                    int predictor_num = predictor_coef_num - 1;

                    while (predictor_num >= 0 && error_val > 0) {
                        int val = buffer_out[buffer_out_idx] - buffer_out[buffer_out_idx + predictor_coef_num - predictor_num];
                        int sign = (Integer.compare(val, 0));

                        predictor_coef_table[predictor_num] -= sign;

                        val *= sign; // absolute value

                        error_val -= ((val >> predictor_quantitization) * (predictor_coef_num - predictor_num));

                        predictor_num--;
                    }
                } else if (error_val < 0) {
                    int predictor_num = predictor_coef_num - 1;

                    while (predictor_num >= 0 && error_val < 0) {
                        int val = buffer_out[buffer_out_idx] - buffer_out[buffer_out_idx + predictor_coef_num - predictor_num];
                        int sign = -(Integer.compare(val, 0));

                        predictor_coef_table[predictor_num] -= sign;

                        val *= sign; // neg value

                        error_val -= ((val >> predictor_quantitization) * (predictor_coef_num - predictor_num));

                        predictor_num--;
                    }
                }

                buffer_out_idx++;
            }
        }
        return buffer_out;
    }

    private static void deinterlace_16(int[] buffer_a, int[] buffer_b, int[] buffer_out, int numchannels, int numsamples, int interlacing_shift, int interlacing_leftweight) {

        if (numsamples <= 0)
            return;

        // weighted interlacing
        if (0 != interlacing_leftweight) {
            for (int i = 0; i < numsamples; i++) {
                int difference = 0;
                int midright = 0;
                int left = 0;
                int right = 0;

                midright = buffer_a[i];
                difference = buffer_b[i];

                right = (midright - ((difference * interlacing_leftweight) >> interlacing_shift));
                left = (right + difference);

                // output is always little endian

                buffer_out[i * numchannels] = left;
                buffer_out[i * numchannels + 1] = right;
            }

            return;
        }

        // otherwise basic interlacing took place
        for (int i = 0; i < numsamples; i++) {
            int left = 0;
            int right = 0;

            left = buffer_a[i];
            right = buffer_b[i];

            // output is always little endian

            buffer_out[i * numchannels] = left;
            buffer_out[i * numchannels + 1] = right;
        }
    }

    private static void deinterlace_24(int[] buffer_a, int[] buffer_b, int uncompressed_bytes, int[] uncompressed_bytes_buffer_a, int[] uncompressed_bytes_buffer_b, int[] buffer_out, int numchannels, int numsamples, int interlacing_shift, int interlacing_leftweight) {
        if (numsamples <= 0)
            return;

        // weighted interlacing
        if (interlacing_leftweight != 0) {
            for (int i = 0; i < numsamples; i++) {
                int difference = 0;
                int midright = 0;
                int left = 0;
                int right = 0;

                midright = buffer_a[i];
                difference = buffer_b[i];

                right = midright - ((difference * interlacing_leftweight) >> interlacing_shift);
                left = right + difference;

                if (uncompressed_bytes != 0) {
                    int mask = ~(0xFFFFFFFF << (uncompressed_bytes * 8));
                    left <<= (uncompressed_bytes * 8);
                    right <<= (uncompressed_bytes * 8);

                    left = left | (uncompressed_bytes_buffer_a[i] & mask);
                    right = right | (uncompressed_bytes_buffer_b[i] & mask);
                }

                buffer_out[i * numchannels * 3] = (left & 0xFF);
                buffer_out[i * numchannels * 3 + 1] = ((left >> 8) & 0xFF);
                buffer_out[i * numchannels * 3 + 2] = ((left >> 16) & 0xFF);

                buffer_out[i * numchannels * 3 + 3] = (right & 0xFF);
                buffer_out[i * numchannels * 3 + 4] = ((right >> 8) & 0xFF);
                buffer_out[i * numchannels * 3 + 5] = ((right >> 16) & 0xFF);
            }

            return;
        }

        // otherwise basic interlacing took place
        for (int i = 0; i < numsamples; i++) {
            int left = 0;
            int right = 0;

            left = buffer_a[i];
            right = buffer_b[i];

            if (uncompressed_bytes != 0) {
                int mask = ~(0xFFFFFFFF << (uncompressed_bytes * 8));
                left <<= (uncompressed_bytes * 8);
                right <<= (uncompressed_bytes * 8);

                left = left | (uncompressed_bytes_buffer_a[i] & mask);
                right = right | (uncompressed_bytes_buffer_b[i] & mask);
            }

            buffer_out[i * numchannels * 3] = (left & 0xFF);
            buffer_out[i * numchannels * 3 + 1] = ((left >> 8) & 0xFF);
            buffer_out[i * numchannels * 3 + 2] = ((left >> 16) & 0xFF);

            buffer_out[i * numchannels * 3 + 3] = (right & 0xFF);
            buffer_out[i * numchannels * 3 + 4] = ((right >> 8) & 0xFF);
            buffer_out[i * numchannels * 3 + 5] = ((right >> 16) & 0xFF);
        }
    }

    public static AlacFile create(int samplesize, int numchannels) {
        AlacFile newfile = new AlacFile();

        newfile.samplesize = samplesize;
        newfile.numchannels = numchannels;
        newfile.bytespersample = (samplesize / 8) * numchannels;

        return newfile;
    }

    public void entropy_rice_decode(int[] outputBuffer, int outputSize, int readSampleSize, int rice_initialhistory, int rice_kmodifier, int rice_historymult, int rice_kmodifier_mask) {
        int history = rice_initialhistory;
        int outputCount = 0;
        int signModifier = 0;

        while (outputCount < outputSize) {
            int decodedValue = 0;
            int finalValue = 0;
            int k = 0;

            k = 31 - rice_kmodifier - this.lz.count_leading_zeros((history >> 9) + 3);

            if (k < 0)
                k += rice_kmodifier;
            else
                k = rice_kmodifier;

            // note: don't use rice_kmodifier_mask here (set mask to 0xFFFFFFFF)
            decodedValue = entropy_decode_value(readSampleSize, k, 0xFFFFFFFF);

            decodedValue += signModifier;
            finalValue = ((decodedValue + 1) / 2); // inc by 1 and shift out sign bit
            if ((decodedValue & 1) != 0) // the sign is stored in the low bit
                finalValue *= -1;

            outputBuffer[outputCount] = finalValue;

            signModifier = 0;

            // update history
            history += (decodedValue * rice_historymult) - ((history * rice_historymult) >> 9);

            if (decodedValue > 0xFFFF)
                history = 0xFFFF;

            // special case, for compressed blocks of 0
            if ((history < 128) && (outputCount + 1 < outputSize)) {
                int blockSize = 0;

                signModifier = 1;

                k = this.lz.count_leading_zeros(history) + ((history + 16) / 64) - 24;

                // note: blockSize is always 16bit
                blockSize = entropy_decode_value(16, k, rice_kmodifier_mask);

                // got blockSize 0s
                if (blockSize > 0) {
                    int countSize = 0;
                    countSize = blockSize;
                    for (int j = 0; j < countSize; j++) {
                        outputBuffer[outputCount + 1 + j] = 0;
                    }
                    outputCount += blockSize;
                }

                if (blockSize > 0xFFFF)
                    signModifier = 0;

                history = 0;
            }

            outputCount++;
        }
    }

    /** */
    public int decodeFrame(byte[] inbuffer, int[] outbuffer, int outputsize) {
        int channels;
        int outputSamples = this.setinfo_max_samples_per_frame;

        // setup the stream
        this.input_buffer = inbuffer;
        this.input_buffer_bitaccumulator = 0;
        this.ibIdx = 0;

        channels = readbits(3);

        outputsize = outputSamples * this.bytespersample;

        if (channels == 0) { // 1 channel
            int hassize;
            int isnotcompressed;
            int readsamplesize;

            int uncompressed_bytes;
            int ricemodifier;

            int tempPred = 0;

            // 2^result = something to do with output waiting.
			// perhaps matters if we read > 1 frame in a pass?
            readbits(4);

            readbits(12); // unknown, skip 12 bits

            hassize = readbits(1); // the output sample size is stored soon

            uncompressed_bytes = readbits(2); // number of bytes in the (compressed) stream that are not compressed

            isnotcompressed = readbits(1); // whether the frame is compressed

            if (hassize != 0) {
                // now read the number of samples,
				// as a 32bit integer
                outputSamples = readbits(32);
                outputsize = outputSamples * this.bytespersample;
            }

            readsamplesize = this.setinfo_sample_size - (uncompressed_bytes * 8);

            if (isnotcompressed == 0) { // so it is compressed
                int[] predictor_coef_table = this.predictor_coef_table;
                int predictor_coef_num;
                int prediction_type;
                int prediction_quantitization;

                // skip 16 bits, not sure what they are. seem to be used in
                // two channel case
                readbits(8);
                readbits(8);

                prediction_type = readbits(4);
                prediction_quantitization = readbits(4);

                ricemodifier = readbits(3);
                predictor_coef_num = readbits(5);

                // read the predictor table

                for (int i = 0; i < predictor_coef_num; i++) {
                    tempPred = readbits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }

                    predictor_coef_table[i] = tempPred;
                }

                if (uncompressed_bytes != 0) {
                    for (int i = 0; i < outputSamples; i++) {
                        this.uncompressed_bytes_buffer_a[i] = readbits(uncompressed_bytes * 8);
                    }
                }

                this.entropy_rice_decode(this.predicterror_buffer_a, outputSamples, readsamplesize, this.setinfo_rice_initialhistory, this.setinfo_rice_kmodifier, ricemodifier * (this.setinfo_rice_historymult / 4), (1 << this.setinfo_rice_kmodifier) - 1);

                if (prediction_type == 0) { // adaptive fir
                    this.outputsamples_buffer_a = predictor_decompress_fir_adapt(this.predicterror_buffer_a, outputSamples, readsamplesize, predictor_coef_table, predictor_coef_num, prediction_quantitization);
                } else {
                    logger.warning("FIXME: unhandled predicition type: " + prediction_type);

                    // i think the only other prediction type (or perhaps this is just a
					// boolean?) runs adaptive fir twice.. like:
					// predictor_decompress_fir_adapt(predictor_error, tempout, ...)
					// predictor_decompress_fir_adapt(predictor_error, outputsamples ...)
					// little strange..
                }

            } else { // not compressed, easy case
                if (this.setinfo_sample_size <= 16) {
                    int bitsmove;
                    for (int i = 0; i < outputSamples; i++) {
                        int audiobits = readbits(this.setinfo_sample_size);
                        bitsmove = 32 - this.setinfo_sample_size;

                        audiobits = ((audiobits << bitsmove) >> bitsmove);

                        this.outputsamples_buffer_a[i] = audiobits;
                    }
                } else {
                    int m = 1 << (24 - 1);
                    for (int i = 0; i < outputSamples; i++) {
                        int audiobits;

                        audiobits = readbits(16);
                        // special case of sign extension..
						// as we'll be ORing the low 16bits into this
                        audiobits = audiobits << (this.setinfo_sample_size - 16);
                        audiobits = audiobits | readbits(this.setinfo_sample_size - 16);
                        int x = audiobits & ((1 << 24) - 1);
                        audiobits = (x ^ m) - m; // sign extend 24 bits

                        this.outputsamples_buffer_a[i] = audiobits;
                    }
                }
                uncompressed_bytes = 0; // always 0 for uncompressed
            }

            switch (this.setinfo_sample_size) {
            case 16: {

                for (int i = 0; i < outputSamples; i++) {
                    int sample = this.outputsamples_buffer_a[i];
                    outbuffer[i * this.numchannels] = sample;

                    // We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
                    // in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
                    // will be overwritten in the next iteration

                    outbuffer[(i * this.numchannels) + 1] = 0;
                }
                break;
            }
            case 24: {
                for (int i = 0; i < outputSamples; i++) {
                    int sample = this.outputsamples_buffer_a[i];

                    if (uncompressed_bytes != 0) {
                        int mask = 0;
                        sample = sample << (uncompressed_bytes * 8);
                        mask = ~(0xffff_ffff << (uncompressed_bytes * 8));
                        sample = sample | (this.uncompressed_bytes_buffer_a[i] & mask);
                    }

                    outbuffer[i * this.numchannels * 3] = ((sample) & 0xFF);
                    outbuffer[i * this.numchannels * 3 + 1] = ((sample >> 8) & 0xFF);
                    outbuffer[i * this.numchannels * 3 + 2] = ((sample >> 16) & 0xFF);

                    // We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
					// in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
					// will be overwritten in the next iteration

                    outbuffer[i * this.numchannels * 3 + 3] = 0;
                    outbuffer[i * this.numchannels * 3 + 4] = 0;
                    outbuffer[i * this.numchannels * 3 + 5] = 0;

                }
                break;
            }
            case 20:
            case 32:
                logger.warning("FIXME: unimplemented sample size " + this.setinfo_sample_size);
            default:
            }
        } else if (channels == 1) { // 2 channels
            int hassize;
            int isnotcompressed;
            int readsamplesize;

            int uncompressed_bytes;

            int interlacing_shift;
            int interlacing_leftweight;

            // 2^result = something to do with output waiting.
			// perhaps matters if we read > 1 frame in a pass?
            readbits(4);

            readbits(12); // unknown, skip 12 bits

            hassize = readbits(1); // the output sample size is stored soon

            uncompressed_bytes = readbits(2); // the number of bytes in the (compressed) stream that are not compressed

            isnotcompressed = readbits(1); // whether the frame is compressed

            if (hassize != 0) {
                // now read the number of samples,
                // as a 32bit integer
                outputSamples = readbits(32);
                outputsize = outputSamples * this.bytespersample;
            }

            readsamplesize = this.setinfo_sample_size - (uncompressed_bytes * 8) + 1;

            if (isnotcompressed == 0) { // compressed
                int[] predictor_coef_table_a = this.predictor_coef_table_a;
                int predictor_coef_num_a;
                int prediction_type_a;
                int prediction_quantitization_a;
                int ricemodifier_a;

                int[] predictor_coef_table_b = this.predictor_coef_table_b;
                int predictor_coef_num_b;
                int prediction_type_b;
                int prediction_quantitization_b;
                int ricemodifier_b;

                int tempPred = 0;

                interlacing_shift = readbits(8);
                interlacing_leftweight = readbits(8);

                // channel 1
                prediction_type_a = readbits(4);
                prediction_quantitization_a = readbits(4);

                ricemodifier_a = readbits(3);
                predictor_coef_num_a = readbits(5);

                // read the predictor table

                for (int i = 0; i < predictor_coef_num_a; i++) {
                    tempPred = readbits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictor_coef_table_a[i] = tempPred;
                }

                // channel 2
                prediction_type_b = readbits(4);
                prediction_quantitization_b = readbits(4);

                ricemodifier_b = readbits(3);
                predictor_coef_num_b = readbits(5);

                // read the predictor table

                for (int i = 0; i < predictor_coef_num_b; i++) {
                    tempPred = readbits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictor_coef_table_b[i] = tempPred;
                }

                //
                if (uncompressed_bytes != 0) {
                    // see mono case
                    for (int i = 0; i < outputSamples; i++) {
                        this.uncompressed_bytes_buffer_a[i] = readbits(uncompressed_bytes * 8);
                        this.uncompressed_bytes_buffer_b[i] = readbits(uncompressed_bytes * 8);
                    }
                }

                // channel 1

                this.entropy_rice_decode(this.predicterror_buffer_a, outputSamples, readsamplesize, this.setinfo_rice_initialhistory, this.setinfo_rice_kmodifier, ricemodifier_a * (this.setinfo_rice_historymult / 4), (1 << this.setinfo_rice_kmodifier) - 1);

                if (prediction_type_a == 0) { // adaptive fir

                    this.outputsamples_buffer_a = predictor_decompress_fir_adapt(this.predicterror_buffer_a, outputSamples, readsamplesize, predictor_coef_table_a, predictor_coef_num_a, prediction_quantitization_a);

                } else { // see mono case
                    logger.warning("FIXME: unhandled predicition type: " + prediction_type_a);
                }

                // channel 2
                this.entropy_rice_decode(this.predicterror_buffer_b, outputSamples, readsamplesize, this.setinfo_rice_initialhistory, this.setinfo_rice_kmodifier, ricemodifier_b * (this.setinfo_rice_historymult / 4), (1 << this.setinfo_rice_kmodifier) - 1);

                if (prediction_type_b == 0) { // adaptive fir
                    this.outputsamples_buffer_b = predictor_decompress_fir_adapt(this.predicterror_buffer_b, outputSamples, readsamplesize, predictor_coef_table_b, predictor_coef_num_b, prediction_quantitization_b);
                } else {
                    logger.warning("FIXME: unhandled predicition type: " + prediction_type_b);
                }
            } else { // not compressed, easy case
                if (this.setinfo_sample_size <= 16) {
                    int bitsmove;

                    for (int i = 0; i < outputSamples; i++) {
                        int audiobits_a;
                        int audiobits_b;

                        audiobits_a = readbits(this.setinfo_sample_size);
                        audiobits_b = readbits(this.setinfo_sample_size);

                        bitsmove = 32 - this.setinfo_sample_size;

                        audiobits_a = ((audiobits_a << bitsmove) >> bitsmove);
                        audiobits_b = ((audiobits_b << bitsmove) >> bitsmove);

                        this.outputsamples_buffer_a[i] = audiobits_a;
                        this.outputsamples_buffer_b[i] = audiobits_b;
                    }
                } else {
                    int x;
                    int m = 1 << (24 - 1);

                    for (int i = 0; i < outputSamples; i++) {
                        int audiobits_a;
                        int audiobits_b;

                        audiobits_a = readbits(16);
                        audiobits_a = audiobits_a << (this.setinfo_sample_size - 16);
                        audiobits_a = audiobits_a | readbits(this.setinfo_sample_size - 16);
                        x = audiobits_a & ((1 << 24) - 1);
                        audiobits_a = (x ^ m) - m; // sign extend 24 bits

                        audiobits_b = readbits(16);
                        audiobits_b = audiobits_b << (this.setinfo_sample_size - 16);
                        audiobits_b = audiobits_b | readbits(this.setinfo_sample_size - 16);
                        x = audiobits_b & ((1 << 24) - 1);
                        audiobits_b = (x ^ m) - m; // sign extend 24 bits

                        this.outputsamples_buffer_a[i] = audiobits_a;
                        this.outputsamples_buffer_b[i] = audiobits_b;
                    }
                }
                uncompressed_bytes = 0; // always 0 for uncompressed
                interlacing_shift = 0;
                interlacing_leftweight = 0;
            }

            switch (this.setinfo_sample_size) {
            case 16: {
                deinterlace_16(this.outputsamples_buffer_a, this.outputsamples_buffer_b, outbuffer, this.numchannels, outputSamples, interlacing_shift, interlacing_leftweight);
                break;
            }
            case 24: {
                deinterlace_24(this.outputsamples_buffer_a, this.outputsamples_buffer_b, uncompressed_bytes, this.uncompressed_bytes_buffer_a, this.uncompressed_bytes_buffer_b, outbuffer, this.numchannels, outputSamples, interlacing_shift, interlacing_leftweight);
                break;
            }
            case 20:
            case 32:
            default:
                logger.warning("FIXME: unimplemented sample size " + this.setinfo_sample_size);
            }
        }
        return outputsize;
    }

    public int entropy_decode_value(int readSampleSize, int k, int rice_kmodifier_mask) {
        int x = 0; // decoded value

        // read x, number of 1s before 0 represent the rice value.
        while (x <= RICE_THRESHOLD && readbit() != 0) {
            x++;
        }

        if (x > RICE_THRESHOLD) {
            // read the number from the bit stream (raw value)
            int value = 0;

            value = readbits(readSampleSize);

            // mask value
            value &= ((0xffff_ffff) >> (32 - readSampleSize));

            x = value;
        } else {
            if (k != 1) {
                int extraBits = readbits(k);

                x *= (((1 << k) - 1) & rice_kmodifier_mask);

                if (extraBits > 1)
                    x += extraBits - 1;
                else
                    unreadbits(1);
            }
        }

        return x;
    }

    void unreadbits(int bits) {
        int new_accumulator = (this.input_buffer_bitaccumulator - bits);

        this.ibIdx += (new_accumulator >> 3);

        this.input_buffer_bitaccumulator = (new_accumulator & 7);
        if (this.input_buffer_bitaccumulator < 0)
            this.input_buffer_bitaccumulator *= -1;
    }

    /** reads a single bit */
    int readbit() {
        int result = 0;
        int new_accumulator = 0;
        int part1 = 0;

        part1 = (this.input_buffer[this.ibIdx] & 0xff);

        result = part1;

        result = result << this.input_buffer_bitaccumulator;

        result = result >> 7 & 1;

        new_accumulator = (this.input_buffer_bitaccumulator + 1);

        this.ibIdx += new_accumulator / 8;

        this.input_buffer_bitaccumulator = (new_accumulator % 8);

        return result;
    }

    /** supports reading 1 to 32 bits, in big endian format */
    int readbits(int bits) {
        int result = 0;

        if (bits > 16) {
            bits -= 16;

            result = readbits_16(16) << bits;
        }

        result |= readbits_16(bits);

        return result;
    }

    /** supports reading 1 to 16 bits, in big endian format */
    int readbits_16(int bits) {
        int result = 0;
        int new_accumulator = 0;
        int part1 = 0;
        int part2 = 0;
        int part3 = 0;

        part1 = (this.input_buffer[this.ibIdx] & 0xff);
        part2 = (this.input_buffer[this.ibIdx + 1] & 0xff);
        part3 = (this.input_buffer[this.ibIdx + 2] & 0xff);

        result = ((part1 << 16) | (part2 << 8) | part3);

        // shift left by the number of bits we've already read,
		// so that the top 'n' bits of the 24 bits we read will
		// be the return bits
        result = result << this.input_buffer_bitaccumulator;

        result = result & 0x00ffffff;

        // and then only want the top 'n' bits from that, where
		// n is 'bits'
        result = result >> (24 - bits);

        new_accumulator = (this.input_buffer_bitaccumulator + bits);

        // increase the buffer pointer if we've read over n bytes.
        this.ibIdx += (new_accumulator >> 3);

        // and the remainder goes back into the bit accumulator
        this.input_buffer_bitaccumulator = (new_accumulator & 7);

        return result;
    }

    public void alac_set_info(int[] inputbuffer) {
        int ptrIndex = 0;
        ptrIndex += 4; // size
        ptrIndex += 4; // frma
        ptrIndex += 4; // file
        ptrIndex += 4; // size
        ptrIndex += 4; // file

        ptrIndex += 4; // 0 ?

        this.setinfo_max_samples_per_frame = ((inputbuffer[ptrIndex] << 24) + (inputbuffer[ptrIndex + 1] << 16) + (inputbuffer[ptrIndex + 2] << 8) + inputbuffer[ptrIndex + 3]); // buffer size / 2 ?
        ptrIndex += 4;
        this.setinfo_7a = inputbuffer[ptrIndex];
        ptrIndex += 1;
        this.setinfo_sample_size = inputbuffer[ptrIndex];
        ptrIndex += 1;
        this.setinfo_rice_historymult = (inputbuffer[ptrIndex] & 0xff);
        ptrIndex += 1;
        this.setinfo_rice_initialhistory = (inputbuffer[ptrIndex] & 0xff);
        ptrIndex += 1;
        this.setinfo_rice_kmodifier = (inputbuffer[ptrIndex] & 0xff);
        ptrIndex += 1;
        this.setinfo_7f = inputbuffer[ptrIndex];
        ptrIndex += 1;
        this.setinfo_80 = (inputbuffer[ptrIndex] << 8) + inputbuffer[ptrIndex + 1];
        ptrIndex += 2;
        this.setinfo_82 = ((inputbuffer[ptrIndex] << 24) + (inputbuffer[ptrIndex + 1] << 16) + (inputbuffer[ptrIndex + 2] << 8) + inputbuffer[ptrIndex + 3]);
        ptrIndex += 4;
        this.setinfo_86 = ((inputbuffer[ptrIndex] << 24) + (inputbuffer[ptrIndex + 1] << 16) + (inputbuffer[ptrIndex + 2] << 8) + inputbuffer[ptrIndex + 3]);
        ptrIndex += 4;
        this.setinfo_8a_rate = ((inputbuffer[ptrIndex] << 24) + (inputbuffer[ptrIndex + 1] << 16) + (inputbuffer[ptrIndex + 2] << 8) + inputbuffer[ptrIndex + 3]);
        ptrIndex += 4;
    }
}