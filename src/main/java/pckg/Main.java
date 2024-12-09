package pckg;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    public static class IndexMapper extends Mapper<Object, Text, Text, Text> {

        private final Text word = new Text();
        private final Text fileName = new Text();

        private final Pattern pattern = Pattern.compile("\\w+");


        @Override
        protected void setup(Mapper<Object, Text, Text, Text>.Context context) {
            Path path = MapperUtils.getPath(context.getInputSplit());
            fileName.set(path.getName());
        }

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

            Matcher matcher = pattern.matcher(value.toString());
            while (matcher.find()) {

                word.set(matcher.group());
                context.write(word, fileName);
            }
        }
    }

    public static class IndexReducer extends Reducer<Text, Text, Text, Text> {

        private final Text result = new Text();

        @Override
        public void reduce(Text key, Iterable<Text> values,
                           Context context
        ) throws IOException, InterruptedException {

            TreeSet<String> set = new TreeSet<>();
            
            for (Text val : values) {
                set.add(val.toString());
            }

            String correspondingArticles = String.join("\t", set);

            result.set(correspondingArticles);
            context.write(key, result);
        }
    }

    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();

        conf.set("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
        conf.set("fs.file.impl", org.apache.hadoop.fs.LocalFileSystem.class.getName());

        System.setProperty("HADOOP_USER_NAME", "hadoop");

        conf.set("fs.default.name", "hdfs://namenode");
        conf.set("fs.defaultFS", "hdfs://namenode");
        conf.set("yarn.resourcemanager.hostname", "resourcemanager");
        conf.set("mapreduce.framework.name", "yarn");

        Job job = Job.getInstance(conf, "reverse index");
        job.setJarByClass(Main.class);

        job.setMapperClass(IndexMapper.class);
        job.setReducerClass(IndexReducer.class);

        job.setOutputFormatClass(TextOutputFormat.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path("/input-data/articles"));
        FileOutputFormat.setOutputPath(job, new Path("/output-data-"
                + LocalDateTime.now().toString().replace(":", "-")));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }

    public static class MapperUtils {

        public static Path getPath(InputSplit split) {
            return getFileSplit(split).map(FileSplit::getPath).orElseThrow(() ->
                    new AssertionError("cannot find path from split " + split.getClass()));
        }

        public static Optional<FileSplit> getFileSplit(InputSplit split) {
            if (split instanceof FileSplit) {
                return Optional.of((FileSplit) split);
            } else if (TaggedInputSplit.clazz.isInstance(split)) {
                return getFileSplit(TaggedInputSplit.getInputSplit(split));
            } else {
                return Optional.empty();
            }
        }

        private static final class TaggedInputSplit {
            private static final Class<?> clazz;
            private static final MethodHandle method;

            static {
                try {
                    clazz = Class.forName("org.apache.hadoop.mapreduce.lib.input.TaggedInputSplit");
                    Method m = clazz.getDeclaredMethod("getInputSplit");
                    m.setAccessible(true);
                    method = MethodHandles.lookup().unreflect(m).asType(
                            MethodType.methodType(InputSplit.class, InputSplit.class));
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }

            static InputSplit getInputSplit(InputSplit o) {
                try {
                    return (InputSplit) method.invokeExact(o);
                } catch (Throwable e) {
                    throw new AssertionError(e);
                }
            }
        }

        private MapperUtils() {
        }

    }
}