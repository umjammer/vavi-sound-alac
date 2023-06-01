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

    private int curbyte = 0;
    private int output = 0;

    private void count_leading_zeros_extra(int curbyte, int output) {

        if ((curbyte & 0xf0) == 0) {
            output += 4;
        } else
            curbyte = curbyte >> 4;

        if ((curbyte & 0x8) != 0) {
            this.output = output;
            this.curbyte = curbyte;
            return;
        }
        if ((curbyte & 0x4) != 0) {
            this.output = output + 1;
            this.curbyte = curbyte;
            return;
        }
        if ((curbyte & 0x2) != 0) {
            this.output = output + 2;
            this.curbyte = curbyte;
            return;
        }
        if ((curbyte & 0x1) != 0) {
            this.output = output + 3;
            this.curbyte = curbyte;
            return;
        }

        // shouldn't get here:

        this.output = output + 4;
        this.curbyte = curbyte;
    }

    int count_leading_zeros(int input) {
        int output = 0;
        int curbyte = 0;

        curbyte = input >> 24;
        if (curbyte != 0) {
            count_leading_zeros_extra(curbyte, output);
            return this.output;
        }
        output += 8;

        curbyte = input >> 16;
        if ((curbyte & 0xFF) != 0) {
            count_leading_zeros_extra(curbyte, output);

            return this.output;
        }
        output += 8;

        curbyte = input >> 8;
        if ((curbyte & 0xFF) != 0) {
            count_leading_zeros_extra(curbyte, output);

            return this.output;
        }
        output += 8;

        curbyte = input;
        if ((curbyte & 0xFF) != 0) {
            count_leading_zeros_extra(curbyte, output);

            return this.output;
        }
        output += 8;

        return output;
    }
}