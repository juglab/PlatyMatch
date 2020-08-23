package PlatyMatch.nucleiDetection;

import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.convolution.kernel.Kernel1D;
import net.imglib2.algorithm.convolution.kernel.SeparableKernelConvolution;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.List;

/*
 * This is a Laplacian implementation optimized for speed using
 * {@link SeparableKernelConvolution}.
 *
 * @param <T> type
 * @author Matthias Arzt, Tim-Oliver Buccholz, Manan Lalit, MPI-CBG / CSBD, Dresden
 */
public class Laplacian<T extends RealType<T>> {


    private Img<T> image;

    private double sigma;

    private int axis;

    private double samplingFactor;

    private Img<FloatType> output;

    private OpService ops;

    public Laplacian(Img<T> image, double sigma, int axis, double samplingFactor, OpService ops){


        this.image=image;
        this.sigma=sigma;
        this.axis=axis;
        this.samplingFactor=samplingFactor;
        this.ops=ops;

        final List<RandomAccessibleInterval<FloatType>> secondDerivativeList = new ArrayList<>();

        for (int dim = 0; dim < image.numDimensions(); dim++) {
            secondDerivativeList.add(secondDerivative(image, sigma, dim));
        }

        output = ops.create().img(image, new FloatType());
        LoopBuilder.setImages(Views.collapse(Views.stack(secondDerivativeList)), output).forEachPixel((derivatives, r) -> {

            float sum = 0;
            for (int i = 0; i < image.numDimensions(); i++) {
                sum += derivatives.get(i).getRealFloat();
            }

            r.setReal(sum);
        });

    }

    public Img<FloatType> getOutput(){return output;}

    private RandomAccessibleInterval<FloatType> secondDerivative(final RandomAccessibleInterval<T> input, final double sigma, final int d) {


        /*Output here is the second derivative of the image w.r.t one axis*/
        final Img<FloatType> output = ArrayImgs.floats(Intervals.dimensionsAsLongArray(input));

        final Kernel1D second_derivative;

        if (d == axis) {
            second_derivative = DerivedNormalDistribution.derivedGaussKernel(sigma / samplingFactor, 2);
        } else {
            second_derivative = DerivedNormalDistribution.derivedGaussKernel(sigma, 2);
        }


        final Kernel1D[] kernels = new Kernel1D[image.numDimensions()];
        for (int i = 0; i < image.numDimensions(); i++) {
            if (i == axis) {
                kernels[i] = DerivedNormalDistribution.derivedGaussKernel(sigma / samplingFactor, 0);
            } else {
                kernels[i] = DerivedNormalDistribution.derivedGaussKernel(sigma, 0);
            }

        }
        kernels[d] = second_derivative;
        SeparableKernelConvolution.convolution(kernels).process(Views.extendBorder(input), output);
        return output;
    }

    public static void main(String[] args) {
        final ImageJ imageJ = new ImageJ();
        imageJ.ui().showUI();
    }

}
