package test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;

public class AdvancedPanelExtractor 
{
    // stolen from panelextractor, plz giv bak
    private static Mat floodFillMask (Mat m, boolean floodRelative, Point floodOrigin, int floodFluctuation) 
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
    
    // http://tech-algorithm.com/articles/drawing-line-using-bresenham-algorithm/
    private static List<Point> pointsOnLine(int x1, int y1, int x2, int y2)
    {
        List<Point> points = new ArrayList<>();
        int w = x2 - x1 ;
        int h = y2 - y1 ;
        int x = x1;
        int y = y1;
        int dx1 = 0, dy1 = 0, dx2 = 0, dy2 = 0 ;
        if (w<0) dx1 = -1 ; else if (w>0) dx1 = 1 ;
        if (h<0) dy1 = -1 ; else if (h>0) dy1 = 1 ;
        if (w<0) dx2 = -1 ; else if (w>0) dx2 = 1 ;
        int longest = Math.abs(w) ;
        int shortest = Math.abs(h) ;
        if (!(longest>shortest)) 
        {
            longest = Math.abs(h) ;
            shortest = Math.abs(w) ;
            if (h<0) dy2 = -1 ; else if (h>0) dy2 = 1 ;
            dx2 = 0 ;            
        }
        int numerator = longest >> 1 ;
        for (int i=0;i<=longest;i++) {
            points.add(new Point(x, y));
            numerator += shortest ;
            if (!(numerator<longest)) 
            {
                numerator -= longest ;
                x += dx1 ;
                y += dy1 ;
            } 
            else 
            {
                x += dx2 ;
                y += dy2 ;
            }
        }
        return points;
    }

    public static void doStuff2 ()
    {
        Mat thresh = new Mat();
        Mat m = MatUtils.fileToMat(".data/testpanels/amorasC.jpg");
        Mat gray = new Mat(m.rows(), m.cols(), CvType.CV_8UC1);
        for (int x = 0; x < m.width(); ++x)
        {
            for (int y = 0; y < m.height(); ++y)
            {
                double[] vals = m.get(y, x);
                gray.put(y, x, vals[0] + vals[1] + vals[2]);
            }
        }
        
        Core.addWeighted(m, 2, m, 2, 0, m);

        //Imgproc.cvtColor(m, gray, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(gray, thresh, 100, 255, Imgproc.THRESH_BINARY);
        Mat canny = new Mat();
        Imgproc.Canny(gray, canny, 150, 200);
        
        MatUtils.displayMat(m);
    }
    
    public static double pointToRectDist (Point p, Rect r)
    {
        List<Point> rp = new ArrayList<>();
        rp.add(new Point(r.x, r.y));
        rp.add(new Point(r.x+r.width, r.y));
        rp.add(new Point(r.x+r.width, r.y+r.height));
        rp.add(new Point(r.x, r.y+r.height));
        MatOfPoint2f contour = new MatOfPoint2f();
        contour.fromList(rp);
        double dist = Imgproc.pointPolygonTest(contour, p, true);
        
        return Math.abs(dist);
    }
    
