package test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.apache.commons.lang3.StringUtils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

public class TextMatcher 
{
    
    public int swInsert;
    public int swGap;
    public int swMatch;
    public int swMismatch;
    
    public TextMatcher ()
    {
        this.swInsert = -2;
        this.swGap = -2;
        this.swMatch = 1;
        this.swMismatch = -1;
    }
    
    public void combineImagesText ()
    {
        try
        {
            List<String> lines = Files.readAllLines(FileSystems.getDefault().getPath(".data", "got", "text", "0_Prologue.txt"), Charset.forName("UTF-8"));
            String text = StringUtils.join(lines, ' ').toLowerCase();
            TreeMap<MatchResult, String> panelResults = new TreeMap<>(Collections.reverseOrder());
            for (String path : new File(".data/got/panels").list())
            {
                Mat m = Highgui.imread(".data/got/panels/" + path);
                List<Mat> panels = getTextPanels(m);
                for (Mat panel : panels)
                {
                    Highgui.imwrite("DUMMY.bmp", panel);
                    String ocr = tesseract("DUMMY.bmp").trim();
                    if (ocr.length() > 5)
                    {
                        // MatchResult result = match(ocr, text);
                        // TODO: if match occurs several times in the text (e.g. 'had been'), ignore the result, use tf-idf?
                        MatchResult result = smithWaterman(ocr, text);
                        if (result.score > 10)
                            panelResults.put(result, path);
                    }
                }
            }
            
            text = StringUtils.join(lines, '\n'); // lowercase ugly
            for (MatchResult result : panelResults.keySet())
            {
                System.out.println(panelResults.get(result));
                System.out.println(result);
                text = text.substring(0, result.idx) + 
                        "<a href='../panels/" + panelResults.get(result) +"'>" + 
                        text.substring(result.idx, result.idx + result.template.length()) + 
                        "</a>" + 
                        text.substring(result.idx + result.template.length());
            }
            
            // TODO: really could use a class for these kinds of things
            int i = 0;
            Set<String> panels = new HashSet<>();
            while (i < text.length())
            {
                if (i < text.length()-8 && text.substring(i, i+8).equals("a href='"))
                {
                    String subtext = text.substring(i+8);
                    String panel = subtext.substring(0, subtext.indexOf("'"));
                    if (panels.add(panel))
                    {
                        int j = i;
                        while (j > 0 && text.charAt(j) != '\n')
                            --j;
                        text = text.substring(0, j) +
                                "\n<a href='" + panel + "'><img src='" + panel + "'/></a>\n" +
                                text.substring(j);
                    }
                    i+=10;
                }
                ++i;
            }
            
            text = text.replace("\n", "<br>\n");
            text = "<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js\"></script>\n" + 
                    "<script type=\"text/javascript\">\n" + 
                    "$(document).ready(function() {\n" + 
                    "    $('img').each(function(){\n" + 
                    "        $(this).load(function() {\n" + 
                    "            $(this).width($(this).width() * 0.4);\n" + 
                    "        });\n" + 
                    "    });\n" + 
                    "});\n" + 
                    "</script>"
                    + text;
            Files.write(FileSystems.getDefault().getPath(".data", "got", "text", "0_Prologue.html"), text.getBytes());
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            System.exit(1);
        }
    }
    
