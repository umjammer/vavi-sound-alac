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
    byte[] inputBuffer;
    int ibIndex = 0;
    /** used so we can do arbitrary bit reads */
    int inputBufferBitAccumulator = 0;

    int sampleSize = 0;
    int numChannels = 0;
    int bytesPerSample = 0;

    LeadingZeros lz = new LeadingZeros();

    private static final int bufferSize = 16384;

    // buffers
    int[] predicterrorBufferA = new int[bufferSize];
    int[] predicterrorBufferB = new int[bufferSize];

    int[] outputSamplesBufferA = new int[bufferSize];
    int[] outputsamplesBufferB = new int[bufferSize];

    int[] uncompressedBytesBufferA = new int[bufferSize];
    int[] uncompressedBytesBufferB = new int[bufferSize];

    // stuff from setinfo

    /** max samples per frame? */
    int setInfo_maxSamplesPerFrame = 0; // 0x1000 = 4096
    int setInfo_7A = 0; // 0x00
    int setInfo_sampleSize = 0; // 0x10
    int setInfo_riceHistoryMult = 0; // 0x28
    int setInfo_riceInitialHistory = 0; // 0x0a
    int setInfo_riceKModifier = 0; // 0x0e
    int setInfo_7f = 0; // 0x02
    int setInfo_80 = 0; // 0x00ff
    /** max sample size?? */
    int setInfo_82 = 0; // 0x000020e7
    /** bit rate (avarge)?? */
    int setInfo_86 = 0; // 0x00069fe4
    /** end setinfo stuff */
    int setInfo_8a_rate = 0; // 0x0000ac44

    public int[] predictorCoefTable = new int[1024];
    public int[] predictorCoefTableA = new int[1024];
    public int[] predictorCoefTableB = new int[1024];

    // stream reading

    private static int[] predictorDecompressFirAdapt(int[] errorBuffer, int outputSize, int readSampleSize, int[] predictorCoefTable, int predictorCoefNum, int predictorQuantitization) {
        int bufferOutIndex = 0;
        int[] bufferOut;
        int bitsMove = 0;

        // first sample always copies
        bufferOut = errorBuffer;

        if (predictorCoefNum == 0) {
            if (outputSize <= 1)
                return (bufferOut);
            int sizeToCopy = 0;
            sizeToCopy = (outputSize - 1) * 4;
            System.arraycopy(errorBuffer, 1, bufferOut, 1, sizeToCopy);
            return (bufferOut);
        }

        if (predictorCoefNum == 0x1f) { // 11111 - max value of predictorCoefNum
            // second-best case scenario for fir decompression,
			// error describes a small difference from the previous sample only
            if (outputSize <= 1)
                return (bufferOut);

            for (int i = 0; i < (outputSize - 1); i++) {
                int prevValue = 0;
                int errorValue = 0;

                prevValue = bufferOut[i];
                errorValue = errorBuffer[i + 1];

                bitsMove = 32 - readSampleSize;
                bufferOut[i + 1] = (((prevValue + errorValue) << bitsMove) >> bitsMove);
            }
            return (bufferOut);
        }

        // read warm-up samples
        if (predictorCoefNum > 0) {
            for (int i = 0; i < predictorCoefNum; i++) {
                int val = 0;

                val = bufferOut[i] + errorBuffer[i + 1];

                bitsMove = 32 - readSampleSize;

                val = ((val << bitsMove) >> bitsMove);

                bufferOut[i + 1] = val;
            }
        }

        // general case
        if (predictorCoefNum > 0) {
            bufferOutIndex = 0;
            for (int i = predictorCoefNum + 1; i < outputSize; i++) {
                int j;
                int sum = 0;
                int outVal;
                int errorVal = errorBuffer[i];

                for (j = 0; j < predictorCoefNum; j++) {
                    sum += (bufferOut[bufferOutIndex + predictorCoefNum - j] - bufferOut[bufferOutIndex]) * predictorCoefTable[j];
                }

                outVal = (1 << (predictorQuantitization - 1)) + sum;
                outVal = outVal >> predictorQuantitization;
                outVal = outVal + bufferOut[bufferOutIndex] + errorVal;
                bitsMove = 32 - readSampleSize;

                outVal = ((outVal << bitsMove) >> bitsMove);

                bufferOut[bufferOutIndex + predictorCoefNum + 1] = outVal;

                if (errorVal > 0) {
                    int predictorNum = predictorCoefNum - 1;

                    while (predictorNum >= 0 && errorVal > 0) {
                        int val = bufferOut[bufferOutIndex] - bufferOut[bufferOutIndex + predictorCoefNum - predictorNum];
                        int sign = (Integer.compare(val, 0));

                        predictorCoefTable[predictorNum] -= sign;

                        val *= sign; // absolute value

                        errorVal -= ((val >> predictorQuantitization) * (predictorCoefNum - predictorNum));

                        predictorNum--;
                    }
                } else if (errorVal < 0) {
                    int predictorNum = predictorCoefNum - 1;

                    while (predictorNum >= 0 && errorVal < 0) {
                        int val = bufferOut[bufferOutIndex] - bufferOut[bufferOutIndex + predictorCoefNum - predictorNum];
                        int sign = -(Integer.compare(val, 0));

                        predictorCoefTable[predictorNum] -= sign;

                        val *= sign; // neg value

                        errorVal -= ((val >> predictorQuantitization) * (predictorCoefNum - predictorNum));

                        predictorNum--;
                    }
                }

                bufferOutIndex++;
            }
        }
        return bufferOut;
    }

    private static void deinterlace16(int[] bufferA, int[] bufferB, int[] bufferOut, int numChannels, int numSamples, int interlacingShift, int interlacingLeftWeight) {

        if (numSamples <= 0)
            return;

        // weighted interlacing
        if (0 != interlacingLeftWeight) {
            for (int i = 0; i < numSamples; i++) {
                int difference = 0;
                int midright = 0;
                int left = 0;
                int right = 0;

                midright = bufferA[i];
                difference = bufferB[i];

                right = (midright - ((difference * interlacingLeftWeight) >> interlacingShift));
                left = (right + difference);

                // output is always little endian

                bufferOut[i * numChannels] = left;
                bufferOut[i * numChannels + 1] = right;
            }

            return;
        }

        // otherwise basic interlacing took place
        for (int i = 0; i < numSamples; i++) {
            int left = 0;
            int right = 0;

            left = bufferA[i];
            right = bufferB[i];

            // output is always little endian

            bufferOut[i * numChannels] = left;
            bufferOut[i * numChannels + 1] = right;
        }
    }

    private static void deinterlace24(int[] bufferA, int[] bufferB, int uncompressedBytes,
                                      int[] uncompressedBytesBufferA, int[] uncompressedBytesBufferB, int[] bufferOut,
                                      int numChannels, int numSamples, int interlacingShift, int interlacingLeftWeight) {
        if (numSamples <= 0)
            return;

        // weighted interlacing
        if (interlacingLeftWeight != 0) {
            for (int i = 0; i < numSamples; i++) {
                int difference = 0;
                int midright = 0;
                int left = 0;
                int right = 0;

                midright = bufferA[i];
                difference = bufferB[i];

                right = midright - ((difference * interlacingLeftWeight) >> interlacingShift);
                left = right + difference;

                if (uncompressedBytes != 0) {
                    int mask = ~(0xFFFFFFFF << (uncompressedBytes * 8));
                    left <<= (uncompressedBytes * 8);
                    right <<= (uncompressedBytes * 8);

                    left = left | (uncompressedBytesBufferA[i] & mask);
                    right = right | (uncompressedBytesBufferB[i] & mask);
                }

                bufferOut[i * numChannels * 3] = (left & 0xFF);
                bufferOut[i * numChannels * 3 + 1] = ((left >> 8) & 0xFF);
                bufferOut[i * numChannels * 3 + 2] = ((left >> 16) & 0xFF);

                bufferOut[i * numChannels * 3 + 3] = (right & 0xFF);
                bufferOut[i * numChannels * 3 + 4] = ((right >> 8) & 0xFF);
                bufferOut[i * numChannels * 3 + 5] = ((right >> 16) & 0xFF);
            }

            return;
        }

        // otherwise basic interlacing took place
        for (int i = 0; i < numSamples; i++) {
            int left = 0;
            int right = 0;

            left = bufferA[i];
            right = bufferB[i];

            if (uncompressedBytes != 0) {
                int mask = ~(0xFFFFFFFF << (uncompressedBytes * 8));
                left <<= (uncompressedBytes * 8);
                right <<= (uncompressedBytes * 8);

                left = left | (uncompressedBytesBufferA[i] & mask);
                right = right | (uncompressedBytesBufferB[i] & mask);
            }

            bufferOut[i * numChannels * 3] = (left & 0xFF);
            bufferOut[i * numChannels * 3 + 1] = ((left >> 8) & 0xFF);
            bufferOut[i * numChannels * 3 + 2] = ((left >> 16) & 0xFF);

            bufferOut[i * numChannels * 3 + 3] = (right & 0xFF);
            bufferOut[i * numChannels * 3 + 4] = ((right >> 8) & 0xFF);
            bufferOut[i * numChannels * 3 + 5] = ((right >> 16) & 0xFF);
        }
    }

    public static AlacFile create(int sampleSize, int numChannels) {
        AlacFile newFile = new AlacFile();

        newFile.sampleSize = sampleSize;
        newFile.numChannels = numChannels;
        newFile.bytesPerSample = (sampleSize / 8) * numChannels;

        return newFile;
    }

    public void decodeEntropyRice(int[] outputBuffer, int outputSize, int readSampleSize, int riceInitialHistory,
                                  int riceKModifier, int riceHistoryMult, int riceKModifierMask) {
        int history = riceInitialHistory;
        int outputCount = 0;
        int signModifier = 0;

        while (outputCount < outputSize) {
            int decodedValue = 0;
            int finalValue = 0;
            int k = 0;

            k = 31 - riceKModifier - this.lz.countLeadingZeros((history >> 9) + 3);

            if (k < 0)
                k += riceKModifier;
            else
                k = riceKModifier;

            // note: don't use riceKModifierMask here (set mask to 0xFFFFFFFF)
            decodedValue = decodeEntropyValue(readSampleSize, k, 0xFFFFFFFF);

            decodedValue += signModifier;
            finalValue = ((decodedValue + 1) / 2); // inc by 1 and shift out sign bit
            if ((decodedValue & 1) != 0) // the sign is stored in the low bit
                finalValue *= -1;

            outputBuffer[outputCount] = finalValue;

            signModifier = 0;

            // update history
            history += (decodedValue * riceHistoryMult) - ((history * riceHistoryMult) >> 9);

            if (decodedValue > 0xFFFF)
                history = 0xFFFF;

            // special case, for compressed blocks of 0
            if ((history < 128) && (outputCount + 1 < outputSize)) {
                int blockSize = 0;

                signModifier = 1;

                k = this.lz.countLeadingZeros(history) + ((history + 16) / 64) - 24;

                // note: blockSize is always 16bit
                blockSize = decodeEntropyValue(16, k, riceKModifierMask);

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
    public int decodeFrame(byte[] inBuffer, int[] outBuffer, int outputSize) {
        int channels;
        int outputSamples = this.setInfo_maxSamplesPerFrame;

        // setup the stream
        this.inputBuffer = inBuffer;
        this.inputBufferBitAccumulator = 0;
        this.ibIndex = 0;

        channels = readBits(3);

        outputSize = outputSamples * this.bytesPerSample;

        if (channels == 0) { // 1 channel
            int hasSize;
            int isNotCompressed;
            int readSampleSize;

            int uncompressedBytes;
            int riceModifier;

            int tempPred = 0;

            // 2^result = something to do with output waiting.
			// perhaps matters if we read > 1 frame in a pass?
            readBits(4);

            readBits(12); // unknown, skip 12 bits

            hasSize = readBits(1); // the output sample size is stored soon

            uncompressedBytes = readBits(2); // number of bytes in the (compressed) stream that are not compressed

            isNotCompressed = readBits(1); // whether the frame is compressed

            if (hasSize != 0) {
                // now read the number of samples,
				// as a 32bit integer
                outputSamples = readBits(32);
                outputSize = outputSamples * this.bytesPerSample;
            }

            readSampleSize = this.setInfo_sampleSize - (uncompressedBytes * 8);

            if (isNotCompressed == 0) { // so it is compressed
                int[] predictorCoefTable = this.predictorCoefTable;
                int predictorCoefNum;
                int predictionType;
                int predictionQuantitization;

                // skip 16 bits, not sure what they are. seem to be used in
                // two channel case
                readBits(8);
                readBits(8);

                predictionType = readBits(4);
                predictionQuantitization = readBits(4);

                riceModifier = readBits(3);
                predictorCoefNum = readBits(5);

                // read the predictor table

                for (int i = 0; i < predictorCoefNum; i++) {
                    tempPred = readBits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16 bit signed
                        tempPred = tempPred - 65536;
                    }

                    predictorCoefTable[i] = tempPred;
                }

                if (uncompressedBytes != 0) {
                    for (int i = 0; i < outputSamples; i++) {
                        this.uncompressedBytesBufferA[i] = readBits(uncompressedBytes * 8);
                    }
                }

                this.decodeEntropyRice(this.predicterrorBufferA, outputSamples, readSampleSize, this.setInfo_riceInitialHistory, this.setInfo_riceKModifier, riceModifier * (this.setInfo_riceHistoryMult / 4), (1 << this.setInfo_riceKModifier) - 1);

                if (predictionType == 0) { // adaptive fir
                    this.outputSamplesBufferA = predictorDecompressFirAdapt(this.predicterrorBufferA, outputSamples, readSampleSize, predictorCoefTable, predictorCoefNum, predictionQuantitization);
                } else {
                    logger.warning("FIXME: unhandled predicition type: " + predictionType);

                    // i think the only other prediction type (or perhaps this is just a
					// boolean?) runs adaptive fir twice.. like:
					// predictor_decompress_fir_adapt(predictor_error, tempout, ...)
					// predictor_decompress_fir_adapt(predictor_error, outputsamples ...)
					// little strange..
                }

            } else { // not compressed, easy case
                if (this.setInfo_sampleSize <= 16) {
                    int bitsmove;
                    for (int i = 0; i < outputSamples; i++) {
                        int audiobits = readBits(this.setInfo_sampleSize);
                        bitsmove = 32 - this.setInfo_sampleSize;

                        audiobits = ((audiobits << bitsmove) >> bitsmove);

                        this.outputSamplesBufferA[i] = audiobits;
                    }
                } else {
                    int m = 1 << (24 - 1);
                    for (int i = 0; i < outputSamples; i++) {
                        int audiobits;

                        audiobits = readBits(16);
                        // special case of sign extension..
						// as we'll be ORing the low 16bits into this
                        audiobits = audiobits << (this.setInfo_sampleSize - 16);
                        audiobits = audiobits | readBits(this.setInfo_sampleSize - 16);
                        int x = audiobits & ((1 << 24) - 1);
                        audiobits = (x ^ m) - m; // sign extend 24 bits

                        this.outputSamplesBufferA[i] = audiobits;
                    }
                }
                uncompressedBytes = 0; // always 0 for uncompressed
            }

            switch (this.setInfo_sampleSize) {
            case 16: {

                for (int i = 0; i < outputSamples; i++) {
                    int sample = this.outputSamplesBufferA[i];
                    outBuffer[i * this.numChannels] = sample;

                    // We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
                    // in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
                    // will be overwritten in the next iteration

                    outBuffer[(i * this.numChannels) + 1] = 0;
                }
                break;
            }
            case 24: {
                for (int i = 0; i < outputSamples; i++) {
                    int sample = this.outputSamplesBufferA[i];

                    if (uncompressedBytes != 0) {
                        int mask = 0;
                        sample = sample << (uncompressedBytes * 8);
                        mask = ~(0xffff_ffff << (uncompressedBytes * 8));
                        sample = sample | (this.uncompressedBytesBufferA[i] & mask);
                    }

                    outBuffer[i * this.numChannels * 3] = ((sample) & 0xFF);
                    outBuffer[i * this.numChannels * 3 + 1] = ((sample >> 8) & 0xFF);
                    outBuffer[i * this.numChannels * 3 + 2] = ((sample >> 16) & 0xFF);

                    // We have to handle the case where the data is actually mono, but the stsd atom says it has 2 channels
					// in this case we create a stereo file where one of the channels is silent. If mono and 1 channel this value
					// will be overwritten in the next iteration

                    outBuffer[i * this.numChannels * 3 + 3] = 0;
                    outBuffer[i * this.numChannels * 3 + 4] = 0;
                    outBuffer[i * this.numChannels * 3 + 5] = 0;

                }
                break;
            }
            case 20:
            case 32:
                logger.warning("FIXME: unimplemented sample size " + this.setInfo_sampleSize);
            default:
            }
        } else if (channels == 1) { // 2 channels
            int hasSize;
            int isNotCompressed;
            int readSampleSize;

            int uncompressedBytes;

            int interlacingShift;
            int interlacingLeftWeight;

            // 2^result = something to do with output waiting.
			// perhaps matters if we read > 1 frame in a pass?
            readBits(4);

            readBits(12); // unknown, skip 12 bits

            hasSize = readBits(1); // the output sample size is stored soon

            uncompressedBytes = readBits(2); // the number of bytes in the (compressed) stream that are not compressed

            isNotCompressed = readBits(1); // whether the frame is compressed

            if (hasSize != 0) {
                // now read the number of samples,
                // as a 32bit integer
                outputSamples = readBits(32);
                outputSize = outputSamples * this.bytesPerSample;
            }

            readSampleSize = this.setInfo_sampleSize - (uncompressedBytes * 8) + 1;

            if (isNotCompressed == 0) { // compressed
                int[] predictorCoefTableA = this.predictorCoefTableA;
                int predictorCoefNumA;
                int predictionTypeA;
                int predictionQuantitizationA;
                int riceModifierA;

                int[] predictorCoefTableB = this.predictorCoefTableB;
                int predictorCoefNumB;
                int predictionTypeB;
                int predictionQuantitizationB;
                int riceModifierB;

                int tempPred = 0;

                interlacingShift = readBits(8);
                interlacingLeftWeight = readBits(8);

                // channel 1
                predictionTypeA = readBits(4);
                predictionQuantitizationA = readBits(4);

                riceModifierA = readBits(3);
                predictorCoefNumA = readBits(5);

                // read the predictor table

                for (int i = 0; i < predictorCoefNumA; i++) {
                    tempPred = readBits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictorCoefTableA[i] = tempPred;
                }

                // channel 2
                predictionTypeB = readBits(4);
                predictionQuantitizationB = readBits(4);

                riceModifierB = readBits(3);
                predictorCoefNumB = readBits(5);

                // read the predictor table

                for (int i = 0; i < predictorCoefNumB; i++) {
                    tempPred = readBits(16);
                    if (tempPred > 32767) {
                        // the predictor coef table values are only 16bit signed
                        tempPred = tempPred - 65536;
                    }
                    predictorCoefTableB[i] = tempPred;
                }

                //
                if (uncompressedBytes != 0) {
                    // see mono case
                    for (int i = 0; i < outputSamples; i++) {
                        this.uncompressedBytesBufferA[i] = readBits(uncompressedBytes * 8);
                        this.uncompressedBytesBufferB[i] = readBits(uncompressedBytes * 8);
                    }
                }

                // channel 1

                this.decodeEntropyRice(this.predicterrorBufferA, outputSamples, readSampleSize, this.setInfo_riceInitialHistory, this.setInfo_riceKModifier, riceModifierA * (this.setInfo_riceHistoryMult / 4), (1 << this.setInfo_riceKModifier) - 1);

                if (predictionTypeA == 0) { // adaptive fir

                    this.outputSamplesBufferA = predictorDecompressFirAdapt(this.predicterrorBufferA, outputSamples, readSampleSize, predictorCoefTableA, predictorCoefNumA, predictionQuantitizationA);

                } else { // see mono case
                    logger.warning("FIXME: unhandled predicition type: " + predictionTypeA);
                }

                // channel 2
                this.decodeEntropyRice(this.predicterrorBufferB, outputSamples, readSampleSize, this.setInfo_riceInitialHistory, this.setInfo_riceKModifier, riceModifierB * (this.setInfo_riceHistoryMult / 4), (1 << this.setInfo_riceKModifier) - 1);

                if (predictionTypeB == 0) { // adaptive fir
                    this.outputsamplesBufferB = predictorDecompressFirAdapt(this.predicterrorBufferB, outputSamples, readSampleSize, predictorCoefTableB, predictorCoefNumB, predictionQuantitizationB);
                } else {
                    logger.warning("FIXME: unhandled predicition type: " + predictionTypeB);
                }
            } else { // not compressed, easy case
                if (this.setInfo_sampleSize <= 16) {
                    int bitsMove;

                    for (int i = 0; i < outputSamples; i++) {
                        int audioBitsA;
                        int audioBitsB;

                        audioBitsA = readBits(this.setInfo_sampleSize);
                        audioBitsB = readBits(this.setInfo_sampleSize);

                        bitsMove = 32 - this.setInfo_sampleSize;

                        audioBitsA = ((audioBitsA << bitsMove) >> bitsMove);
                        audioBitsB = ((audioBitsB << bitsMove) >> bitsMove);

                        this.outputSamplesBufferA[i] = audioBitsA;
                        this.outputsamplesBufferB[i] = audioBitsB;
                    }
                } else {
                    int x;
                    int m = 1 << (24 - 1);

                    for (int i = 0; i < outputSamples; i++) {
                        int audioBitsA;
                        int audioBitsB;

                        audioBitsA = readBits(16);
                        audioBitsA = audioBitsA << (this.setInfo_sampleSize - 16);
                        audioBitsA = audioBitsA | readBits(this.setInfo_sampleSize - 16);
                        x = audioBitsA & ((1 << 24) - 1);
                        audioBitsA = (x ^ m) - m; // sign extend 24 bits

                        audioBitsB = readBits(16);
                        audioBitsB = audioBitsB << (this.setInfo_sampleSize - 16);
                        audioBitsB = audioBitsB | readBits(this.setInfo_sampleSize - 16);
                        x = audioBitsB & ((1 << 24) - 1);
                        audioBitsB = (x ^ m) - m; // sign extend 24 bits

                        this.outputSamplesBufferA[i] = audioBitsA;
                        this.outputsamplesBufferB[i] = audioBitsB;
                    }
                }
                uncompressedBytes = 0; // always 0 for uncompressed
                interlacingShift = 0;
                interlacingLeftWeight = 0;
            }

            switch (this.setInfo_sampleSize) {
            case 16: {
                deinterlace16(this.outputSamplesBufferA, this.outputsamplesBufferB, outBuffer, this.numChannels, outputSamples, interlacingShift, interlacingLeftWeight);
                break;
            }
            case 24: {
                deinterlace24(this.outputSamplesBufferA, this.outputsamplesBufferB, uncompressedBytes, this.uncompressedBytesBufferA, this.uncompressedBytesBufferB, outBuffer, this.numChannels, outputSamples, interlacingShift, interlacingLeftWeight);
                break;
            }
            case 20:
            case 32:
            default:
                logger.warning("FIXME: unimplemented sample size " + this.setInfo_sampleSize);
            }
        }
        return outputSize;
    }

    public int decodeEntropyValue(int readSampleSize, int k, int riceKModifierMask) {
        int x = 0; // decoded value

        // read x, number of 1s before 0 represent the rice value.
        while (x <= RICE_THRESHOLD && readBit() != 0) {
            x++;
        }

        if (x > RICE_THRESHOLD) {
            // read the number from the bit stream (raw value)
            int value = 0;

            value = readBits(readSampleSize);

            // mask value
            value &= ((0xffff_ffff) >> (32 - readSampleSize));

            x = value;
        } else {
            if (k != 1) {
                int extraBits = readBits(k);

                x *= (((1 << k) - 1) & riceKModifierMask);

                if (extraBits > 1)
                    x += extraBits - 1;
                else
                    unreadBits(1);
            }
        }

        return x;
    }

    void unreadBits(int bits) {
        int newAccumulator = (this.inputBufferBitAccumulator - bits);

        this.ibIndex += (newAccumulator >> 3);

        this.inputBufferBitAccumulator = (newAccumulator & 7);
        if (this.inputBufferBitAccumulator < 0)
            this.inputBufferBitAccumulator *= -1;
    }

    /** reads a single bit */
    int readBit() {
        int result = 0;
        int newAccumulator = 0;
        int part1 = 0;

        part1 = (this.inputBuffer[this.ibIndex] & 0xff);

        result = part1;

        result = result << this.inputBufferBitAccumulator;

        result = result >> 7 & 1;

        newAccumulator = (this.inputBufferBitAccumulator + 1);

        this.ibIndex += newAccumulator / 8;

        this.inputBufferBitAccumulator = (newAccumulator % 8);

        return result;
    }

    /** supports reading 1 to 32 bits, in big endian format */
    int readBits(int bits) {
        int result = 0;

        if (bits > 16) {
            bits -= 16;

            result = readBits16(16) << bits;
        }

        result |= readBits16(bits);

        return result;
    }

    /** supports reading 1 to 16 bits, in big endian format */
    int readBits16(int bits) {
        int result = 0;
        int newAccumulator = 0;
        int part1 = 0;
        int part2 = 0;
        int part3 = 0;

        part1 = (this.inputBuffer[this.ibIndex] & 0xff);
        part2 = (this.inputBuffer[this.ibIndex + 1] & 0xff);
        part3 = (this.inputBuffer[this.ibIndex + 2] & 0xff);

        result = ((part1 << 16) | (part2 << 8) | part3);

        // shift left by the number of bits we've already read,
		// so that the top 'n' bits of the 24 bits we read will
		// be the return bits
        result = result << this.inputBufferBitAccumulator;

        result = result & 0x00ff_ffff;

        // and then only want the top 'n' bits from that, where
		// n is 'bits'
        result = result >> (24 - bits);

        newAccumulator = (this.inputBufferBitAccumulator + bits);

        // increase the buffer pointer if we've read over n bytes.
        this.ibIndex += (newAccumulator >> 3);

        // and the remainder goes back into the bit accumulator
        this.inputBufferBitAccumulator = (newAccumulator & 7);

        return result;
    }

    public void setAlacInfo(int[] inputBuffer) {
        int index = 0;
        index += 4; // size
        index += 4; // frma
        index += 4; // file
        index += 4; // size
        index += 4; // file

        index += 4; // 0 ?

        this.setInfo_maxSamplesPerFrame = ((inputBuffer[index] << 24) + (inputBuffer[index + 1] << 16) + (inputBuffer[index + 2] << 8) + inputBuffer[index + 3]); // buffer size / 2 ?
        index += 4;
        this.setInfo_7A = inputBuffer[index];
        index += 1;
        this.setInfo_sampleSize = inputBuffer[index];
        index += 1;
        this.setInfo_riceHistoryMult = (inputBuffer[index] & 0xff);
        index += 1;
        this.setInfo_riceInitialHistory = (inputBuffer[index] & 0xff);
        index += 1;
        this.setInfo_riceKModifier = (inputBuffer[index] & 0xff);
        index += 1;
        this.setInfo_7f = inputBuffer[index];
        index += 1;
        this.setInfo_80 = (inputBuffer[index] << 8) + inputBuffer[index + 1];
        index += 2;
        this.setInfo_82 = ((inputBuffer[index] << 24) + (inputBuffer[index + 1] << 16) + (inputBuffer[index + 2] << 8) + inputBuffer[index + 3]);
        index += 4;
        this.setInfo_86 = ((inputBuffer[index] << 24) + (inputBuffer[index + 1] << 16) + (inputBuffer[index + 2] << 8) + inputBuffer[index + 3]);
        index += 4;
        this.setInfo_8a_rate = ((inputBuffer[index] << 24) + (inputBuffer[index + 1] << 16) + (inputBuffer[index + 2] << 8) + inputBuffer[index + 3]);
        index += 4;
    }
}