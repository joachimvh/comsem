package test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opencv.core.Mat;
import org.opencv.core.Point3;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

public class SceneExtractor {

    public static void extract () {
        //Mat m = MatUtils.fileToMat(".data/wpg/SW32-DRR-009-012-lay-03.color.panel.tif");
        
        PanelExtractor pe = new PanelExtractor();
        //Mat m = MatUtils.mergeInkColor(
        //        MatUtils.fileToMat(".data/wpg/strips_data/SUSKE EN WISKE_324/324SW_BW map/links/SW32-DRR-045-048-page-12C.tif"), 
        //        MatUtils.fileToMat(".data/wpg/strips_data/SUSKE EN WISKE_324/324SW_BW map/links/SW32-DRR-045-048-lay-12.cmy.tif"));
        Mat m = MatUtils.fileToMat(".data/testpanels/amorasC.jpg");
        pe.generateData(m);
        for (Rect r : pe.rects)
        {
            Mat sub = m.submat(r);
            List<Scalar> colors = getPopularColors(sub);
            System.out.println(colors);

            /*for (int x = 0; x < sub.cols(); ++x)
            {
                for (int y = 0; y < sub.rows(); ++y)
                {
                    double[] vals = sub.get(y, x);
                    if (!colors.contains(new Scalar(vals[0], vals[1], vals[2])))
                    {
                        Arrays.fill(vals, 0);
                    }
                    sub.put(y, x, vals);
                }
            }*/
        }
        MatUtils.displayMat(m);
    }
    
    public static List<Scalar> getPopularColors (Mat m)
    {
        int mod = 255;
        int[][][] colors = new int[256/mod][256/mod][256/mod];
        
        for (int x = 0; x < m.cols(); ++x)
        {
            for (int y = 0; y < m.rows(); ++y)
            {
                double[] vals = m.get(y, x);
                if (!(vals[0] == 255 && vals[1] == 255 && vals[2] == 255) && ! (vals[0] == 0 && vals[1] == 0 && vals[2] == 0))
                {
                    for (int i = 0; i < 3; ++i)
                    {
                        vals[i] = Math.round(vals[i]/mod)*mod;
                    }
                    m.put(y, x, vals);
                    //colors[(int)vals[0]/mod][(int)vals[1]/mod][(int)vals[2]/mod]++;
                }
            }
        }
        
        List<String> colorStrs = new ArrayList<>(); 
        for (int i = 0; i < colors.length; ++i)
        {
            for (int j = 0; j < colors.length; ++j)
            {
                for (int k = 0; k < colors.length; ++k)
                {
                    colorStrs.add(String.format("(%d,%d,%d):%d", i*64, j*64, k*64, colors[i][j][k]));
                }
                
            }
        }
        
        Collections.sort(colorStrs, new Comparator<String>() {
            public int compare(String o1, String o2) {
                int i1 = Integer.parseInt(o1.split(":")[1]);
                int i2 = Integer.parseInt(o2.split(":")[1]);
                return -(i1-i2);
            }
        });
        
        List<Scalar> topColors = new ArrayList<>();
        
        /*for (int i = 0; i < 3; ++i)
        {
            String[] split = colorStrs.get(i).split(",");
            topColors.add(new Scalar(
                    Integer.parseInt(split[0].substring(1)),
                    Integer.parseInt(split[1]),
                    Integer.parseInt(split[2].split("\\)")[0])));
        }*/
        return topColors;
    }
}
