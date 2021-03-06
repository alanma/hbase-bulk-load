package com.blogspot.anikulin.bulkload.mrjob;

import com.blogspot.anikulin.bulkload.commons.Utils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import static com.blogspot.anikulin.bulkload.commons.Constants.*;

/**
 * Hadoop job implementation.
 * It prepares data for bulk load.
 * Mapper splits tab - separated text lines on key and value.
 * All row-keys are MD5 hashed since it helps to get normal data distribution
 *
 * @author Anatoliy Nikulin
 * email 2anikulin@gmail.com
 */
public class BulkLoadJob extends Configured implements Tool {

    private static final Logger LOG = LoggerFactory.getLogger(BulkLoadJob.class);
    private static final String JOB_NAME = "HBase bulk-load";

    /**
     * Tools method.
     * @param strings arguments
     * @return code
     * @throws Exception .
     */
    @Override
    public int run(final String[] strings) throws Exception {
        throw new NotImplementedException("Method not implemented");
    }

    /**
     * Creates and initializes Job.
     *
     * @param configuration  Job configuration.
     * @param hTable         HBase table.
     * @param inputPath      HDFS-path to input files.
     * @param outputPath     HDFS-path to prepared output HFile.
     * @return               Constructed and initialized Job.
     * @throws IOException .
     */
    public static Job createJob(final Configuration configuration,
                                final HTable hTable,
                                final String inputPath,
                                final String outputPath)
            throws IOException {

        LOG.info("Job \"{}\" initializing...", JOB_NAME);

        Job job = new Job(configuration, JOB_NAME);
        job.setJarByClass(BulkLoadJob.class);

        job.setMapOutputKeyClass(ImmutableBytesWritable.class);
        job.setMapOutputValueClass(Put.class);

        job.setMapperClass(DataMapper.class);
        job.setNumReduceTasks(0);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(HFileOutputFormat.class);

        FileInputFormat.setInputPaths(job, inputPath);
        HFileOutputFormat.setOutputPath(job, new Path(outputPath));

        //It used for auto-configuring partitioner and reducer
        HFileOutputFormat.configureIncrementalLoad(job, hTable);

        LOG.info("Job \"{}\" created", JOB_NAME);

        return job;
    }

    /**
     * Mapper implementation.
     * Reads text lines and convert to HBase Put.
     */
    public static class DataMapper extends Mapper<LongWritable, Text, ImmutableBytesWritable, Put> {

        private static final Logger LOG = LoggerFactory.getLogger(DataMapper.class);

        private static final byte[] COLUMN_FAMILY_NAME_BYTES = Bytes.toBytes(COLUMN_FAMILY_NAME);
        private static final byte[] COLUMN_QUALIFIER_DESCRIPTION_BYTES = Bytes.toBytes(COLUMN_QUALIFIER_DESCRIPTION);
        private static final byte[] COLUMN_QUALIFIER_INDEX_BYTES = Bytes.toBytes(COLUMN_QUALIFIER_INDEX);

        @Override
        public void setup(final Context context) throws IOException, InterruptedException {

        }

        /**
         * Map function.
         *
         * @param key      Input key. It always 0.
         * @param value    Input value, text line.
         * @param context  Job context.
         *
         * @throws IOException .
         * @throws InterruptedException .
         */
        @Override
        public void map(final LongWritable key, final Text value, final Context context)
                throws IOException, InterruptedException {

            String[] values = value.toString().split("\t");
            if (values.length == 2) {
                String rowKey = values[0];
                byte[] hashedRowKey = Utils.getHash(rowKey);

                Put put = new Put(hashedRowKey);
                put.add(COLUMN_FAMILY_NAME_BYTES, COLUMN_QUALIFIER_INDEX_BYTES, Bytes.toBytes(rowKey));
                put.add(COLUMN_FAMILY_NAME_BYTES, COLUMN_QUALIFIER_DESCRIPTION_BYTES, Bytes.toBytes(values[1]));

                context.write(new ImmutableBytesWritable(hashedRowKey), put);
            } else {
                context.getCounter(Counters.WRONG_DATA_FORMAT_COUNTER).increment(1);
                LOG.warn("Wrong line format: {}", value);
            }
        }
    }

    /**
     * Enum of counters.
     * It used for collect statistics
     */
    public static enum Counters {
        /**
         * Counts data format errors.
         */
        WRONG_DATA_FORMAT_COUNTER
    }
}
