package org.apache.hadoop.examples.iterative;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.IterativeMapper;
import org.apache.hadoop.mapred.IterativeReducer;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.KeyValueTextInputFormat;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Partitioner;
import org.apache.hadoop.mapred.Projector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.lib.HashPartitioner;


public class PageRank {
	
	//damping factor
	public static final float DAMPINGFAC = (float)0.8;
	public static final float RETAINFAC = (float)0.2;
	
	public static class DistributeDataMap extends MapReduceBase implements
		Mapper<Text, Text, LongWritable, Text> {
		
		@Override
		public void map(Text arg0, Text value,
				OutputCollector<LongWritable, Text> arg2, Reporter arg3)
				throws IOException {
			int page = Integer.parseInt(arg0.toString());
		
			arg2.collect(new LongWritable(page), value);
		}
		
		/*
		private LongWritable outputKey = new LongWritable();
		private Text outputVal = new Text();
		private List<String> tokenList = new ArrayList<String>();

		public void map(LongWritable key, Text value,
				OutputCollector<LongWritable, Text> output, Reporter reporter)
				throws IOException {
			tokenList.clear();

			String line = value.toString();
			StringTokenizer tokenizer = new StringTokenizer(line);

			while (tokenizer.hasMoreTokens()) {
				tokenList.add(tokenizer.nextToken());
			}

			if (tokenList.size() >= 2) {
				outputKey.set(Long.parseLong(tokenList.get(0)));
				outputVal.set(tokenList.get(1).getBytes());
				output.collect(outputKey, outputVal);
			} else if(tokenList.size() == 1){
				//no out link
				outputKey.set(Long.parseLong(tokenList.get(0)));
				output.collect(outputKey, new Text(tokenList.get(0)));
			}
			//System.out.println("output " + outputKey + "\t" + outputVal);
		}
		*/
	}
	
	public static class DistributeDataReduce extends MapReduceBase implements
		Reducer<LongWritable, Text, LongWritable, Text> {
		Random rand = new Random();
		@Override
		public void reduce(LongWritable key, Iterator<Text> values,
				OutputCollector<LongWritable, Text> output, Reporter reporter)
				throws IOException {
			String outputv = "";

			while(values.hasNext()){
				String ends = values.next().toString();
				/*
				if(key.get() == Long.parseLong(end)){
					int randlong = rand.nextInt(Integer.MAX_VALUE);
					while(randlong == Long.parseLong(end)){
						randlong = rand.nextInt(Integer.MAX_VALUE);
					}
					outputv += randlong + " ";
				}else{
					outputv += end + " ";
				}
				*/
				outputv += ends;
			}
			
			output.collect(key, new Text(outputv));
			//System.out.println("output " + outputv);
		}
	}
	
	public static class PageRankMap extends MapReduceBase implements
		IterativeMapper<LongWritable, Text, LongWritable, FloatWritable, LongWritable, FloatWritable> {
	
		@Override
		public void map(LongWritable statickey, Text staticval,
				FloatWritable dynamicvalue,
				OutputCollector<LongWritable, FloatWritable> output,
				Reporter reporter) throws IOException {
			
			float rank = dynamicvalue.get();
			//System.out.println("input : " + key + " : " + rank);
			String linkstring = staticval.toString();
			
			//in order to avoid non-inlink node, which will mismatch the static file
			output.collect(statickey, new FloatWritable(PageRank.RETAINFAC));
			
			String[] links = linkstring.split(" ");	
			float delta = rank * PageRank.DAMPINGFAC / links.length;
			
			for(String link : links){
				if(link.equals("")) continue;
				output.collect(new LongWritable(Long.parseLong(link)), new FloatWritable(delta));
				//System.out.println("output: " + link + "\t" + delta);
			}
		}

		@Override
		public FloatWritable removeLable() {
			// TODO Auto-generated method stub
			return null;
		}
	
	}
	
	public static class PageRankReduce extends MapReduceBase implements
		IterativeReducer<LongWritable, FloatWritable, LongWritable, FloatWritable> {
	
		@Override
		public void reduce(LongWritable key, Iterator<FloatWritable> values,
				OutputCollector<LongWritable, FloatWritable> output, Reporter report)
				throws IOException {
			float rank = 0;
			while(values.hasNext()){
				float v = values.next().get();
				if(v == -1) continue;
				rank += v;
			}
			
			output.collect(key, new FloatWritable(rank));
			//System.out.println("output:" + key + "\t" + rank);
		}
		
		@Override
		public float distance(LongWritable key, FloatWritable prevV,
				FloatWritable currV) throws IOException {
			// TODO Auto-generated method stub
			return Math.abs(prevV.get() - currV.get());
		}

		@Override
		public FloatWritable removeLable() {
			// TODO Auto-generated method stub
			return null;
		}
	
	}
	
