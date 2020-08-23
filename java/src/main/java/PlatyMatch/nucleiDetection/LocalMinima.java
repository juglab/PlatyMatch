package PlatyMatch.nucleiDetection;

import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.List;

public class LocalMinima<T extends RealType<T> & NativeType<T> & Comparable<T>> {

    private Img<T> image;
    private List<Point> output;


    public LocalMinima(Img<T> image) {
        this.image = image;
        final T value = image.firstElement().createVariable();
        output = extractLocalMinima(image, value);

    }

    public List<Point> getOutput(){
        return output;}


    private List<Point> extractLocalMinima(final RandomAccessibleInterval<T> image, final T highValue) {
        return extractLocalMinima(Views.extendValue(image, highValue), image);
    }


    private List<Point> extractLocalMinima(final RandomAccessible<T> image, final Interval interval) {
        final Shape shape = new RectangleShape(1, true);
        final RandomAccessible<Neighborhood<T>> neighborhoods = shape.neighborhoodsRandomAccessibleSafe(image);
        final List<Point> points = new ArrayList<>();

        LoopBuilder.setImages(Views.interval(image, interval), Views.interval(neighborhoods, interval)).forEachPixel(
                (centerValue, neighborhood) -> {
                    if (isCenterMinimal(centerValue, neighborhood)) {
                        final Point minimumLocation = new Point(neighborhood);
                        points.add(minimumLocation);
                    }
                }
        );
        return points;
    }


    private boolean isCenterMinimal(final T center, final Neighborhood<T> neighborhood) {
        boolean isMinimum = true;
        for (final T value : neighborhood) {
            if (center.compareTo(value) >= 0) {
                isMinimum = false;
                break;
            }
        }
        return isMinimum;
    }
}


