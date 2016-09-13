package test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.opencv.core.Core;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

public class PanelExtractor {

    //static{ System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }
    
    // Class:
    // parameters can be set in constructor/set functions
    // functions such as load image to go in several steps or one function to do the entire run
    // can request output values in functions
    // can also write to files or extract Mats
    
    public int threshold;
    public boolean floodRelative;
    public Point floodOrigin;
    public int floodFluctuation;
    public double minRelPanelSize;
    public double maxRelPanelSize;
    public boolean split;
    public double splitThresholdModifier;
    public boolean merge;
    public double minRelMergeOverlap;
    public double maxRelOrderOverlap;
    public double minSplitSize;
    
    public boolean splitted; // best variable name that could be used of course
    public boolean merged;
    public boolean ordered;
    public List<MatOfPoint> contours;
    public List<Rect> rects;
    
    public PanelExtractor () 
    {
        this.threshold = 230;
        this.floodRelative = false;
        this.floodOrigin = new Point();
        this.floodFluctuation = 20;
        this.minRelPanelSize = 0.005;
        this.maxRelPanelSize = 0.99;
        this.split = true;
        this.splitThresholdModifier = 10;
        this.merge = true;
        this.minRelMergeOverlap = 0.25;
        this.maxRelOrderOverlap = 0.1;
        this.minSplitSize = 0.1;

        this.splitted = false;
        this.merged = true;
        this.ordered = true;
        this.contours = null;
        this.rects = null;
    }
    
    public void generateData (Mat m)
    {
        if (m.empty())
        {
            this.contours = new ArrayList<>();
            this.rects = new ArrayList<>();
            return;
        }
        this.contours = this.generateContours(m);
        if (this.split)
        {
            this.contours = this.splitContours(this.contours);
            this.splitted = true;
        }
        
        this.rects = this.contoursToRects(this.contours);
        if (this.merge)
        {
            this.rects = this.mergeRects(this.rects);
            this.merged = true;
        }
        this.rects = this.orderRects(this.rects);
        this.ordered = true;
    }
    
