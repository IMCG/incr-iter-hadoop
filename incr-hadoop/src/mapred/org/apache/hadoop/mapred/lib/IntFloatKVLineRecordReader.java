package org.apache.hadoop.mapred.lib;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.LineRecordReader;
import org.apache.hadoop.mapred.RecordReader;
import org.mortbay.log.Log;


public class IntFloatKVLineRecordReader implements RecordReader<IntWritable, FloatWritable> {
	  private final LineRecordReader lineRecordReader;

	  private byte separator = (byte) '\t';

	  private LongWritable dummyKey;

	  private Text innerValue;
	  
	  public Class getKeyClass() { return IntWritable.class; }
	  
	  public IntWritable createKey() {
	    return new IntWritable();
	  }
	  
	  public FloatWritable createValue() {
	    return new FloatWritable();
	  }

	  public IntFloatKVLineRecordReader(Configuration job, FileSplit split)
	    throws IOException {
	    lineRecordReader = new LineRecordReader(job, split, true);
	    dummyKey = lineRecordReader.createKey();
	    innerValue = lineRecordReader.createValue();
	    String sepStr = job.get("key.value.separator.in.input.line", "\t");
	    this.separator = (byte) sepStr.charAt(0);
	  }

	  public IntFloatKVLineRecordReader(Configuration job, InputStream in)
			    throws IOException {
			    
	    lineRecordReader = new LineRecordReader(in, 0, Integer.MAX_VALUE, job);
	    dummyKey = lineRecordReader.createKey();
	    innerValue = lineRecordReader.createValue();
	    String sepStr = job.get("key.value.separator.in.input.line", "\t");
	    this.separator = (byte) sepStr.charAt(0);
	  }
	  
	  public static int findSeparator(byte[] utf, int start, int length, byte sep) {
	    for (int i = start; i < (start + length); i++) {
	      if (utf[i] == sep) {
	        return i;
	      }
	    }
	    return -1;
	  }

	  /** Read key/value pair in a line. */
	  public synchronized boolean next(IntWritable key, FloatWritable value)
	    throws IOException {
		IntWritable tKey = key;
		FloatWritable tValue = value;
	    byte[] line = null;
	    int lineLen = -1;
	    if (lineRecordReader.next(dummyKey, innerValue)) {
	      line = innerValue.getBytes();
	      lineLen = innerValue.getLength();
	    } else {
	      return false;
	    }
	    if (line == null)
	      return false;
	    int pos = findSeparator(line, 0, lineLen, this.separator);
	    if (pos == -1) {
	    	Log.info("position is -1");
	    	
	      tKey.set(Integer.MAX_VALUE);
	      tValue.set(Float.MAX_VALUE);
	    } else {
	      int keyLen = pos;
	      byte[] keyBytes = new byte[keyLen];
	      System.arraycopy(line, 0, keyBytes, 0, keyLen);
	      int keyvalue = Integer.parseInt(new String(keyBytes));
	      int valLen = lineLen - keyLen - 1;
	      byte[] valBytes = new byte[valLen];
	      System.arraycopy(line, pos + 1, valBytes, 0, valLen);
	      float valuevalue = Float.parseFloat(new String(valBytes));
	      tKey.set(keyvalue);
	      tValue.set(valuevalue);
	    }
	    return true;
	  }
	  
	  public float getProgress() {
	    return lineRecordReader.getProgress();
	  }
	  
	  public synchronized long getPos() throws IOException {
	    return lineRecordReader.getPos();
	  }

	  public synchronized void close() throws IOException { 
	    lineRecordReader.close();
	  }
}