	public static class PageRankProjector implements Projector<LongWritable, LongWritable, FloatWritable> {

		@Override
		public void configure(JobConf job) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public LongWritable project(LongWritable statickey) {
			return statickey;
		}

		@Override
		public FloatWritable initDynamicV(LongWritable dynamickey) {
			return new FloatWritable(1);
		}

		@Override
		public Partitioner<LongWritable, FloatWritable> getDynamicKeyPartitioner() {
			// TODO Auto-generated method stub
			return new HashPartitioner<LongWritable, FloatWritable>();
		}

		@Override
		public org.apache.hadoop.mapred.Projector.Type getProjectType() {
			return Projector.Type.ONE2ONE;
		}
	}

	private static void printUsage() {
		System.out.println("pagerank [-p partitions] <inStaticDir> <outDir>");
		System.out.println(	"\t-p # of parittions\n" +
							"\t-i snapshot interval\n" +
							"\t-I # of iterations\n" +
							"\t-n # of nodes");
	}

	public static int main(String[] args) throws Exception {
		if (args.length < 2) {
			return -1;
		}
		
		int partitions = 0;
		int interval = 1;
		int max_iterations = Integer.MAX_VALUE;
		
		List<String> other_args = new ArrayList<String>();
		for(int i=0; i < args.length; ++i) {
		      try {
		          if ("-p".equals(args[i])) {
		        	partitions = Integer.parseInt(args[++i]);
		          } else if ("-i".equals(args[i])) {
		        	interval = Integer.parseInt(args[++i]);
		          } else if ("-I".equals(args[i])) {
		        	  max_iterations = Integer.parseInt(args[++i]);
		          } else {
		    		  other_args.add(args[i]);
		    	  }
		      } catch (NumberFormatException except) {
		        System.out.println("ERROR: Integer expected instead of " + args[i]);
		        printUsage();
		        return -1;
		      } catch (ArrayIndexOutOfBoundsException except) {
		        System.out.println("ERROR: Required parameter missing from " +
		                           args[i-1]);
		        printUsage();
		        return -1;
		      }
		}
		
	    if (other_args.size() < 2) {
		      System.out.println("ERROR: Wrong number of parameters: " +
		                         other_args.size() + ".");
		      printUsage(); return -1;
		}
	    
	    String inStatic = other_args.get(0);
	    String output = other_args.get(1);
		
	    String iteration_id = "pagerank" + new Date().getTime();
	    
		/**
		 * the initialization job, for partition the data and workload
		 */
	    long initstart = System.currentTimeMillis();
	    
	    JobConf job1 = new JobConf(PageRank.class);
	    String jobname1 = "PageRank Init";
	    job1.setJobName(jobname1);
	    
	    job1.setDataDistribution(true);
	    job1.setIterativeAlgorithmID(iteration_id);
	    job1.setInputFormat(KeyValueTextInputFormat.class);
	    job1.setOutputFormat(SequenceFileOutputFormat.class);
	    TextInputFormat.addInputPath(job1, new Path(inStatic));
	    FileOutputFormat.setOutputPath(job1, new Path(output + "/substatic"));

	    job1.setMapperClass(DistributeDataMap.class);
		job1.setReducerClass(DistributeDataReduce.class);

	    job1.setOutputKeyClass(LongWritable.class);
	    job1.setOutputValueClass(Text.class);
	    
	    //new added, the order is strict
	    job1.setProjectorClass(PageRankProjector.class);
	    
	    /**
	     * if partitions to0 small, which limit the map performance (since map is usually more haveyly loaded),
	     * we should partition the static data into 2*partitions, and copy reduce results to the other mappers in the same scale,
	     * buf first, we just consider the simple case, static data partitions == dynamic data partitions
	     */
	    job1.setNumMapTasks(partitions);
	    job1.setNumReduceTasks(partitions);
	    
	    JobClient.runJob(job1);
	    
	    long initend = System.currentTimeMillis();
		Util.writeLog("iter.pagerank.log", "init job use " + (initend - initstart)/1000 + " s");
		
	    /**
	     * start iterative application jobs
	     */
	    
	    int iteration = 1;
	    boolean cont = true;
	    
	    long itertime = 0;
	    
	    while(cont && iteration < max_iterations){
	    	long iterstart = System.currentTimeMillis();
	    	
		    JobConf job = new JobConf(PageRank.class);
		    String jobname = "PageRank Main " + iteration;
		    job.setJobName(jobname);
	    
		    //if(partitions == 0) partitions = Util.getTTNum(job);
		    
		    //set for iterative process   
		    job.setIterative(true);
		    job.setIterativeAlgorithmID(iteration_id);		//must be unique for an iterative algorithm
		    job.setIterationNum(iteration);					//iteration number
		    job.setCheckPointInterval(interval);					//checkpoint interval
		    if(iteration > 1){
		    	job.setDynamicDataPath(output + "/iteration-" + (iteration-1));				//init by file, if not set init by API
		    }
		    job.setStaticDataPath(output + "/substatic");
		    job.setStaticInputFormat(SequenceFileInputFormat.class);
		    job.setDynamicInputFormat(SequenceFileInputFormat.class);		//MUST have this for the following jobs, even though the first job not need it
	    	job.setResultInputFormat(SequenceFileInputFormat.class);		//if set termination check, you have to set this
		    job.setOutputFormat(SequenceFileOutputFormat.class);
		    
		    FileInputFormat.addInputPath(job, new Path(output + "/substatic"));
		    FileOutputFormat.setOutputPath(job, new Path(output + "/iteration-" + iteration));
		    
		    if(max_iterations == Integer.MAX_VALUE){
		    	job.setDistanceThreshold(1);
		    }
    
		    job.setOutputKeyClass(LongWritable.class);
		    job.setOutputValueClass(FloatWritable.class);
		    
		    job.setIterativeMapperClass(PageRankMap.class);	
		    job.setIterativeReducerClass(PageRankReduce.class);
		    job.setProjectorClass(PageRankProjector.class);
		    
		    job.setNumReduceTasks(partitions);			

		    cont = JobClient.runIterativeJob(job);

	    	long iterend = System.currentTimeMillis();
	    	itertime += (iterend - iterstart) / 1000;
	    	Util.writeLog("iter.pagerank.log", "iteration computation " + iteration + " takes " + itertime + " s");
	    	
	    	iteration++;
	    }
	    
	    
	    //preserving job
    	long preservestart = System.currentTimeMillis();
    	
	    JobConf job2 = new JobConf(PageRank.class);
	    String jobname = "PageRank Preserve " + iteration;
	    job2.setJobName(jobname);
    
	    if(partitions == 0) partitions = Util.getTTNum(job2);
	    
	    //set for iterative process   
	    job2.setPreserve(true);
	    job2.setIterativeAlgorithmID(iteration_id);		//must be unique for an iterative algorithm
	    job2.setIterationNum(iteration);					//iteration numbe
	    job2.setCheckPointInterval(interval);					//checkpoint interval
	    job2.setStaticDataPath(output + "/substatic");
	    job2.setStaticInputFormat(SequenceFileInputFormat.class);
	    job2.setDynamicInputFormat(SequenceFileInputFormat.class);		//MUST have this for the following jobs, even though the first job not need it
    	job2.setResultInputFormat(SequenceFileInputFormat.class);		//if set termination check, you have to set this
	    job2.setOutputFormat(SequenceFileOutputFormat.class);
	    job2.setPreserveStatePath(output + "/preserve");
	    
	    FileInputFormat.addInputPath(job2, new Path(output + "/substatic"));
	    FileOutputFormat.setOutputPath(job2, new Path(output + "/preserve/convergeState"));
	    
	    if(max_iterations == Integer.MAX_VALUE){
	    	job2.setDistanceThreshold(1);
	    }

	    job2.setStaticKeyClass(LongWritable.class);
	    job2.setOutputKeyClass(LongWritable.class);
	    job2.setOutputValueClass(FloatWritable.class);
	    
	    job2.setIterativeMapperClass(PageRankMap.class);	
	    job2.setIterativeReducerClass(PageRankReduce.class);
	    job2.setProjectorClass(PageRankProjector.class);
	    
	    job2.setNumReduceTasks(partitions);			

	    JobClient.runIterativeJob(job2);

    	long preserveend = System.currentTimeMillis();
    	long preservationtime = (preserveend - preservestart) / 1000;
    	Util.writeLog("iter.pagerank.log", "iteration preservation takes " + preservationtime + " s");
	    
	    
		return 0;
	}

}
