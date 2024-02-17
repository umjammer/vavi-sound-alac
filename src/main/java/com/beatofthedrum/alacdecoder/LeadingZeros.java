/*
 * Copyright (c) 2011 Peter McQuillan
 *
 * All Rights Reserved.
 *
 * Distributed under the BSD Software License (see license.txt)
 */

package com.beatofthedrum.alacdecoder;


/**
 * LeadingZeros.
 */
class LeadingZeros {

    private int curByte = 0;
    private int output = 0;

    private void countLeadingZerosExtra(int curByte, int output) {

        if ((curByte & 0xf0) == 0) {
            output += 4;
        } else
            curByte = curByte >> 4;

        if ((curByte & 0x8) != 0) {
            this.output = output;
            this.curByte = curByte;
            return;
        }
        if ((curByte & 0x4) != 0) {
            this.output = output + 1;
            this.curByte = curByte;
            return;
        }
        if ((curByte & 0x2) != 0) {
            this.output = output + 2;
            this.curByte = curByte;
            return;
        }
        if ((curByte & 0x1) != 0) {
            this.output = output + 3;
            this.curByte = curByte;
            return;
        }

        // shouldn't get here:

        this.output = output + 4;
        this.curByte = curByte;
    }

    int countLeadingZeros(int input) {
        int output = 0;
        int curByte = 0;

        curByte = input >> 24;
        if (curByte != 0) {
            countLeadingZerosExtra(curByte, output);
            return this.output;
        }
        output += 8;

        curByte = input >> 16;
        if ((curByte & 0xFF) != 0) {
            countLeadingZerosExtra(curByte, output);

            return this.output;
        }
        output += 8;

        curByte = input >> 8;
        if ((curByte & 0xFF) != 0) {
            countLeadingZerosExtra(curByte, output);

            return this.output;
        }
        output += 8;

        curByte = input;
        if ((curByte & 0xFF) != 0) {
            countLeadingZerosExtra(curByte, output);

            return this.output;
        }
        output += 8;

        return output;
    }
}