    public static Rect findMostObviousPanel (Mat m)
    {
        // TODO: does more stuff than needed
        PanelExtractor extractor = new PanelExtractor();
        extractor.split = false;
        extractor.merge = false;
        extractor.generateData(m);
        if (extractor.contours.isEmpty())
        {
            return new Rect();
        }

        MatOfPoint2f contour2f = new MatOfPoint2f();
        contour2f.fromList(extractor.contours.get(0).toList());
        
        Imgproc.approxPolyDP(contour2f, contour2f, 8, true); // TODO: determine value based on min size between 2 corners (also: double work?)
        
        List<Point> points = contour2f.toList();

        
        HashMap<Integer, Integer> scoreMap = new HashMap<>();
        int best = 0;
        for (int i = 0 ; i < points.size()-2; ++i)
        {
            MatOfPoint subPoints = new MatOfPoint();
            subPoints.fromList(points.subList(i, i+3));
            Rect r = Imgproc.boundingRect(subPoints); // TODO: rotated rect?
            double in = Imgproc.pointPolygonTest(contour2f, new Point(r.x+r.width/2, r.y+r.height/2), false);
            if (in > 0)
            {
                int score = 0;
                for (Point p : points)
                {
                    double d = pointToRectDist(p, r);
                    if (d < 5) //TODO: relative
                    {
                        score++;
                    }
                }
                scoreMap.put(i, score);
                best = i;
            }
        }

        for (Integer index : scoreMap.keySet())
        {
            if (scoreMap.get(index) > scoreMap.get(best))
            {
                best = index;
            }
        }
        MatOfPoint subPoints = new MatOfPoint();
        subPoints.fromList(points.subList(best, best+3));
        Rect r = Imgproc.boundingRect(subPoints);
        
        return r;
    }
    
    public static void findLongestBlackLine (Mat m)
    {
        
    }
    
    public static Mat findDarkNeighbours (Mat m)
    {
        // top|left|bot|right -> bit positions (0|1|2|3)
        int[][] bools = new int[m.height()][m.width()];
        Mat gray = new Mat();
        Imgproc.cvtColor(m, gray, Imgproc.COLOR_RGB2GRAY);
        
        // initial run
        for (int y = 0; y < m.height(); ++y)
        {
            for (int x = 0; x < m.width(); ++x)
            {
                double val = gray.get(y, x)[0];
                for (int i = 0; i < 4; ++i)
                {   // this totally gets the points in the order top|left|bot|right
                    int dx = x + ((i-2)%2);
                    int dy = y + ((i-1)%2);
                    double[] neighbour = null;
                    if (dx >= 0 && dx < m.width() && dy >= 0 && dy < m.height())
                        neighbour = gray.get(dy, dx);
                    if (neighbour == null || val > neighbour[0] + 10 && neighbour[0] < 50)
                    {
                        bools[y][x] = 1<<i;
                    }
                }
            }
        }
        
        // iterate until only border pixels are black
        boolean changed;
        do
        {
            changed = false;
            for (int y = 0; y < m.height(); ++y)
            {
                for (int x = 0; x < m.width(); ++x)
                {
                    if (bools[y][x] == 0)
                        continue;
                    for (int i = 0; i < 4; ++i)
                    {
                        if ((bools[y][x] & (1<<i)) == 0)
                            continue;
                        int dx = x + ((i-2)%2);
                        int dy = y + ((i-1)%2);
                        if (dx < 0 || dy < 0 || dx == m.width() || dy == m.height())
                            continue;
                        if ((bools[dy][dx] & (1<<i)) > 0)
                        {
                            bools[y][x] &= ~(1<<i);
                            changed = true;
                        }
                    }
                }
            }
        }
        while (changed);
        
        // visualize
        gray.setTo(new Scalar(255));
        for (int y = 0; y < m.height(); ++y)
        {
            for (int x = 0; x < m.width(); ++x)
            {
                for (int i = 0; i < 4; ++i)
                {
                    if ((bools[y][x] & (1<<i)) > 0)
                    {
                        int dx = x + ((i-2)%2);
                        int dy = y + ((i-1)%2);
                        if (dx < 0 || dy < 0 || dx == m.width() || dy == m.height())
                            continue;
                        gray.put(dy, dx, 0);
                    }
                }
            }
        }
        return gray;
    }
    
    public static Mat findDarkNeighboursWithCanny (Mat m)
    {
        Mat gray = new Mat();
        Imgproc.cvtColor(m, gray, Imgproc.COLOR_RGB2GRAY);
        
        Mat canny = new Mat();
        Imgproc.Canny(gray, canny, 100, 200);
        
        for (int y = 0; y < m.height(); ++y)
        {
            for (int x = 0; x < m.width(); ++x)
            {
                double val = gray.get(y, x)[0];
                if (val > 50)
                {
                    canny.put(y, x, 0);
                }
            }
        }
        
        return canny;
    }
    
