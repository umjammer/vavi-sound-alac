/*
 * Copyright (c) 2011 Peter McQuillan
 *
 * All Rights Reserved.
 *
 * Distributed under the BSD Software License (see license.txt)
 */

package com.beatofthedrum.alacdecoder;

import java.io.IOException;


/**
 * DemuxResT.
 */
class DemuxResT {

    /**
     * SampleInfo.
     */
    static class SampleInfo {
        int sample_count = 0;
        int sample_duration = 0;
    }

    /**
     * @author Denis Tulskiy
     * @version 4/9/11
     */
    static class ChunkInfo {
        int first_chunk;
        int samples_per_chunk;
        int sample_desc_index;
    }

    /** */
    static class SampleDuration {
        int sample_byte_size = 0;
        int sample_duration = 0;
    }

    int format_read;

    int num_channels;
    int sample_size;
    int sample_rate;
    int format;
    private int[] buf = new int[1024 * 80];

    SampleInfo[] time_to_sample = new SampleInfo[16];
    int num_time_to_samples;

    int[] sample_byte_size;

    int codecdata_len;

    int[] codecdata = new int[1024];

    int[] stco;
    ChunkInfo[] stsc;

    int mdat_len;

    DemuxResT() {
        // not sure how many of these I need, so make 16
        for (int i = 0; i < 16; i++) {
            time_to_sample[i] = new SampleInfo();
        }
    }

    void get_sample_info(int samplenum, SampleDuration sampleinfo) throws IOException {
        int duration_index_accum = 0;
        int duration_cur_index = 0;

        if (samplenum >= this.sample_byte_size.length) {
            throw new IOException("sample " + samplenum + " does not exist ");
        }

        if (this.num_time_to_samples == 0) { // was null
            throw new IOException("no time to samples");
        }
        while ((this.time_to_sample[duration_cur_index].sample_count + duration_index_accum) <= samplenum) {
            duration_index_accum += this.time_to_sample[duration_cur_index].sample_count;
            duration_cur_index++;
            if (duration_cur_index >= this.num_time_to_samples) {
                throw new IOException("sample " + samplenum + " does not have a duration");
            }
        }

        sampleinfo.sample_duration = this.time_to_sample[duration_cur_index].sample_duration;
        sampleinfo.sample_byte_size = this.sample_byte_size[samplenum];
    }
}