    private List<Mat> getTextPanels (Mat m) 
    {
        Mat thresh = new Mat();
        Mat thresh2 = new Mat();
        //m.convertTo(m, -1, 1.5, 0);
        Imgproc.cvtColor(m, thresh, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(thresh, thresh, 100, 255, Imgproc.THRESH_BINARY);
        
        thresh.copyTo(thresh2);
        
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        
        Map<Integer, List<MatOfPoint>> children = new HashMap<>();
        for (int i = 0; i < hierarchy.width(); ++i) 
        {
            if (hierarchy.get(0, i)[3] >= 0 /*&& contours.get(i).height() > 10*/) 
            {
                int parent = (int)hierarchy.get(0, i)[3];
                if (children.get(parent) == null)
                    children.put(parent, new ArrayList<MatOfPoint>());
                children.get(parent).add(contours.get(i));
            }
        }

        List<Mat> submats = new ArrayList<>();
        for (int parent : children.keySet())
        {
            double area = Imgproc.contourArea(contours.get(parent));
            
            MatOfPoint mergedContour = new MatOfPoint();
            ArrayList<Point> mergedList = new ArrayList<>();
            for (MatOfPoint child : children.get(parent))
                mergedList.addAll(child.toList());
            mergedContour.fromList(mergedList);
            Rect bound = Imgproc.boundingRect(mergedContour);

            Core.bitwise_not(thresh2, thresh2); // TODO: maybe look up why this is needed
            if (bound.area() < area && children.get(parent).size() > 10 /*&& children.get(parent).size() > area/1000*/) 
            {
                
                Mat mask = new Mat(m.rows(), m.cols(), CvType.CV_8UC1, new Scalar(0));
                Mat crop = new Mat(m.rows(), m.cols(), CvType.CV_8UC3, new Scalar(255, 255, 255));
                Imgproc.drawContours(mask, contours, parent, new Scalar(255), Core.FILLED);
                
                thresh2.copyTo(crop, mask);
                Core.bitwise_not(crop, crop);
                
                submats.add(crop.submat(bound));
            }
            Core.bitwise_not(thresh2, thresh2);
        }
        return submats;
    }
    
    private MatchResult smithWaterman (String template, String text)
    {
        return smithWaterman(template, text, this.swInsert, this.swGap, this.swMatch, this.swMismatch);
    }
    
    private MatchResult smithWaterman (String template, String text, int insert, int gap, int match, int mismatch)
    {
        // TODO: store pointers to find the path more easily?
        int[][] matrix = new int[template.length()+1][text.length()+1];
        java.awt.Point endPos = new java.awt.Point();
        int endVal = 0;
        for (int i = 1; i < template.length()+1; ++i)
        {
            for (int j = 1; j < text.length()+1; ++j)
            {
                matrix[i][j] = Math.max(matrix[i-1][j] + insert, matrix[i][j-1] + gap);
                matrix[i][j] = Math.max(matrix[i][j], matrix[i-1][j-1] + (template.charAt(i-1) == text.charAt(j-1) ? match : mismatch));
                matrix[i][j] = Math.max(0, matrix[i][j]);
                
                if (matrix[i][j] > endVal)
                {
                    endPos.x = i;
                    endPos.y = j;
                    endVal = matrix[i][j];
                }
            }
        }

        // TODO: maybe there are several good matches in the matrix (if the sentence was differently structured in the book)
        java.awt.Point startPos = new java.awt.Point(endPos);
        java.awt.Point prevPos = new java.awt.Point(endPos);
        while (matrix[prevPos.x][prevPos.y] > 0)
        {
            startPos = prevPos;
            
            if (template.charAt(startPos.x-1) == text.charAt(startPos.y-1) && matrix[prevPos.x-1][prevPos.y-1] == matrix[prevPos.x][prevPos.y] - 1)
            {
                prevPos.x--;
                prevPos.y--;
            }
            else if (template.charAt(startPos.x-1) != text.charAt(startPos.y-1) && (matrix[prevPos.x-1][prevPos.y-1] == matrix[prevPos.x][prevPos.y] + 1 || matrix[prevPos.x][prevPos.y] == 0))
            {
                prevPos.x--;
                prevPos.y--; 
            }
            else if (matrix[prevPos.x-1][prevPos.y] == matrix[prevPos.x][prevPos.y] + 2 || matrix[prevPos.x][prevPos.y] == 0)
            {
                prevPos.x--;
            }
            else
            {
                prevPos.y--;
            }
        }
        
        return new MatchResult(startPos.y, endVal, template.substring(startPos.x, endPos.x), text.substring(startPos.y, endPos.y));
    }
    
    private String tesseract (String path)
    {
        try
        {
            Process process = new ProcessBuilder("tesseract", path, "out", "blacklist").start();
            process.waitFor();
            
            List<String> lines = Files.readAllLines(FileSystems.getDefault().getPath("out.txt"), Charset.forName("UTF-8"));
            Files.deleteIfExists(FileSystems.getDefault().getPath("out.txt"));
            return StringUtils.join(lines, ' ').toLowerCase();
        }
        catch (IOException | InterruptedException ex)
        {
            ex.printStackTrace();
            System.exit(1);
            return null;
        }
    }
    
    private class MatchResult implements Comparable<MatchResult>
    {
        public int idx;
        public int score;
        public String template;
        public String match;
        public MatchResult (int idx, int score, String template, String match)
        {
            this.idx = idx;
            this.score = score;
            this.template = template;
            this.match = match;
        }
        public String toString ()
        {
            return "score: " + score + " " + (int)(100.0*score/template.length()) + "%" + "\n" +
                   "template: " + template + "\n" +
                   "match:    " + match;
        }
        public int compareTo(MatchResult o) 
        {
            return this.idx - o.idx;
        }
    }
}
