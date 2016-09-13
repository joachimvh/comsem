package test;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.opencv.core.Core;
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
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

public class ExactTextMatcher 
{
    
    public List<Character> legalChars;
    public Map<Character, Mat> charImages;
    public Font font;
    public int fontPts;
    public double whiteRatio;
    public double minVal;
    
    public ExactTextMatcher ()
    {
        this.legalChars = new ArrayList<>();
        for (char c = 'a'; c <= 'z'; ++c)
            this.legalChars.add(c);
        for (char c = 'A'; c <= 'Z'; ++c)
            this.legalChars.add(c);
        for (int i = 0; i <= 9; ++i)
            this.legalChars.add(Character.forDigit(i, 10));
        this.legalChars.addAll(Arrays.asList('!', '?', ',', '.', '\'')); // , 'ó', 'é'
        
        this.whiteRatio = 0.9;
        this.minVal = 0.8;
        
        this.fontPts = 220;
        try
        {
            this.font = Font.createFont(Font.TRUETYPE_FONT, new File("SuskewiskeNew.ttf"));
            this.font = this.font.deriveFont((float)this.fontPts);
        }
        catch (IOException | FontFormatException ex)
        {
            ex.printStackTrace();
            System.exit(1);
        }
        
        this.charImages = charsToMats(this.legalChars, this.font);
    }
    
    public String matToString (ExactTextMatch etc)
    {
        if (etc.score >= 0.9)
            return etc.s;
        return bestTemplateMatch(etc.m, this.charImages);
    }
    
    private Map<Character, Mat> charsToMats (Collection<Character> chars, Font f)
    {
        Map<Character, Mat> charImages = new HashMap<>();
        //Font font = f.deriveFont((float)pts);
        for (char c : chars) 
        {
            charImages.put(c, charToMat(c, f));
        }
        return charImages;
    }
    
