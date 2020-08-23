package PlatyMatch.nucleiDetection;

import net.imglib2.*;
import net.imglib2.type.numeric.RealType;

import java.util.Iterator;
import java.util.List;

/**
 * This wraps an ArrayList as an IterableInterval.
 *
 * @param <T> type
 * @author Tim-Oliver Buccholz MPI-CBG / CSBD, Dresden
 */

public class SampleList<T extends RealType<T>> implements IterableInterval<T> {

	private List<T> list;

	private T firstElement = null;

	private float min = Float.MAX_VALUE;

	private long min_pos = -1;

	private float max = Float.MIN_VALUE;

	private long max_pos = -1;

	public SampleList(final List<T> list) {
		this.list = list;
		if (list.size() > 0) {
			this.firstElement = list.get(0).createVariable();
		}
		long i = 0;
		for (T f : list) {
			if (f.getRealFloat() < min) {
				min = f.getRealFloat();
				min_pos = i;
			}
			if (f.getRealFloat() > max) {
				max = f.getRealFloat();
				max_pos = i;
			}
			i++;
		}
	}

	@Override
	public long size() {
		return list.size();
	}

	@Override
	public T firstElement() {
		return firstElement;
	}

	@Override
	public Object iterationOrder() {
		return null;
	}

	@Override
	public double realMin(int d) {
		return min;
	}

	@Override
	public void realMin(double[] min) {
		min[0] = this.min;
	}

	@Override
	public void realMin(RealPositionable min) {
		min.setPosition(new long[] {min_pos});
	}

	@Override
	public double realMax(int d) {
		return max;
	}

	@Override
	public void realMax(double[] max) {
		max[0] = this.max;
	}

	@Override
	public void realMax(RealPositionable max) {
		max.setPosition(new long[] {max_pos});
	}

	@Override
	public int numDimensions() {
		return 1;
	}

	@Override
	public Iterator<T> iterator() {
		return list.iterator();
	}

	@Override
	public long min(int d) {
		return min_pos;
	}

	@Override
	public void min(long[] min) {
		min[0] = min_pos;
	}

	@Override
	public void min(Positionable min) {
		min.setPosition(new long[] {min_pos});
	}

	@Override
	public long max(int d) {
		return max_pos;
	}

	@Override
	public void max(long[] max) {
		max[0] = max_pos;
	}

	@Override
	public void max(Positionable max) {
		max.setPosition(new long[] {max_pos});
	}

	@Override
	public void dimensions(long[] dimensions) {
		dimensions[0] = list.size();
	}


	@Override
	public long dimension(int d) {
		return list.size();
	}

	@Override
	public Cursor<T> cursor() {
		return new ListCursor();
	}

	@Override
	public Cursor<T> localizingCursor() {
		return new ListCursor();
	}

	private class ListCursor implements Cursor<T> {

		public ListCursor() {
		}

		int pos = -1;

		@Override
		public void localize(float[] position) {
			position[0] = pos;
		}

		@Override
		public void localize(double[] position) {
			position[0] = pos;
		}

		@Override
		public float getFloatPosition(int d) {
			return pos;
		}

		@Override
		public double getDoublePosition(int d) {
			return pos;
		}

		@Override
		public int numDimensions() {
			return 1;
		}

		@Override
		public T get() {
			return list.get(pos);
		}

		@Override
		public Sampler<T> copy() {
			return this.copy();
		}

		@Override
		public void jumpFwd(long steps) {
			pos += steps;
		}

		@Override
		public void fwd() {
			pos++;
		}

		@Override
		public void reset() {
			pos = -1;
		}

		@Override
		public boolean hasNext() {
			return pos < list.size() - 1;
		}

		@Override
		public T next() {
			pos++;
			return list.get(pos);
		}

		@Override
		public void localize(int[] position) {
			position[0] = pos;
		}

		@Override
		public void localize(long[] position) {
			position[0] = pos;
		}

		@Override
		public int getIntPosition(int d) {
			return pos;
		}

		@Override
		public long getLongPosition(int d) {
			return pos;
		}

		@Override
		public Cursor<T> copyCursor() {
			return this.copyCursor();
		}
	};
}
