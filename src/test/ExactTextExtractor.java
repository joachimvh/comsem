package test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

public class ExactTextExtractor 
{

    public int threshold;
    public boolean verifyTextBox;
    public boolean merge;
    public boolean order;
    public boolean addWhitespace;
    
    public Map<Integer, List<ExactTextMatch>> charData;
    
    public ExactTextExtractor ()
    {
        this.threshold = 250;
        this.verifyTextBox = true;
        this.merge = true;
        this.order = true;
        this.addWhitespace = true;
    }
    
    public void generateData (Mat m)
    {
        this.charData = new HashMap<>();
        
        Map<Integer, List<MatOfPoint>> children = this.groupContoursByParent(m);
        
        for (int parent : children.keySet())
        {
            // TODO: split colours?
            // Mat box = this.cropText(m, children.get(parent));
            
            List<MatOfPoint> contours = children.get(parent);
            
            if (this.merge)
                contours = this.mergeContours(contours);
            
            if (!contours.isEmpty())
            {
                this.charData.put(parent, new ArrayList<ExactTextMatch>());
            }
            
            List<ExactTextMatch> parentCharData = new ArrayList<>();
            
            for (MatOfPoint contour : contours)
            {
                Mat charMat = this.contourToMat(m, contour);
                parentCharData.add(new ExactTextMatch(contour, charMat));
            }
            
            if (this.order)
            {
                parentCharData = this.orderChars(parentCharData);
            }
            this.charData.put(parent, parentCharData);
        }
    }
    
