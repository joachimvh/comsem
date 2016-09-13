package test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Random;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

public class FaceDetector 
{
    
    public static void doStuff ()
    {
        Mat gray = new Mat();
        MatOfRect rects = new MatOfRect();
        CascadeClassifier cc = new CascadeClassifier("cascade.xml");
        for (int i = 1; i< 50; i+=2)
        {
            String path = String.format(".data/wpg/strips/SUSKE EN WISKE/SW254/S&W254_%03d-%03d.jpg", i, i+1);
            Mat m = MatUtils.fileToMat(path);
    
            Imgproc.cvtColor(m, gray, Imgproc.COLOR_RGB2GRAY);
            cc.detectMultiScale(gray, rects, 1.1, 5, 0, new Size(), new Size());
            for (Rect r : rects.toList())
            {
                MatUtils.drawRect(m, r);
            }
            MatUtils.displayMat(m);
        }
    }
    
    // http://note.sonots.com/SciSoftware/haartraining.html
    // http://stackoverflow.com/questions/18047086/how-to-compile-mergevec-cpp-on-windows
    // http://docs.opencv.org/doc/user_guide/ug_traincascade.html
    public static void generateTrainingData () throws IOException 
    {
        String positivePathStr = "C:/projects/estrips/.data/wpg/lambik/positive2/";
        String negativePathStr = "C:/projects/estrips/.data/wpg/lambik/negative2/";
        String createSamplesPathStr = "C:/Users/jvherweg/Desktop/opencv/build/x86/vc10/bin/opencv_createsamples.exe";
        String outputPathStr = "C:/projects/estrips/.data/wpg/lambik/out";
        String testPathStr = "C:/projects/estrips/.data/wpg/lambik/test";
        int amount = 5000;
        int testAmount = 200;
        
        String[] positivePaths = new File(positivePathStr).list();  
        String[] negativePaths = new File(negativePathStr).list();       
        int positives = positivePaths.length;
        int negatives = negativePaths.length;
        int amountPerImage = amount/positives;
        int remainder = amount - positives*amountPerImage;
        int testAmountPerImage = testAmount/positives;
        int testRemainder = amount - positives*testAmountPerImage;
        
        Path outputPath = FileSystems.getDefault().getPath(outputPathStr);
        Path testPath = FileSystems.getDefault().getPath(testPathStr);
        Files.createDirectories(outputPath);
        Files.createDirectories(testPath);
        
        Random rng = new Random();
        for (int i = 0; i < positives; ++i)
        {
            int n = (i < remainder) ? amountPerImage + 1 : amountPerImage;
            
            Path tempPath = outputPath.resolve("temp.txt");
            Files.write(tempPath, new byte[]{}, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            for (int j = 0; j < n; ++j)
            {
                Path negativeFilePath = FileSystems.getDefault().getPath(negativePathStr+negativePaths[rng.nextInt(negatives)]);
                Files.write(tempPath, (outputPath.relativize(negativeFilePath) + "\n").replace("\\", "/").getBytes(), StandardOpenOption.APPEND);
            }
            
            Path vecPath = outputPath.resolve("out"+i+".vec");
            
            // call command
            // "./createsamples  -bgcolor 0 -bgthresh 0 -maxxangle 1.1 -maxyangle 1.1 maxzangle 0.5 -maxidev 40 -w 20 -h 20"
            try
            {
                System.out.println(i);
                ProcessBuilder pb = new ProcessBuilder(
                        createSamplesPathStr, 
                        "-img", (positivePathStr + positivePaths[i]).replace("\\", "/"),
                        "-bg", tempPath.toAbsolutePath().toString().replace("\\", "/"),
                        "-vec", vecPath.toAbsolutePath().toString().replace("\\", "/"),
                        "-bgcolor", "0",
                        "-bgthresh", "0",
                        "-maxxangle", "1.1",
                        "-maxyangle", "1.1",
                        "-maxzangle", "0.5",
                        "-maxidev", "40",
                        "-w", "20",
                        "-h", "20");
                Process process = pb.start();
                process.waitFor();
            }
            catch (InterruptedException ex)
            {
                ex.printStackTrace();
            }
            
            // test files
            int nTest = (i < testRemainder) ? testAmountPerImage + 1 : testAmountPerImage;
            Files.write(tempPath, new byte[]{}, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            for (int j = 0; j < n; ++j)
            {
                Path negativeFilePath = FileSystems.getDefault().getPath(negativePathStr+negativePaths[rng.nextInt(negatives)]);
                Files.write(tempPath, (outputPath.relativize(negativeFilePath) + "\n").replace("\\", "/").getBytes(), StandardOpenOption.APPEND);
            }
            
            // "./createsamples -bgcolor 0 -bgthresh 0 -maxxangle 1.1 -maxyangle 1.1 -maxzangle 0.5 maxidev 40"
            try
            {
                ProcessBuilder pb = new ProcessBuilder(
                        createSamplesPathStr, 
                        "-img", (positivePathStr + positivePaths[i]).replace("\\", "/"),
                        "-bg", tempPath.toAbsolutePath().toString().replace("\\", "/"),
                        "-num", nTest+"",
                        "-info", testPath.resolve(positivePaths[i]+"/info.dat").toAbsolutePath().toString().replace("\\", "/"),
                        "-bgcolor", "0",
                        "-bgthresh", "0",
                        "-maxxangle", "1.1",
                        "-maxyangle", "1.1",
                        "-maxzangle", "0.5",
                        "-maxidev", "40",
                        "-w", "20",
                        "-h", "20");
                Process process = pb.start();
                process.waitFor();
            }
            catch (InterruptedException ex)
            {
                ex.printStackTrace();
            }
        }
    }

}
