package test;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

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

public class MatUtils {
    
    //static{ System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }
    
    // ------------------------------------ FILE IO --------------------------------------

    public static Mat fileToMat (Path path)
    {
        return fileToMat(path.toString());
    }
    
    public static Mat fileToMat (String path)
    {
        return Highgui.imread(path);
    }
    
    public static BufferedImage streamToImg (InputStream stream) throws IOException
    {
        if (stream == null)
            return null;
        BufferedImage img = ImageIO.read(stream);
        if (img == null)
            return null;
        
        // make sure DataBuffer contains bytes
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        copy.getGraphics().drawImage(img, 0, 0, null);
        
        return copy;
    }
    
    public static Mat streamToMat (InputStream stream) throws IOException 
    {
        BufferedImage img = streamToImg(stream);
        
        Mat m = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC3);
        m.put(0, 0, ((DataBufferByte)img.getRaster().getDataBuffer()).getData());
        return m;
    }
    
    public static BufferedImage matToImg (Mat m) throws IOException
    {
        MatOfByte matOfByte = new MatOfByte();
        Highgui.imencode(".jpg", m, matOfByte);
        byte[] byteArray = matOfByte.toArray();
        BufferedImage bufImage = null;
        
        InputStream in = new ByteArrayInputStream(byteArray);
        bufImage = ImageIO.read(in);
        
        return bufImage;
    }
    
    public static void matToFile (Mat m, Path path)
    {
        matToFile (m, path.toString());
    }
    
    public static void matToFile (Mat m, String path)
    {
        Highgui.imwrite(path, m);
    }
    
    public static Mat mergeInkColor (Mat ink, Mat color)
    {
        Mat result = new Mat();
        Imgproc.resize(color, result, ink.size());
        Core.bitwise_and(ink, result, result);
        return result;
    }
    
    // ----------------------------------- DISPLAY -----------------------------------
    public static void displayMat (Mat m) 
    {
        displayMat (m, 1000);
    }
    
    public static void displayMat (Mat m, int maxHeight) 
    {
        Mat small = new Mat();
        double scale = Math.max(1, 1.0*m.height()/maxHeight);
        if (scale > 1)
            Imgproc.resize(m, small, new Size(m.width()/scale, m.height()/scale));
        else
            small = m;
        
        MatOfByte matOfByte = new MatOfByte();
        Highgui.imencode(".jpg", small, matOfByte);
        byte[] byteArray = matOfByte.toArray();
        BufferedImage bufImage = null;
        try 
        {
            InputStream in = new ByteArrayInputStream(byteArray);
            bufImage = ImageIO.read(in);
            JFrame frame = new JFrame();
            frame.getContentPane().add(new JLabel(new ImageIcon(bufImage)));
            frame.pack();
            frame.setVisible(true);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
        }
    }
    
    // ----------------------------------- DRAW FUNCTIONS -------------------------------
    public static void drawContours (Mat m, List<MatOfPoint> contours)
    {
        drawContours(m, contours, 1000);
    }
    public static void drawContours (Mat m, List<MatOfPoint> contours, int maxHeight)
    {
        double scale = Math.max(1, 1.0*m.height()/maxHeight);
        Imgproc.drawContours(m, contours, -1, new Scalar(0, 0, 255), (int)(3*scale));
    }

    public static void drawRect (Mat m, Rect r)
    {
        drawRect(m, r, 1000);
    }
    public static void drawRect (Mat m, Rect r, int maxHeight)
    {
        double scale = Math.max(1, 1.0*m.height()/maxHeight);
        Core.rectangle(m, r.tl(), r.br(), new Scalar(0, 0, 255), (int)(3*scale));
    }
    
    public static void drawRects (Mat m, List<Rect> rects)
    {
        drawRects(m, rects, 1000);
    }
    public static void drawRects (Mat m, List<Rect> rects, int maxHeight)
    {
        double scale = Math.max(1, 1.0*m.height()/maxHeight);
        
        for (Rect r : rects)
        {
            Core.rectangle(m, r.tl(), r.br(), new Scalar(255, 0, 0), (int)(scale*3));
        }
    }
    
    public static void fillRects (Mat m, List<Rect> rects)
    {        
        Mat layer = m.clone();
        for (Rect rect : rects)
            Core.rectangle(layer, rect.tl(), rect.br(), new Scalar(100, 100, 100), Core.FILLED);
        
        Core.addWeighted(m, 0.5, layer, 0.5, 0, m); // needed to get alpha
        double scale = 1;
        for (int i = 0; i < rects.size(); ++i) {
            Rect rect = rects.get(i);
            Core.rectangle(m, rect.tl(), rect.br(), new Scalar(50, 50, 50), (int)(3*scale));
            Size size = Core.getTextSize(""+(i+1), Core.FONT_HERSHEY_SIMPLEX, (int)(4*scale), (int)(4*scale), null);
            Core.putText(
                    m, 
                    ""+(i+1), 
                    new Point(rect.x + rect.width/2 - size.width/2, rect.y + rect.height/2 + size.height/2), 
                    Core.FONT_HERSHEY_SIMPLEX, 
                    (int)(4*scale), 
                    new Scalar(0, 0, 255), 
                    (int)(4*scale));
        }
    }
    

    // ----------------------------------- ALGORITHM STUFF -------------------------------
    // http://rosettacode.org/wiki/Zhang-Suen_thinning_algorithm
    public static Mat zhangSuenThinning (Mat m)
    {
        Mat copy = new Mat();
        m.copyTo(copy);
        
        boolean[][] checks = new boolean[m.rows()][m.cols()];
        
        boolean changed;
        int count = 0;
        do
        {
            changed = false;
            
            for (int step = 0; step < 2; ++step)
            {
                for (boolean[] row : checks)
                    Arrays.fill(row, false);
                
                for (int row = 0; row < copy.rows(); ++row)
                {
                    for (int col = 0; col < copy.cols(); ++col)
                    {
                        if (copy.get(row, col)[0] == 0)
                        {
                            int[][] neighbours = zhangSuenNeighbours(copy, col, row);
                            
                            boolean stepChange = step == 0 ? zhangSuenStep1(neighbours) : zhangSuenStep2(neighbours);
                            if (stepChange)
                            {
                                changed = true;
                                checks[row][col] = true;
                            }
                        }
                    }
                }
                
                for (int row = 0; row < copy.rows(); ++row)
                {
                    for (int col = 0; col < copy.cols(); ++col)
                    {
                        if (checks[row][col])
                        {
                            copy.put(row, col, 255);
                        }
                    }
                }
            }
            System.out.println(changed);
            if (++count > 50)
            {
                System.out.println("hard stop");
                return copy;
            }
        }
        while (changed);
        
        return copy;
    }
    
    private static int[][] zhangSuenNeighbours (Mat m, int col, int row)
    {
        // int[row][col]
        int[][] neighbours = new int[3][3];
        
        for (int[] r : neighbours)
            Arrays.fill(r, -1);
        
        neighbours[1][1] = m.get(row, col)[0] == 0 ? 1 : 0; // black pixels are 1, whites 0
        
        if (col == 0)
        {
            neighbours[0][0] = 0;
            neighbours[1][0] = 0;
            neighbours[2][0] = 0;
        }
        if (row == 0)
        {
            neighbours[0][0] = 0;
            neighbours[0][1] = 0;
            neighbours[0][2] = 0;
        }
        if (col == m.cols()-1)
        {
            neighbours[0][2] = 0;
            neighbours[1][2] = 0;
            neighbours[2][2] = 0;
        }
        if (row == m.rows()-1)
        {
            neighbours[2][0] = 0;
            neighbours[2][1] = 0;
            neighbours[2][2] = 0;
        }
        
        for (int i = 0; i < 3; ++i)
        {
            for (int j = 0; j < 3; ++j)
            {
                if (neighbours[i][j] == -1)
                {
                    neighbours[i][j] = m.get(row+i-1, col+j-1)[0] == 0 ? 1 : 0; // black pixels are 1, whites 0
                }
            }
        }
            
        return neighbours;
    }
    
    private static int zhangSuenA (int[][] neighbours)
    {
        int a = 0;
        
        a += neighbours[0][1] ^ neighbours[0][2]; // P2 -> P3
        a += neighbours[0][2] ^ neighbours[1][2]; // P3 -> P4
        a += neighbours[1][2] ^ neighbours[2][2]; // P4 -> P5
        a += neighbours[2][2] ^ neighbours[2][1]; // P5 -> P6
        a += neighbours[2][1] ^ neighbours[2][0]; // P6 -> P7
        a += neighbours[2][0] ^ neighbours[1][0]; // P7 -> P8
        a += neighbours[1][0] ^ neighbours[0][0]; // P8 -> P9
        a += neighbours[0][0] ^ neighbours[0][1]; // P9 -> P2
        
        // counted double
        a /= 2;
        
        return a;
    }
    
    private static int zhangSuenB (int[][] neighbours)
    {
        int b = 0;
        for (int i = 0; i < 3; ++i)
            for (int j = 0; j < 3; ++j)
                if (i != 1 || j != 1)
                    b += neighbours[i][j];
        return b;
    }
    
    // assume condition 0 is true
    private static boolean zhangSuenStep1 (int[][] neighbours)
    {
        int a = zhangSuenA(neighbours);
        int b = zhangSuenB(neighbours);
        
        // 1
        if (b < 2 || b > 6)
            return false;
        
        // 2
        if (a != 1)
            return false;
        
        // 3
        if (neighbours[0][1] + neighbours[1][2] + neighbours[2][1] == 3)
            return false;
        
        // 4
        if (neighbours[1][2] + neighbours[2][1] + neighbours[1][0] == 3)
            return false;
        
        return true;
    }

    // assume condition 0 is true
    private static boolean zhangSuenStep2 (int[][] neighbours)
    {
        int a = zhangSuenA(neighbours);
        int b = zhangSuenB(neighbours);
        
        // 1
        if (b < 2 || b > 6)
            return false;
        
        // 2
        if (a != 1)
            return false;
        
        // 3
        if (neighbours[0][1] + neighbours[1][2] + neighbours[1][0] == 3)
            return false;
        
        // 4
        if (neighbours[0][1] + neighbours[2][1] + neighbours[1][0] == 3)
            return false;
        
        return true;
    }
}