    // --------------------- TEXT BLOCK DETECTION ----------------------------
    private Map<Integer, List<MatOfPoint>> groupContoursByParent (Mat m)
    {
        Mat thresh = new Mat();
        
        Imgproc.cvtColor(m, thresh, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(thresh, thresh, this.threshold, 255, Imgproc.THRESH_BINARY);
        
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        
        // find the contours and categorize by parent (all characters in a text box should have the same parent)
        Map<Integer, List<MatOfPoint>> children = new HashMap<>();
        for (int i = 0; i < hierarchy.width(); ++i) {
            int parent = (int)hierarchy.get(0, i)[3];
            if (parent >= 0) 
            {
                if (children.get(parent) == null)
                {
                    children.put(parent, new ArrayList<MatOfPoint>());
                }
                children.get(parent).add(contours.get(i));
            }
        }
        
        Map<Integer, List<MatOfPoint>> validChildren = new HashMap<>();
        for (int parent : children.keySet())
        {
            if (!this.verifyTextBox || this.isTextBox(children.get(parent)))
                validChildren.put(parent, children.get(parent));
        }
        
        return validChildren;
    }
    
    private boolean isTextBox (List<MatOfPoint> contours) 
    {
        List<Rect> rects = new ArrayList<>();
        
        // ignore samples of < 5 chars?
        if (contours.size() < 5)
            return false;
        
        for (MatOfPoint contour : contours)
            rects.add(Imgproc.boundingRect(contour));
        
        long mean = 0;
        
        for (Rect r : rects)
            mean += r.area();
        
        mean /= rects.size();
        
        for (Rect r : rects) 
        {
            if (mean / r.area() < 4)
            {
                // all letters are higher than they are wide?
                // TODO: problem if characters stick to each other
                //if (r.height < r.width)
                    //return false;
                // but they also can't be too high?
                if (r.height > r.width * 5)
                    return false;
            }
            // letters can't be that much bigger than average?
            // TODO: also problem with sticking
            //if (r.area() / mean > 4)
                //return false;
            
            // characters should be close together
            boolean foundNeighbour = false;
            for (Rect r2 : rects)
            {
                if (r != r2)
                {
                    if (dist(r, r2) < (r.width + r.height)/2)
                    {
                        foundNeighbour = true;
                        break;
                    }
                }
            }
            if (!foundNeighbour)
                return false;
        }
        
        return true;
    }
    
    private double dist(Rect r1, Rect r2)
    {
        int dx = r1.x-r2.x;
        int dy = r1.y-r2.y;
        double dist = Math.sqrt(dx*dx + dy*dy) - (r1.width + r1.width)/2 - (r2.width + r2.height)/2;// not exact
        dist = Math.max(0, dist);
        return dist;
    }
    
    private Mat cropText (Mat m, List<MatOfPoint> contours)
    {
        Rectangle bound = null;
        for (MatOfPoint child : contours) {
            Rect r = Imgproc.boundingRect(child);
            if (bound == null)
                bound = new Rectangle(r.x, r.y, r.width, r.height);
            else
                bound = bound.union(new Rectangle(r.x, r.y, r.width, r.height));
        }
        
        Mat mask = new Mat(m.size(), CvType.CV_8UC1, new Scalar(0));
        Mat crop = new Mat(mask.size(), CvType.CV_8UC3, new Scalar(255, 255, 255));
        Imgproc.drawContours(mask, contours, -1, new Scalar(255), Core.FILLED);
        m.copyTo(crop, mask);
        crop = crop.submat(bound.y, (int)bound.getMaxY()-1, bound.x, (int)bound.getMaxX()-1);
        
        return crop;
    }
    
    // ------------------------- INDIVIDUAL CHARACTER DETECTION ---------------------
    
    private double sqrdBlobDistance (MatOfPoint blob1, MatOfPoint blob2) 
    {
        double distance = Double.MAX_VALUE;
        List<Point> points1 = blob1.toList();
        List<Point> points2 = blob2.toList();
        for (int p1 = 0; p1 < points1.size(); ++p1) 
        {
            for (int p2 = 0; p2 < points2.size(); ++p2) 
            {
                Point point1 = points1.get(p1);
                Point point2 = points2.get(p2);
                double dx = point1.x - point2.x;
                double dy = point1.y - point2.y;
                double dist = dx*dx + dy*dy;
                if (dist < distance) 
                {
                    distance = dist;
                }
            }
        }
        return distance;
    }
    
    private List<MatOfPoint> textBoxToContours (Mat m)
    {
        Imgproc.copyMakeBorder(m, m, 5, 5, 5, 5, Imgproc.BORDER_CONSTANT, new Scalar(255, 255, 255)); // characters on borders give problems
        Mat gray = new Mat();
        Mat thresh = new Mat();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        
        Imgproc.cvtColor(m, gray, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(gray, thresh, threshold, 255, Imgproc.THRESH_BINARY); // should already be correct because of text though
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        
        List<MatOfPoint> matches = new ArrayList<>();
        // extract the contour data without the surroundings
        for (int i = 0; i < hierarchy.width(); ++i) 
        {
            if (hierarchy.get(0, i)[3] >= 0) // TODO: will this always give good results?
            {
                matches.add(contours.get(i));
            }
        }
        
        return matches;
    }
    
    // TODO: comma on line 1 gets matched with ! on line 2 beneath it, somehow find wich line a dot belongs to?
    private int findClosestContour (List<MatOfPoint> contours, int idx)
    {
        List<MatOfPoint> neighbours = new ArrayList<>();
        
        MatOfPoint contour = contours.get(idx);
        double diameterSqrd = Imgproc.contourArea(contour)*4/Math.PI; // TODO: use better formula
        Rect rect = Imgproc.boundingRect(contour);
        double closestDist = Double.MAX_VALUE;
        int closest = -1;
        for (int i = 0; i < contours.size(); ++i)
        {
            MatOfPoint neighbour = contours.get(i);
            if (neighbour != contour && Imgproc.contourArea(neighbour) > 1000) // TODO: ratio as parameter
            { 
                Rect neighbourRect = Imgproc.boundingRect(neighbour);
                if (rect.x > neighbourRect.x && rect.x < neighbourRect.br().x ||
                        rect.br().x > neighbourRect.x && rect.br().x < neighbourRect.br().x) // needs to be in the same x space
                { 
                    if (rect.y > neighbourRect.br().y || rect.br().y < neighbourRect.y) // no overlap?
                    {
                        // TODO: fix for dot getting matched to wrong line: make sure its neighbours are also next to the main body
                        //       (it is possible it has no neighbours though... eie, neighbour can be further away maybe)
                        double distanceSqrd = sqrdBlobDistance (contour, neighbour);
                        if (distanceSqrd < diameterSqrd && distanceSqrd < closestDist) 
                        {
                            closestDist = distanceSqrd;
                            closest = i;
                        }
                    }
                }
            }
        }
        
        return closest;
    }
    
    private MatOfPoint mergeContourWithNeighbour (MatOfPoint contour, MatOfPoint neighbour)
    {
        // TODO: white line through character if the match point is on the other side of the contour
        MatOfPoint mergedContour = new MatOfPoint();
        ArrayList<Point> mergedList = new ArrayList<>();
        mergedList.addAll(contour.toList());
        mergedList.addAll(neighbour.toList());
        mergedContour.fromList(mergedList);
        
        return mergedContour;
    }
    
    private List<MatOfPoint> mergeContours (List<MatOfPoint> children) 
    {
        //List<MatOfPoint> contours = this.textBoxToContours(m);
        List<MatOfPoint> contours = new ArrayList<>(children);
        
        // find close contours (to match i with its dot)
        Map<Integer, Integer> neighbours = new HashMap<>();
        for (int i = 0; i < contours.size(); ++i) 
        {
            MatOfPoint contour = contours.get(i);
            if (Imgproc.contourArea(contour) < 1000) // TODO: ratio as parameter
            { 
                int neighbourIdx = this.findClosestContour(contours, i);
                if (neighbourIdx >= 0)
                {
                    neighbours.put(i, neighbourIdx);
                }
            }
        }
        
        // TODO: check if an element isn't neighbour to multiple other elements
        for (Integer idx : neighbours.keySet())
        {
            // we update the matrix in the location of the big contour, because others might also be added there (e.g. trema)
            MatOfPoint contour = contours.get(idx);
            contours.set(neighbours.get(idx), this.mergeContourWithNeighbour(contour, contours.get(neighbours.get(idx))));
        }
        
        TreeSet<Integer> sortedIndices = new TreeSet<>(Collections.reverseOrder());
        sortedIndices.addAll(neighbours.keySet());
        for (int idx : sortedIndices)
        {
            contours.remove(idx);
        }
        
        return contours;
    }
    
    private Mat contourToMat (Mat m, MatOfPoint contour)
    {
        Rect r = Imgproc.boundingRect(contour);
        r.width--;
        r.height--;
        
        MatOfPoint shiftedContour = new MatOfPoint();
        List<Point> shiftedPoints = new ArrayList<>();
        for (Point p : contour.toList())
        {
            shiftedPoints.add(new Point(p.x-r.x, p.y-r.y));
        }
        shiftedContour.fromList(shiftedPoints);
        
        Mat sub = m.submat(r); // start with submat to reduce size of crop and mask
        Mat mask = new Mat(sub.rows(), sub.cols(), CvType.CV_8UC1, new Scalar(0));
        Mat crop = new Mat(sub.rows(), sub.cols(), CvType.CV_8UC3, new Scalar(255, 255, 255));
        Imgproc.drawContours(mask, Arrays.asList(shiftedContour), -1, new Scalar(255), Core.FILLED);
        
        sub.copyTo(crop, mask);
        
        return crop;
    }
    
    private List<ExactTextMatch> orderChars (List<ExactTextMatch> charData)
    {
        // determine char order and add spaces/newlines
        List<ExactTextMatch> result = new ArrayList<>(charData);
        Collections.sort(result);
        
        int i = 1;
        while (i < result.size()) {
            Rect prev = result.get(i-1).r;
            Rect r = result.get(i).r;
            
            if (this.addWhitespace)
            {
                if (r.y > prev.br().y)
                {
                    ExactTextMatch etc = new ExactTextMatch();
                    etc.s = "\n";
                    etc.score = 1;
                    etc.r = new Rect(r.x-1, r.y, 1, 1);
                    result.add(i, etc);
                    ++i;
                }
                else if (sqrdBlobDistance(result.get(i-1).contour, result.get(i).contour) > 800) // TODO: determine this value
                {
                    ExactTextMatch etc = new ExactTextMatch();
                    etc.s = " ";
                    etc.score = 1;
                    etc.r = new Rect((int)prev.br().x, r.y, r.x-(int)prev.br().x, 1);
                    result.add(i, etc);
                    ++i;
                }
            }
            ++i;
        }
        
        return result;
    }
}