    public static void doStuff ()
    {
        Mat m = MatUtils.fileToMat(".data/testpanels/amorasC.jpg");
        //Mat m = MatUtils.fileToMat(".data/wpg/og.png");
        Mat copy = new Mat();
        m.copyTo(copy);
        //Mat custom = findDarkNeighbours(m);
        findDarkNeighboursWithCanny(m);
        
        Mat thresh = floodFillMask(m, true, new Point(0, 0), 20);
        Mat gray = new Mat();
        //Imgproc.GaussianBlur(thresh, thresh, new Size(3, 3), 2.0, 2.0);
        //Imgproc.copyMakeBorder(thresh, thresh, 50, 50, 50, 50, Imgproc.BORDER_CONSTANT, new Scalar(255, 255, 255));
        //Imgproc.medianBlur(thresh, thresh, 15);

        //Imgproc.cvtColor(m, gray, Imgproc.COLOR_RGB2GRAY);
        //Imgproc.threshold(gray, thresh, 50, 255, Imgproc.THRESH_BINARY);
        //Imgproc.adaptiveThreshold(gray, thresh, 200, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 199, 0);
        //MatUtils.displayMat(m);
        
        
        /*Rect r1 = findMostObviousPanel(copy);
        Core.rectangle(copy, r1.tl(), r1.br(), new Scalar(255, 255, 255), -1);
        Rect r2 = findMostObviousPanel(copy);
        Core.rectangle(copy, r2.tl(), r2.br(), new Scalar(255, 255, 255), -1);
        Rect r3 = findMostObviousPanel(copy);
        Core.rectangle(copy, r3.tl(), r3.br(), new Scalar(255, 255, 255), -1);
        Rect r4 = findMostObviousPanel(copy);
        Core.rectangle(copy, r4.tl(), r4.br(), new Scalar(255, 255, 255), -1);
        
        Core.rectangle(m, r1.tl(), r1.br(), new Scalar(0, 0, 255), 2);
        Core.rectangle(m, r2.tl(), r2.br(), new Scalar(0, 0, 255), 2);
        Core.rectangle(m, r3.tl(), r3.br(), new Scalar(0, 0, 255), 2);*/

        //Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(51, 51));
        //Imgproc.dilate(thresh, thresh, kernel);
        //Imgproc.erode(thresh, thresh, kernel);
        
        //Imgproc.distanceTransform(gray, gray, Imgproc.CV_DIST_L2, 3);
        //Imgproc.threshold(gray, thresh, 50, 255, Imgproc.THRESH_BINARY);
        /*thresh = new Mat(m.size(), CvType.CV_32SC1, new Scalar(0));
        Core.rectangle(thresh, new Point(), new Point(10, 10), Scalar.all(255), -1);
        Core.rectangle(thresh, new Point(140, 350), new Point(160, 370), Scalar.all(255), -1);
        Imgproc.watershed(m, thresh);
        thresh.convertTo(thresh, CvType.CV_8U);
        MatUtils.displayMat(thresh);*/
        
        /*for (int i = 0; i < m.rows(); ++i)
        {
            for (int j = 0; j < m.cols(); ++j)
            {
                double[] rgb = m.get(i, j);
                if (rgb[0] > 20 || rgb[1] > 20 || rgb[2] > 20)
                {
                    m.put(i, j, new double[]{255, 255, 255});
                }
                else
                {
                    m.put(i, j, new double[]{0, 0, 0});
                }
            }
        }
        MatUtils.displayMat(m);*/
        
        /*MatOfKeyPoint keypoints = new MatOfKeyPoint();
        FeatureDetector detector = FeatureDetector.create(FeatureDetector.GFTT);
        detector.detect(canny, keypoints);
        Features2d.drawKeypoints(m, keypoints, m);
        MatUtils.displayMat(m);*/

        Imgproc.cvtColor(m, gray, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(gray, thresh, 200, 255, Imgproc.THRESH_TOZERO_INV);
        Imgproc.threshold(thresh, thresh, 50, 255, Imgproc.THRESH_BINARY);
        //Core.bitwise_not(thresh, thresh);

        /*Imgproc.threshold(gray, thresh, 40, 255, Imgproc.THRESH_BINARY);
        MatUtils.displayMat(thresh);
        MatUtils.displayMat(MatUtils.zhangSuenThinning(thresh));*/
        
        Imgproc.resize(m, m, new Size(), 2, 2, Imgproc.INTER_AREA);
        
        Mat canny = new Mat();
        //Imgproc.GaussianBlur(thresh, thresh, new Size(7, 7), 0);
        //Imgproc.medianBlur(thresh, thresh, 11);
        //Mat m2 = new Mat(m.size(), CvType.CV_8UC3);
        //Imgproc.bilateralFilter(m, m2, 10, 500, 500);
        //m = m2;
        //Imgproc.Canny(custom, canny, 100, 200);
        canny = findDarkNeighbours(m);
        //Imgproc.Canny(canny, canny, 100, 200);
        Core.bitwise_not(canny, canny);

        Mat lines = new Mat();
        Imgproc.HoughLinesP(canny, lines, 1, Math.PI/180, 150, 80, 5);
        for (int i = 0; i < lines.size().width; ++i) {
            double[] out = lines.get(0, i);
            double slope;
            double intercept;
            if ((out[2] - out[0])/(out[2] + out[0]) > 0.01)
            {
                slope = (out[3] - out[1])/(out[2] - out[0]);
                intercept = out[1] - slope*out[0];
                //Core.line(m, new Point(0, intercept), new Point(m.width(), slope*m.width()+intercept), new Scalar(255, 0, 0), 1);
            }
            else
            {
                //Core.line(m, new Point(out[0], 0), new Point(out[2], m.height()), new Scalar(255, 0, 0), 1);
            }
            Core.line(m, new Point(out[0], out[1]), new Point(out[2], out[3]), new Scalar(0, 0, 255), 2);
            Core.circle(m, new Point(out[0], out[1]), 2, new Scalar(0, 255, 0), 2);
            Core.circle(m, new Point(out[2], out[3]), 2, new Scalar(0, 255, 0), 2);
        }
        MatUtils.displayMat(m);

        /*List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(custom, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        MatUtils.drawContours(m, contours);
        MatUtils.displayMat(m);*/
        
        /*Mat corners = new Mat();
        //Imgproc.cornerHarris(thresh, corners, 2, 3, 0.04);
        Imgproc.cornerHarris(gray, corners, 2, 3, 0.04);
        
        Core.normalize(corners, corners, 0, 255, Core.NORM_MINMAX);
        Core.convertScaleAbs(corners, corners);
        
        List<Point> cornerList = new ArrayList<>();
        
        for (int x = 0; x < corners.width(); ++x)
        {
            for (int y = 0; y < corners.height(); ++y)
            {
                double[] data = corners.get(y, x);
                if (data[0] > 170)
                {
                    //System.out.println(data[0]);
                    Core.circle(m, new Point(x, y), 2, new Scalar(255, 0, 0), 2);
                    cornerList.add(new Point(x, y));
                }
            }
        }
        MatUtils.displayMat(m);*/
        
        /*MatOfPoint out = new MatOfPoint();
        Imgproc.goodFeaturesToTrack(thresh, out, 3, 0.01, 5);
        for (Point p : out.toList())
        {
            Core.circle(m, p, 2, new Scalar(255, 0, 0), 2);
        }*/

        //MatUtils.displayMat(thresh);
        //MatUtils.displayMat(m);
    }
}