    private List<MatOfPoint> generateContours (Mat m)
    {
        Mat thresh = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        List<MatOfPoint> approxContours = new ArrayList<>();

        thresh = floodFillMask(m, this.floodRelative, this.floodOrigin, this.floodFluctuation);
        
        //for (int i = 0; i < 40; ++i)
            //Imgproc.dilate(thresh, thresh, new Mat());
        
        Imgproc.findContours(thresh, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        int totalSize = m.height()*m.width();
        for (MatOfPoint cont : contours) 
        {
            if (Imgproc.contourArea(cont) > this.minRelPanelSize*totalSize) 
            {
                if (Imgproc.contourArea(cont) < this.maxRelPanelSize*totalSize)
                {
                    // convert MatOfPoint to MatOfPoint2f
                    MatOfPoint2f contour2f = new MatOfPoint2f();
                    contour2f.fromList(cont.toList());
                    
                    // approximate contour with polygon (and hope for rectangles)
                    Imgproc.approxPolyDP(contour2f, contour2f, 1, true);
                    approxContours.add(new MatOfPoint(contour2f.toArray()));
                }
                else
                {
                    // TODO: add to closest contour? (problem if it is actually close to another panel)
                }
            }
        }
        
        return approxContours;
    }
    
    private List<MatOfPoint> splitContours (List<MatOfPoint> contours)
    {
        List<MatOfPoint> splitContours = new ArrayList<>();
        for (MatOfPoint contour : contours)
        {
            List<MatOfPoint> splits = splitContour(contour);
            if (splits.size() > 1)
            {
                splitContours.addAll(splitContours(splits));
            }
            else
            {
                splitContours.addAll(splits);
            }
        }
        return splitContours;
    }
    
    private Mat floodFillMask (Mat m, boolean floodRelative, Point floodOrigin, int floodFluctuation) 
    {
        Mat mask = Mat.zeros(m.rows() + 2, m.cols() + 2, CvType.CV_8UC1);
        int flags = (255 << 8) | Imgproc.FLOODFILL_MASK_ONLY; // fill the mask with the given value instead of filling img
        if (!floodRelative)
            flags |= Imgproc.FLOODFILL_FIXED_RANGE;
        Imgproc.floodFill(
                m, 
                mask, 
                floodOrigin, 
                new Scalar(0), // flood color
                null, // out: bounding rect of repainted area
                new Scalar(floodFluctuation, floodFluctuation, floodFluctuation), // max color decrease
                new Scalar(floodFluctuation, floodFluctuation, floodFluctuation), // max color increase
                flags);
        
        return mask;
    }
    
    private List<MatOfPoint> splitContour (MatOfPoint contour) 
    {
        double fullSize = Imgproc.contourArea(contour);
        List<MatOfPoint> result = new ArrayList<>();
        MatOfInt hull = new MatOfInt();
        MatOfInt4 out = new MatOfInt4();
        
        // look for convexity defects (i.e. lines going inwards and going outwards again, rectangles should not have this)
        Imgproc.convexHull(contour, hull);
        Imgproc.convexityDefects(contour, hull, out);
        double areaSqrt = Math.sqrt(Imgproc.contourArea(contour)); // used to determine when the convexity error is big enough
        List<Integer> invalidIndices = new ArrayList<>();
        for (int i = 0; i < out.height(); ++i) 
        {
            int data[] = new int[4];
            out.get(i, 0, data);
            if (data[3] > areaSqrt*this.splitThresholdModifier)
                invalidIndices.add(data[2]);
        }
        
        // if there is only 1 invalid point we can't cut to another point
        if (invalidIndices.size() < 2) 
        {
            result.add(contour);
            return result;
        }
        
        Collections.sort(invalidIndices); // we assume the 2 closest indices will need to cut to each other
        
        int i = 0;
        while (i < invalidIndices.size()-1) 
        {
            MatOfPoint split = new MatOfPoint();
            split.fromList(contour.toList().subList(invalidIndices.get(i), invalidIndices.get(i+1)+1));
            double ratio = Imgproc.contourArea(split)/fullSize;
            
            if (split.height() >= 4 && ratio > this.minSplitSize)
            {
                result.add(split);
                ++i;
            }
            else
            {
                invalidIndices.remove(i+1);
            }
        }
        
        // final split between first and last point
        ArrayList<Point> headTail = new ArrayList<>();
        List<Point> head = contour.toList().subList(0, invalidIndices.get(0)+1);
        List<Point> tail = contour.toList().subList(invalidIndices.get(invalidIndices.size()-1), contour.toList().size());
        headTail.addAll(head);
        headTail.addAll(tail);
        MatOfPoint split = new MatOfPoint();
        split.fromList(headTail);
        double ratio = Imgproc.contourArea(split)/fullSize;
        if (split.height() >= 4 && ratio > this.minSplitSize)
        {
            result.add(split);
        }
        else
        {
            // needs to be added to either first or last entry
            MatOfPoint last = result.remove(result.size()-1);
            List<Point> lastPoints = new ArrayList<Point>(last.toList()); // new list so it can be edited
            lastPoints.addAll(tail);
            lastPoints.addAll(head);
            last.fromList(lastPoints);
            result.add(last);
        }
        
        return result;
    }
    
    private List<Rect> contoursToRects (List<MatOfPoint> contours)
    {
        List<Rect> rects = new ArrayList<>();
        for (MatOfPoint contour : contours)
        {
            Rect r = Imgproc.boundingRect(contour);
            r.width--;
            r.height--;
            rects.add(r);
        }
        return rects;
    }
    
    private List<Rect> mergeRects (List<Rect> rects)
    {
        // use rectangle to allow for intersections (do this with opencv rect?)
        ArrayList<Rectangle> javaRects = new ArrayList<>();
        for (Rect r : rects) 
            javaRects.add(new Rectangle(r.x, r.y, r.width, r.height));
        
        boolean changed;
        do 
        {
            changed = false;
            for (int i = 0; i < javaRects.size(); ++i) 
            {
                Rectangle rect = javaRects.get(i);
                int area = rect.width * rect.height;
                for (int j = i+1; j < javaRects.size(); ++j) 
                {
                    Rectangle rect2 = javaRects.get(j);
                    int area2 = rect2.width * rect2.height;
                    Rectangle intersection = rect.intersection(rect2);
                    int areaIntersection = intersection.width * intersection.height;
                    if (areaIntersection > 0 && intersection.width > 0) 
                    {
                        double ratio = 1.0*areaIntersection/Math.min(area, area2);
                        if (ratio > this.minRelMergeOverlap) { // if areas overlap at least x% of the smallest rectangle
                            changed = true;
                            javaRects.remove(j);
                            javaRects.set(i, rect.union(rect2));
                            
                            break;
                        }
                    }
                }
            } 
        } while (changed);
        
        ArrayList<Rect> convertedRects = new ArrayList<>();
        
        for (Rectangle rect : javaRects)
            convertedRects.add(new Rect(rect.x, rect.y, rect.width, rect.height));
        
        return convertedRects;
    }
    
    private List<Rect> orderRects (List<Rect> rects)
    {
        List<Rect> copy = new ArrayList<>(rects);
        List<Rect> ordered = new ArrayList<>();
            
        if (rects.isEmpty())
            return ordered;
        
        while (!copy.isEmpty()) 
        {
            Rect best = copy.get(0);
            for (int i = 1; i < copy.size(); ++i) 
            {
                Rect rect = copy.get(i);
                boolean better = false;
                int minH = Math.max(1, (int)(Math.min(best.height, rect.height)*this.maxRelOrderOverlap));
                int minW = Math.max(1, (int)(Math.min(best.width, rect.width)*this.maxRelOrderOverlap));
                
                // check if the bot of this rect is above the top of the other rect
                int diffTopBot = (rect.y+rect.height - best.y)/minH;
                better = diffTopBot <= 0;
                
                if (!better) 
                {
                    // check if it is more to the left
                    int diffX = (rect.x - best.x)/minW;
                    better = diffX < 0;
                }
                
                if (!better) 
                {
                    // check if it is higher up
                    int diffY = (rect.y - best.y)/minH;
                    better = diffY < 0;
                }
                
                if (better)
                    best = rect;
            }
            copy.remove(best);
            ordered.add(best);
        }
        
        return ordered;
    }
    
    // ---------------------- STATIC STUFF --------------------------------
    
    public static void extract () {
        //String path = ".data/wpg/amorasB.jpg";
        Mat m = MatUtils.fileToMat(".data/testpanels/roderidder.jpg");
        
        PanelExtractor extractor = new PanelExtractor();
        extractor.generateData(m);
        MatUtils.fillRects(m, extractor.rects);
        MatUtils.displayMat(m);
        /*String path2 = ".data/wpg/SW32-DRR-077-080-lay-20B.cmy.tif";
        Mat m2 = Highgui.imread(path2);
        Imgproc.resize(m2, m2, m.size());
        Core.bitwise_and(m, m2, m);
        Highgui.imwrite(".data/wpg/SW32-DRR-077-080-lay-20B.color.png", m);*/
        //getContours(m);
        //houghLines(m);
        //findText(m);
        //matchText(m);
        //floodFill(m);
        //displayRects(m, orderRects(imgToRects(m, true, true)));
        
        // GoT
        /*for (int i = 3; i <= 9; ++i)
        {
            path = ".data/got/George R.R. Martin's - A Game Of Thrones #01/GOT 1_000" + i + ".jpg";
            m = Highgui.imread(path);
            List<Rect> rekts = orderRects(imgToRects(m, false, true));
            for (int j = 0; j < rekts.size(); ++j)
            {
                Rect rekt = rekts.get(j);
                Highgui.imwrite(".data/got/panels/" + i + "_" + j + ".jpg", m.submat(rekt));
            }
        }*/
    }
    
    /*
    private static void matchText(Mat m) {
        Mat s = Highgui.imread(".data/wpg/s.jpg");
        Mat result = new Mat();
        Imgproc.matchTemplate(m, s, result, Imgproc.TM_CCOEFF_NORMED);
        Core.normalize(result, result, 0, 255, Core.NORM_MINMAX, -1);
        for (int i = 0 ; i < result.width(); ++i) {
            for (int j = 0; j < result.height(); ++j) {
                double val = result.get(j, i)[0];
                if (val > 200)
                    Core.circle(m, new Point(i + s.width()/2, j + s.height()/2), 10, new Scalar(0, 0, 255), 3);
            }
        }
        MinMaxLocResult minmax = Core.minMaxLoc(result);
        displayMat(m);
    }
    
    private static void findText(Mat m) {
        Mat gray = new Mat();
        Mat thresh = new Mat();
        
        Imgproc.cvtColor(m, gray, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(gray, thresh, 240, 255, Imgproc.THRESH_BINARY);
        Imgproc.erode(thresh, thresh, new Mat());
        
        displayMat(thresh);
        //Highgui.imwrite(".data/wpg/amoras_text_black.jpg", thresh);
    }
    
    private static void houghLines (Mat m) {
        Mat gray = new Mat();
        Mat thresh = new Mat();
        Mat lines = new Mat();
        
        Imgproc.cvtColor(m, gray, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(gray, thresh, 240, 255, Imgproc.THRESH_BINARY);
        Imgproc.Canny(thresh, thresh, 150, 200);
        //thresh = floodFill(m);
        Imgproc.GaussianBlur(thresh, thresh, new Size(7, 7), 2.0, 2.0);
        
        Imgproc.HoughLinesP(thresh, lines, 1, Math.PI/180, 150, 100, 200);
        for (int i = 0; i < lines.size().width; ++i) {
            double[] out = lines.get(0, i);
            Core.line(m, new Point(out[0], out[1]), new Point(out[2], out[3]), new Scalar(0, 0, 255), 2);
        }
        displayMat(m);
    }*/
}