    private Mat charToMat (char c, Font f)
    {
        Rectangle2D rect = getCharBounds (c, f);
        
        // draw character
        BufferedImage img = new BufferedImage((int) Math.ceil(rect.getWidth()), (int) Math.ceil(rect.getHeight()), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = (Graphics2D)img.getGraphics();
        g.setBackground(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(f);
        
        // make sure there is no whitespace left around the image
        int x = -(int)rect.getMinX();
        int y = -(int)rect.getMinY() + 1;
        g.drawString(c+"", x, y);
        g.dispose();
        
        Mat template = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC3);
        template.put(0, 0, ((DataBufferByte)img.getData().getDataBuffer()).getData());
        
        return template;
    }
    
    private Rectangle2D getCharBounds (char c, Font f)
    {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = (Graphics2D)img.getGraphics();
        g.setFont(f);
        FontRenderContext frc = g.getFontRenderContext();
        
        // determine character size
        TextLayout layout = new TextLayout(c+"", f, frc);
        Rectangle2D rect = layout.getBounds();
        
        return rect;
    }

    private String bestTemplateMatch (Mat m, Map<Character, Mat> templates)
    {
        StringBuilder builder = new StringBuilder();
        TreeSet<MatchResult> results = bestTemplateMatch(m, templates, true);
        for (MatchResult result : results)
        {
            builder.append(result.c);
        }
        return builder.toString();
    }
    
    private TreeSet<MatchResult> bestTemplateMatch (Mat m, Map<Character, Mat> templates, boolean addBorder)
    {
        double bestVal = this.minVal;
        char bestChar = '*';
        Point bestPos = new Point();
        Mat target = new Mat();
        // padding makes sure the template image isn't a few pixels bigger than what we want to match with
        if (addBorder)
        {
            Imgproc.copyMakeBorder(m, target, 5, 5, 5, 5, Imgproc.BORDER_CONSTANT, new Scalar(255, 255, 255));
        }
        else
        {
            m.copyTo(target);
        }
        for (char c : templates.keySet()) {
            Mat template = templates.get(c);
            MinMaxLocResult mm = null;
            double val = 0;
            
            // only try matching if template is smaller (else Imgproc.match crashes)
            if (template.width() <= target.width() && template.height() <= target.height()) {
                Mat out = new Mat();
                Imgproc.matchTemplate(target, template, out, Imgproc.TM_CCOEFF_NORMED);
                mm = Core.minMaxLoc(out);
                val = mm.maxVal;
            }
            
            if (val > bestVal) {
                bestVal = val;
                bestChar = c;
                bestPos = mm.maxLoc;
            }
        }
        
        // remove the letter from the image and check if there are enough black pixels remaining to find a new character
        TreeSet<MatchResult> results = new TreeSet<>();
        results.add(new MatchResult(bestPos, bestChar, bestVal));
        if (templates.containsKey(bestChar))
        {
            Mat mask = new Mat();
            templates.get(bestChar).copyTo(mask);
            Imgproc.copyMakeBorder(mask, mask, (int)bestPos.y, (int)(target.rows()-mask.rows()-bestPos.y), (int)bestPos.x, (int)(target.cols()-mask.cols()-bestPos.x), Imgproc.BORDER_CONSTANT, new Scalar(255, 255, 255));
            Core.bitwise_not(mask, mask);
            Core.add(target, mask, target);
    
            Imgproc.cvtColor(target, mask, Imgproc.COLOR_RGB2GRAY);
            
            int whites = Core.countNonZero(mask);
            if (1.0*whites/target.size().area() < this.whiteRatio)
            {
                results.addAll(bestTemplateMatch(target, templates, false));
            }
        }
        return results;
    }
    
    private class MatchResult implements Comparable<MatchResult>
    {
        Point p;
        char c;
        double score;
        public MatchResult (Point p, char c, double score)
        {
            this.p = p;
            this.c = c;
            this.score = score;
        }
        public int compareTo(MatchResult o) {
            return (int)(this.p.x - o.p.x);
        }
    }
    
    
    // ------------------------- STATIC STUFF ------------------------------------
    
    public static void extract () {
        //String path = ".data/wpg/SW32-DRR-009-012-page-03B_panel.png";
        String path = ".data/got/George R.R. Martin's - A Game Of Thrones #01/GOT 1_0003.jpg";
        Mat m = Highgui.imread(path);
        
        /*String pathColor = ".data/wpg/SW32-DRR-009-012-lay-03.cmy2.tif";
        Mat m2 = Highgui.imread(pathColor);
        
        Imgproc.resize(m, m, m2.size());
        Core.bitwise_and(m, m2, m);*/
        
        /*Core.bitwise_not(m, m);
        m.convertTo(m, -1, 5, 0);
        Core.bitwise_not(m, m);*/
        
        //findCharacters(m);
        //extractFont();
        //match();
        //bestMatch(m);
        findText(m);
    }
    
    public static void findText (Mat m) {
        Mat thresh = new Mat();
        
        Imgproc.cvtColor(m, thresh, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(thresh, thresh, 100, 255, Imgproc.THRESH_BINARY);
        //Imgproc.erode(thresh, thresh, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(25, 25))); // disadvantage: can't use these contours for character detection
        Highgui.imwrite(".data/got/thresh.png", thresh);
        
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        
        Map<Integer, List<MatOfPoint>> children = new HashMap<>();
        for (int i = 0; i < hierarchy.width(); ++i) {
            if (hierarchy.get(0, i)[3] >= 0) {
                //System.out.println(Arrays.toString(hierarchy.get(0, i)));
                int parent = (int)hierarchy.get(0, i)[3];
                if (children.get(parent) == null)
                    children.put(parent, new ArrayList<MatOfPoint>());
                children.get(parent).add(contours.get(i));
            }
        }
        
        // TODO: filter out rects that completely contain an other rect?
        for (Integer parent : children.keySet()) {
            if (isTextBox(children.get(parent))) {
                Rectangle bound = null;
                for (MatOfPoint child : children.get(parent)) {
                    Rect r = Imgproc.boundingRect(child);
                    //Core.rectangle(m, r.tl(), r.br(), new Scalar(0, 0, 255), 3);
                    if (bound == null)
                        bound = new Rectangle(r.x, r.y, r.width, r.height);
                    else
                        bound = bound.union(new Rectangle(r.x, r.y, r.width, r.height));
                }
                
                Mat mask = new Mat(m.size(), CvType.CV_8UC1, new Scalar(0));
                Mat crop = new Mat(mask.size(), CvType.CV_8UC3, new Scalar(255, 255, 255));
                Imgproc.drawContours(mask, contours, parent, new Scalar(255), Core.FILLED);
                m.copyTo(crop, mask);
                crop = crop.submat(bound.y, (int)bound.getMaxY()-1, bound.x, (int)bound.getMaxX()-1);
                //bestMatch(crop);

                Core.rectangle(m, new Point(bound.x, bound.y), new Point(bound.getMaxX(), bound.getMaxY()), new Scalar(0, 0, 255), 10);
                for (MatOfPoint child : children.get(parent)) {
                    Rect r = Imgproc.boundingRect(child);
                    //Core.rectangle(m, r.tl(), r.br(), new Scalar(0, 0, 255), 3);
                }
                //Imgproc.drawContours(m, contours, parent, new Scalar(0, 0, 255), 10);
            }
        }

        Highgui.imwrite(".data/wpg/SW_Text_Detection.png", m);
    }
    
    private static boolean isTextBox (List<MatOfPoint> contours) {
        List<Rect> rects = new ArrayList<>();
        
        // ignore samples of <5 chars?
        // TODO: put this back
        if (contours.size() < 3)
            return false;
        
        for (MatOfPoint contour : contours)
            rects.add(Imgproc.boundingRect(contour));
        
        long mean = 0;
        
        for (Rect r : rects)
            mean += r.area();
        
        mean /= rects.size();
        
        for (Rect r : rects) {
            if (mean / r.area() < 4) {
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
        }
        
        return true;
    }
    
    /*private static class MatchResult implements Comparable<MatchResult> {
        public char c;
        public int pointSize;
        public int pixelSize;
        public double score;
        public MatchResult (char c, int pointSize, int pixelSize, double score) {
            this.c = c;
            this.pointSize = pointSize;
            this.pixelSize = pixelSize;
            this.score = score;
        }
        public String toString () {
            return String.format("%s:%2dpt:%2dpx(%.2f)", ""+c, pointSize, pixelSize, score);
        }
        public int compareTo(MatchResult o) {
            return -1*(int)(1000*(score - o.score)); // want reverse sort
        }
    }
    
    private static void findBestCharacter () {
        Mat target = Highgui.imread(".data/wpg/letters/142.jpg");
        //Imgproc.copyMakeBorder(target, target, 5, 5, 5, 5, Imgproc.BORDER_CONSTANT, new Scalar(255, 255, 255)); // makes sure template isn't bigger than the target
        MatchResult[] results = new MatchResult[target.cols()];
        for (int i = 0; i < target.cols(); ++i)
            results[i] = new MatchResult(' ', 0, 0, 0);
        List<Character> chars = new ArrayList<>();
        for (char c = 'a'; c <= 'z'; ++c)
            chars.add(c);
        for (char c = 'A'; c <= 'Z'; ++c)
            chars.add(c);
        for (char c : chars) {
            for (int points = 210; points <= 220; ++points) {
                try {
                    Mat out = getCharacterMatch(target, points, c);
                    for (int i = 0; i < out.cols()-0; ++i) {
                        for (int j = 0; j < out.rows()-0; ++j) {
                            double val = out.get(j, i)[0];
                            if (val > results[i].score) { // maybe save top 3 to take if current character is too big
                                results[i].c = c;
                                results[i].pointSize = points;
                                results[i].pixelSize = target.cols() - out.cols() + 1;
                                results[i].score = val;
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }
            }
        }

        // ignore padding
        for (int i = 0; i < 10 ; ++i)
            System.out.println(results[i]);
        matchResultToString(results, 0);
    }
    
    private static void matchResultToString (MatchResult[] results, int padding) {        
        TreeMap<Integer, Character> foundChars = new TreeMap<>();
        
        do {            
            // find highest idx
            int best = padding;
            for (int i = padding; i < results.length-padding; ++i) {
                if (results[i].score > results[best].score)
                    best = i;
            }
            if (results[best].score < 0.7)
                break;
            
            // TODO: combinations of characters can take up less space, should write string and check size -> problem with different character spacing?
            // TODO: or: keep trying to make best matching string and keep comparing images
            // check if there is enough space left
            boolean space = true;
            if (best + results[best].pixelSize > results.length - padding) {
                space = false;
            } else {
                for (int i = best; i < best + results[best].pixelSize - 1; ++i)
                    space &= results[i].score != -1;
            }
            if (space) {
                for (int i = best; i < best + results[best].pixelSize - 1; ++i)
                    results[i].score = -1;
                
                foundChars.put(best, results[best].c);
            } else {
                results[best].score = 0;
            }
            
        } while (true);
        System.out.println(foundChars);
    }*/
    
    /*private static Map<Character, Mat> charImages;
    private static void bestMatch (Mat m) {
        try {
            //Mat target = Highgui.imread(".data/wpg/letters/45.jpg");
            Font f = Font.createFont(Font.TRUETYPE_FONT, new File("SuskewiskeNew.ttf"));
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = (Graphics2D)img.getGraphics();
            
            List<Character> chars = new ArrayList<>();
            for (char c = 'a'; c <= 'z'; ++c)
                chars.add(c);
            for (char c = 'A'; c <= 'Z'; ++c)
                chars.add(c);
            for (int i = 0; i <= 9; ++i)
                chars.add(Character.forDigit(i, 10));
            chars.addAll(Arrays.asList('!', '?'));
            
            // TODO: check smaller punctuation if no better result is obtained?
            
            charImages = new HashMap<>();
            for (char c : chars) {
                Font font = f.deriveFont((float)220);
                g.setFont(f);
                FontRenderContext frc = g.getFontRenderContext();
                charImages.put(c, charToMat(font, frc, c));
            }
            
            // TODO: determine size
            long time = System.currentTimeMillis();
            List<CharacterMatch> matches = findCharacters(m);
            for (CharacterMatch match : matches) {
                char c = bestCharForPoints (match.crop, charImages);
                match.c = c;
                // System.out.println(match.idx + ": " + c);
            }
            //System.out.println(bestCharForPoints (Highgui.imread(".data/wpg/letters/136.jpg"), charImages));
            
            Collections.sort(matches);
            
            StringBuffer result = new StringBuffer();
            result.append(matches.get(0).c);
            for (int i = 1; i < matches.size(); ++i) {
                Rect prev = matches.get(i-1).r;
                Rect r = matches.get(i).r;
                
                if (r.y > prev.br().y)
                    result.append('\n');
                //else if (r.x > prev.br().x + 10) // TODO: determine this value
                    //result.append(' ');
                else if (blobDistance(matches.get(i-1).contour, matches.get(i).contour) > 1000) // TODO: determine this value
                    result.append(' ');
                
                result.append(matches.get(i).c);
            }
            System.out.println(result.toString());
            
            System.out.println(System.currentTimeMillis() - time);
            
            // character results to String
            
            g.dispose();
        } catch (IOException | FontFormatException ex) {
            ex.printStackTrace();
        }
    }*/
    
    private static char bestCharForPoints (Mat m, Map<Character, Mat> charImages) {
        double best = 0;
        char bestChar = '*';
        /*Font f = original.deriveFont((float)points);
        g.setFont(f);
        FontRenderContext frc = g.getFontRenderContext();*/
        Mat target = new Mat();
        Imgproc.copyMakeBorder(m, target, 5, 5, 5, 5, Imgproc.BORDER_CONSTANT, new Scalar(255, 255, 255));
        for (char c : charImages.keySet()) {
            Mat template = charImages.get(c);
            Mat out = new Mat();
            double val = 0;
            if (template.width() <= target.width() && template.height() <= target.height()) {
                Imgproc.matchTemplate(target, template, out, Imgproc.TM_CCOEFF_NORMED);
                val = Core.minMaxLoc(out).maxVal;
                System.out.println(c + " " + val);
            }
            // TODO: why is g not getting goed values here when attached to o?
            // probably because the whitespace of the 'g' image overlaps with the o?
            if (val > best) {
                best = val;
                bestChar = c;
            }
        }
        return bestChar;
    }
    
    private static Mat charToMat (Font f, FontRenderContext frc, char c) {
        String input = "" + c;
        
        // determine character size
        //BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
        //Graphics2D g = (Graphics2D)img.getGraphics();
        //Font f = new Font("SuskewiskeNew", Font.PLAIN, points);
        //Font f = Font.createFont(Font.TRUETYPE_FONT, new File("SuskewiskeNew.ttf")); // TODO: don't load font in every call ...
        //f = f.deriveFont((float)points);
        //g.setFont(f);
        //TextLayout layout = new TextLayout(input, f, g.getFontMetrics().getFontRenderContext());
        TextLayout layout = new TextLayout(input, f, frc);
        Rectangle2D rect = layout.getBounds();
        //g.dispose();
        
        // draw character
        BufferedImage img = new BufferedImage((int) Math.ceil(rect.getWidth()), (int) Math.ceil(rect.getHeight()), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = (Graphics2D)img.getGraphics();
        g.setBackground(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(f);
        //g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int x = -(int)rect.getMinX();
        int y = -(int)rect.getMinY() + 1;
        g.drawString(input, x, y);
        g.dispose();
        
        // copy to matrix and match
        Mat template = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC3);
        template.put(0, 0, ((DataBufferByte)img.getData().getDataBuffer()).getData());
        
        return template;
    }
    
    /*private static double getCharacterMatch (Mat m, Font f, FontRenderContext frc, char c) {
        //Mat template = charToMat(f, frc, c);
        Mat template = charImages.get(c);
        Mat out = new Mat();
        if (template.width() > m.width() || template.height() > m.height())
            return 0;
        Imgproc.matchTemplate(m, template, out, Imgproc.TM_CCOEFF_NORMED);
        
        return Core.minMaxLoc(out).maxVal;
    }*/
    
    /*private static void match () {
        Mat template = Highgui.imread(".data/wpg/letters/test.png");
        Mat target = Highgui.imread(".data/wpg/letters/20.jpg");
        Mat out = new Mat();
        Imgproc.copyMakeBorder(target, target, 5, 5, 5, 5, Imgproc.BORDER_CONSTANT, new Scalar(255, 255, 255)); // makes sure template isn't bigger than the target
        Imgproc.matchTemplate(target, template, out, Imgproc.TM_CCOEFF_NORMED);
        System.out.println(out.dump());
    }*/
    
    private static void extractFont () {
        String input = "Wiske! Wat sta je daar te niksen?!";
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = (Graphics2D)img.getGraphics();

        //Set the font to be used when drawing the string
        Font f = new Font("SuskewiskeNew", Font.PLAIN, 220);
        g.setFont(f);
        
        //Get the string visual bounds
        FontRenderContext frc = g.getFontMetrics().getFontRenderContext();
        Rectangle2D rect = f.getStringBounds(input, frc);
        TextLayout layout = new TextLayout(input, f, frc);
        rect = layout.getBounds();
        //Release resources
        g.dispose();

        //Then, we have to draw the string on the final image

        //Create a new image where to print the character
        img = new BufferedImage((int) Math.ceil(rect.getWidth()), (int) Math.ceil(rect.getHeight()), BufferedImage.TYPE_3BYTE_BGR);
        g = (Graphics2D)img.getGraphics();
        g.setBackground(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK); //Otherwise the text would be white
        g.setFont(f);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        //Calculate x and y for that string
        //FontMetrics fm = g.getFontMetrics();
        int x = -(int)rect.getMinX();
        int y = -(int)rect.getMinY() + 1; //fm.getAscent(); //getAscent() = baseline
        g.drawString(input, x, y);

        //Release resources
        g.dispose();
        
        try {
            ImageIO.write(img, "png", new File(".data/wpg/letters/test.png"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    /*private static Mat floodFill (Mat m) {
        Mat mask = Mat.zeros(m.rows() + 2, m.cols() + 2, CvType.CV_8UC1);
        Imgproc.floodFill(m, mask, new Point(0, 0), new Scalar(0, 0, 255), null, new Scalar(60, 60, 60), new Scalar(20, 20, 20), Imgproc.FLOODFILL_FIXED_RANGE | (255 << 8) | Imgproc.FLOODFILL_MASK_ONLY);
        
        return mask;
    }*/
    
    /*private static int closestBlob (int idx, List<MatOfPoint> contours) {
        double distance = Double.MAX_VALUE;
        int closest = -1;
        List<Point> points = contours.get(idx).toList();
        for (int i = 0; i < contours.size(); ++i) {
            if (i == idx)
                continue;
            
            List<Point> contour = contours.get(i).toList();
            
            for (int p1 = 0; p1 < contour.size(); ++p1) {
                for (int p2 = 0; p2 < points.size(); ++p2) {
                    Point point1 = contour.get(p1);
                    Point point2 = points.get(p2);
                    double dx = point1.x - point2.x;
                    double dy = point1.y - point2.y;
                    double dist = dx*dx + dy*dy;
                    if (dist < distance) {
                        distance = dist;
                        closest = i;
                    }
                }
            }
        }
        return closest;
    }*/
    
    private static double blobDistance (MatOfPoint blob1, MatOfPoint blob2) {
        double distance = Double.MAX_VALUE;
        List<Point> points1 = blob1.toList();
        List<Point> points2 = blob2.toList();
        for (int p1 = 0; p1 < points1.size(); ++p1) {
            for (int p2 = 0; p2 < points2.size(); ++p2) {
                Point point1 = points1.get(p1);
                Point point2 = points2.get(p2);
                double dx = point1.x - point2.x;
                double dy = point1.y - point2.y;
                double dist = dx*dx + dy*dy;
                if (dist < distance) {
                    distance = dist;
                }
            }
        }
        return distance;
    }
    
    private static class CharacterMatch implements Comparable<CharacterMatch> {
        public int idx;
        public Rect r;
        public MatOfPoint contour;
        public Mat crop;
        public char c;
        public CharacterMatch (int idx, Rect r, MatOfPoint contour, Mat crop) {
            this.idx = idx;
            this.r = r;
            this.contour = contour;
            this.crop = crop;
        }
        public String toString () {
            return idx + "";
        }
        public int compareTo(CharacterMatch o) {
            if (this.r.br().y < o.r.y)
                return -1;
            else if (o.r.br().y < this.r.y)
                return 1;
            return this.r.x - o.r.x;
        }
    }
    
    private static List<CharacterMatch> findCharacters (Mat m) {
        Imgproc.copyMakeBorder(m, m, 5, 5, 5, 5, Imgproc.BORDER_CONSTANT, new Scalar(255, 255, 255)); // characters on borders give problems
        Mat gray = new Mat();
        Mat thresh = new Mat();
        //Mat edges = new Mat();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        
        Imgproc.cvtColor(m, gray, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(gray, thresh, 200, 255, Imgproc.THRESH_BINARY); // should already be correct because of text though?
        //thresh = floodFill(m);
        //Imgproc.GaussianBlur(thresh, thresh, new Size(7, 7), 10.0, 10.0); // fixes box on bottom right?
        //Imgproc.Canny(thresh, edges, 66, 133);
        //Imgproc.erode(thresh, thresh, new Mat());
        //Imgproc.dilate(thresh, thresh, Imgproc.getStructuringElement(Imgproc.MORPH_CROSS, new Size(1, 2)));
        
        // TODO: somehow prevent dots from being remove fron i, j
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        List<CharacterMatch> matches = new ArrayList<>();
                
        for (int i = 0; i < hierarchy.width(); ++i) {
            if (hierarchy.get(0, i)[3] >= 0) {
                Mat mask = new Mat(m.rows(), m.cols(), CvType.CV_8UC1, new Scalar(0));
                Mat crop = new Mat(m.rows(), m.cols(), CvType.CV_8UC3, new Scalar(255, 255, 255));
                Imgproc.drawContours(mask, contours, i, new Scalar(255), Core.FILLED);
                //Core.rectangle(m, r.tl(), r.br(), new Scalar(0, 0, 255));
                
                m.copyTo(crop, mask);
                
                Rect r = Imgproc.boundingRect(contours.get(i));
                r.width -= 1;
                r.height -= 1;
                
                matches.add(new CharacterMatch(i, r, contours.get(i), crop));
                
                //int radius = (r.width+r.height)/4;
                
                //int closest = closestBlob (i, contours);
                //System.out.println(i + " " + closest + " " + r.x + "," + r.y + " " + Imgproc.contourArea(contours.get(i)) + " " + radius*radius*Math.PI);
                //Highgui.imwrite(".data/wpg/letters/" + i + ".jpg", crop.submat(r));

                /*MatOfPoint point = contours.get(i);
                MatOfInt hull = new MatOfInt();
                MatOfInt4 out = new MatOfInt4();
                Imgproc.convexHull(point, hull);
                if (hull.height() > 3) {
                    Imgproc.convexityDefects(point, hull, out);
                    for (int j = 0; j < out.height(); ++j) {
                        int data[] = new int[4];
                        out.get(j, 0, data);
                        if (data[3] > 10) {
                            //data[2];
                            Core.circle(m, new Point(point.get(data[2], 0)), 3, new Scalar(0, 0, 255));
                        }
                    }
                }*/
            }
        }
        
        Map<CharacterMatch, List<CharacterMatch>> neighbours = new HashMap<>();
        for (CharacterMatch match : matches) {
            neighbours.put(match, new ArrayList<CharacterMatch>());
            if (Imgproc.contourArea(match.contour) < 1000) { // TODO: calculate this
                double diameter = match.r.width*match.r.height*Math.PI/4;
                for (CharacterMatch neighbour : matches) {
                    if (Imgproc.contourArea(neighbour.contour) > 1000) {
                        if (match.r.x > neighbour.r.x && match.r.x < neighbour.r.br().x ||
                                match.r.br().x > neighbour.r.x && match.r.br().x < neighbour.r.br().x) { // needs to be in the same x space
                            if (match.r.y > neighbour.r.br().y || match.r.br().y < neighbour.r.y) { // no overlap?
                                double distance = blobDistance (match.contour, neighbour.contour);
                                if (distance < diameter) {
                                    neighbours.get(match).add(neighbour);
                                }
                            }
                        }
                    }
                }
            }
            if (neighbours.get(match).isEmpty())
                neighbours.remove(match);
        }
        // TODO: use distance if there are multiple matches
        for (CharacterMatch match : neighbours.keySet()) {
            matches.remove(match);
            CharacterMatch neighbour = neighbours.get(match).get(0);
            matches.remove(neighbour);
            
            Mat mask = new Mat(m.rows(), m.cols(), CvType.CV_8UC1, new Scalar(0));
            Mat crop = new Mat(m.rows(), m.cols(), CvType.CV_8UC3, new Scalar(255, 255, 255));
            Imgproc.drawContours(mask, contours, match.idx, new Scalar(255), Core.FILLED);
            Imgproc.drawContours(mask, contours, neighbour.idx, new Scalar(255), Core.FILLED);
            
            m.copyTo(crop, mask);
            
            Rectangle rect1 = new Rectangle(match.r.x, match.r.y, match.r.width, match.r.height);
            Rectangle rect2 = new Rectangle(neighbour.r.x, neighbour.r.y, neighbour.r.width, neighbour.r.height);
            Rectangle union = rect1.union(rect2);
            
            MatOfPoint mergedContour = new MatOfPoint();
            ArrayList<Point> mergedList = new ArrayList<>();
            mergedList.addAll(match.contour.toList());
            mergedList.addAll(neighbour.contour.toList());
            mergedContour.fromList(mergedList);
            matches.add(new CharacterMatch(match.idx, new Rect(union.x, union.y, union.width, union.height), mergedContour, crop));
        }
        
        /*for (CharacterMatch match : matches) {
            Highgui.imwrite(".data/wpg/letters/" + match.idx + ".jpg", match.crop.submat(match.r));
        }*/
        
        for (CharacterMatch match : matches) {
            //Highgui.imwrite(".data/wpg/letters/" + match.idx + ".jpg", match.crop.submat(match.r));
            //Core.rectangle(m, match.r.tl(), match.r.br(), new Scalar(0, 0, 255), 3);
            //Imgproc.drawContours(m, contours, match.idx, new Scalar(0, 0, 255), 3);
            match.crop = match.crop.submat(match.r);
        }
        //Highgui.imwrite(".data/got/got.jpg", m);
        
        return matches;
    }
}
