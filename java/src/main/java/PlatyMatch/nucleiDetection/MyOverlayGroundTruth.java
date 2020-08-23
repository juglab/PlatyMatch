package PlatyMatch.nucleiDetection;

import bdv.util.BdvOverlay;
import net.imglib2.Point;
import net.imglib2.realtransform.AffineTransform3D;

import java.awt.*;
import java.util.List;

public class MyOverlayGroundTruth extends BdvOverlay {
    private List<Point> thresholdedLocalMinima;
    private double samplingFactor;

    public MyOverlayGroundTruth(List<Point> thresholdedLocalMinima, double samplingFactor) {
        this.thresholdedLocalMinima = thresholdedLocalMinima;
        this.samplingFactor=samplingFactor;
    }


    @Override
    protected synchronized void draw(final Graphics2D g) {
        if (thresholdedLocalMinima == null)
            return;

        final AffineTransform3D t = new AffineTransform3D();
        getCurrentTransform3D(t);
        double calibrationFactor = extractScale(t, 0);
        final double[] lPos = new double[3];
        final double[] vPos = new double[3];
        for (int i = 0; i < thresholdedLocalMinima.size(); i++) {
            Point p = thresholdedLocalMinima.get(i);
            float radius = 9;
            radius = (float) calibrationFactor * radius;
            p.localize(lPos);
            t.apply(lPos, vPos);

            double dis = vPos[2];
            final int size;
            if (Math.abs(dis) <= radius) {
                size = 2 * (int) Math.ceil(Math.sqrt(Math.pow(radius, 2) - Math.pow(samplingFactor * dis, 2)));

                final int x = (int) (vPos[0] - 0.5 * size);
                final int y = (int) (vPos[1] - 0.5 * size);
                g.setColor(new Color(255, 0, 0));
                g.setStroke( new BasicStroke( 3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 2, 2 }, 0 ) );
                g.drawOval(x, y, size, size);
                g.setFont(new Font("Serif", Font.BOLD, 20));
                String s = String.valueOf(i);
                FontMetrics fm = g.getFontMetrics();
                g.setColor(new Color(255, 255, 0));
                g.drawString(s, (int) vPos[0]+radius,  (int) vPos[1]+radius);

            } else {
                size = 0;
            }
        }
    }

    public static double extractScale(final AffineTransform3D transform, final int axis) {
        double sqSum = 0;
        final int c = axis;
        for (int r = 0; r < 3; ++r) {
            final double x = transform.get(r, c);
            sqSum += x * x;
        }
        return Math.sqrt(sqSum);
    }
}
