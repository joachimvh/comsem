package test;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

public class ExactTextMatch implements Comparable<ExactTextMatch> {

    public MatOfPoint contour;
    public Rect r;
    public Mat m;
    public String s;
    public double score;
    
    public ExactTextMatch ()
    {
        this.contour = new MatOfPoint();
        this.r = new Rect();
        this.m = new Mat();
        this.s = "*";
        this.score = 0;
    }
    
    public ExactTextMatch (MatOfPoint contour, Mat m)
    {        
        this.contour = contour;
        this.m = m;
        this.r = Imgproc.boundingRect(contour);
        this.s = "*";
        this.score = 0;
    }
    
    public int compareTo(ExactTextMatch o) {
        // TODO: 0.9 needed for character overlap ...
        if (this.r.y + this.r.height * 0.9 < o.r.y)
            return -1;
        else if (o.r.y + o.r.height * 0.9 < this.r.y)
            return 1;
        return this.r.x - o.r.x;
    }
}
