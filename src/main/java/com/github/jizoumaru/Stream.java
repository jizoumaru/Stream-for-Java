package com.github.jizoumaru;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Objects;

public abstract class Stream<T> implements Iterator<T>, AutoCloseable {
	public static class Holder<T> {
		public static <T> Holder<T> of(T value) {
			return new Holder<T>(value, true);
		}

		public static <T> Holder<T> none() {
			return new Holder<T>(null, false);
		}

		private final T value;
		private final boolean exist;

		private Holder(T value, boolean exist) {
			this.value = value;
			this.exist = exist;
		}

		public boolean exists() {
			return exist;
		}

		public T value() {
			if (exist) {
				return value;
			}
			throw new NoSuchElementException();
		}
	}

	public interface Predicate<T> {
		boolean test(T value);
	}

	public interface Function<T, U> {
		U apply(T value);
	}

	public interface Supplier<T> {
		T get();
	}

	public interface Consumer<T> {
		void accept(T value);
	}

	public interface BinaryOperator<T> {
		T apply(T left, T right);
	}

	public interface BiFunction<T, U, R> {
		R apply(T left, U right);
	}

	public interface UnaryOperator<T> {
		T apply(T value);
	}

	public static class Optional<T> {
		public static <T> Optional<T> of(T value) {
			return new Optional<T>(value);
		}

		public static <T> Optional<T> empty() {
			return new Optional<T>(null);
		}

		private final T value;

		public Optional(T value) {
			this.value = value;
		}

		public boolean isPresent() {
			return value != null;
		}

		public T get() {
			if (value != null) {
				return value;
			}
			throw new NoSuchElementException();
		}
	}

	private Holder<T> holder = null;
	private boolean closed = false;

	@Override
	public boolean hasNext() {
		if (holder == null) {
			holder = fetch();
		}
		return holder.exists();
	}

	public T peek() {
		if (hasNext()) {
			return holder.value();
		}
		throw new NoSuchElementException();
	}

	@Override
	public T next() {
		T value = peek();
		holder = null;
		return value;
	}

	@Override
	public void remove() {
		throw new RuntimeException("not suported");
	}

	protected abstract Holder<T> fetch();

	protected abstract void internalClose();

	@Override
	public void close() {
		if (!closed) {
			internalClose();
			closed = true;
		}
	}

	static class ConcatStream<T> extends Stream<T> {
		private final Stream<T> left;
		private final Stream<T> right;

		public ConcatStream(Stream<T> left, Stream<T> right) {
			this.left = left;
			this.right = right;
		}

		@Override
		protected Holder<T> fetch() {
			if (left.hasNext()) {
				return Holder.of(left.next());
			}

			if (right.hasNext()) {
				return Holder.of(right.next());
			}

			return Holder.none();
		}

		@Override
		protected void internalClose() {
			try {
				left.close();
			} finally {
				right.close();
			}
		}
	}

	static class EmptyStream<T> extends Stream<T> {
		@Override
		protected Holder<T> fetch() {
			return Holder.none();
		}

		@Override
		protected void internalClose() {
		}
	}

	static class GenerateStream<T> extends Stream<T> {
		public final Supplier<T> supplier;

		public GenerateStream(Supplier<T> supplier) {
			this.supplier = supplier;
		}

		@Override
		protected Holder<T> fetch() {
			return Holder.of(supplier.get());
		}

		@Override
		protected void internalClose() {
		}
	}

	static class IterateFinateStream<T> extends Stream<T> {
		private final T seed;
		private final Predicate<? super T> hasNext;
		private final UnaryOperator<T> next;
		private Holder<T> holder;

		public IterateFinateStream(T seed, Predicate<? super T> hasNext, UnaryOperator<T> next) {
			this.seed = seed;
			this.hasNext = hasNext;
			this.next = next;
			this.holder = null;
		}

		@Override
		protected Holder<T> fetch() {
			T value = holder == null
					? seed
					: next.apply(holder.value());

			holder = hasNext.test(value)
					? Holder.of(value)
					: Holder.none();

			return holder;
		}

		@Override
		protected void internalClose() {
		}
	}

	static class IterateInfiniteStream<T> extends Stream<T> {
		private final T seed;
		private final UnaryOperator<T> next;
		private Holder<T> holder;

		public IterateInfiniteStream(T seed, UnaryOperator<T> next) {
			this.seed = seed;
			this.next = next;
			this.holder = null;
		}

		@Override
		protected Holder<T> fetch() {
			T value = holder == null
					? seed
					: next.apply(holder.value());

			holder = Holder.of(value);
			return holder;
		}

		@Override
		protected void internalClose() {
		}
	}

	static class ArrayStream<T> extends Stream<T> {
		private final T[] array;
		private int index;

		public ArrayStream(T[] array) {
			this.array = array;
		}

		@Override
		protected Holder<T> fetch() {
			return index < array.length
					? Holder.of(array[index++])
					: Holder.none();
		}

		@Override
		protected void internalClose() {
		}
	}

	static class IterableStream<T> extends Stream<T> {
		private final Iterable<T> iterable;
		private Iterator<T> iterator;

		public IterableStream(Iterable<T> iterable) {
			this.iterable = iterable;
		}

		@Override
		protected Holder<T> fetch() {
			if (iterator == null) {
				iterator = iterable.iterator();
			}
			return iterator.hasNext()
					? Holder.of(iterator.next())
					: Holder.none();
		}

		@Override
		protected void internalClose() {
		}
	}

	static class IteratorStream<T> extends Stream<T> {
		private final Iterator<T> iterator;

		public IteratorStream(Iterator<T> iterator) {
			this.iterator = iterator;
		}

		@Override
		protected Holder<T> fetch() {
			return iterator.hasNext()
					? Holder.of(iterator.next())
					: Holder.none();
		}

		@Override
		protected void internalClose() {
		}
	}

	static class FilterStream<T> extends Stream<T> {
		private final Stream<T> stream;
		private final Predicate<T> predicate;

		public FilterStream(Stream<T> stream, Predicate<T> predicate) {
			this.stream = stream;
			this.predicate = predicate;
		}

		@Override
		protected Holder<T> fetch() {
			while (stream.hasNext()) {
				T value = stream.next();
				if (predicate.test(value)) {
					return Holder.of(value);
				}
			}
			return Holder.none();
		}

		@Override
		protected void internalClose() {
			stream.close();
		}
	}

	static class MapStream<T, U> extends Stream<U> {
		private final Stream<T> stream;
		private final Function<T, U> mapper;

		public MapStream(Stream<T> stream, Function<T, U> mapper) {
			this.stream = stream;
			this.mapper = mapper;
		}

		@Override
		protected Holder<U> fetch() {
			return stream.hasNext()
					? Holder.of(mapper.apply(stream.next()))
					: Holder.none();
		}

		@Override
		protected void internalClose() {
			stream.close();
		}
	}

	static class FlatMapStream<T, U> extends Stream<U> {
		private final Stream<T> stream;
		private final Function<T, Stream<U>> mapper;
		private Stream<U> inner;

		public FlatMapStream(Stream<T> stream, Function<T, Stream<U>> mapper) {
			this.stream = stream;
			this.mapper = mapper;
			this.inner = new EmptyStream<U>();
		}

		@Override
		protected Holder<U> fetch() {
			while (!inner.hasNext() && stream.hasNext()) {
				inner = mapper.apply(stream.next());
			}
			return inner.hasNext()
					? Holder.of(inner.next())
					: Holder.none();
		}

		@Override
		protected void internalClose() {
			try {
				inner.close();
			} finally {
				stream.close();
			}
		}

	}

	static class DistinctStream<T> extends Stream<T> {
		private final Stream<T> stream;
		private Iterator<T> iterator;

		public DistinctStream(Stream<T> stream) {
			this.stream = stream;
		}

		@Override
		protected Holder<T> fetch() {
			if (iterator == null) {
				LinkedHashSet<T> set = new LinkedHashSet<>();
				while (stream.hasNext()) {
					set.add(stream.next());
				}
				iterator = set.iterator();
			}

			return iterator.hasNext()
					? Holder.of(iterator.next())
					: Holder.none();
		}

		@Override
		protected void internalClose() {
			stream.close();
		}
	}

	static class SortedStream<T> extends Stream<T> {
		private final Stream<T> stream;
		private final Comparator<? super T> comparator;
		private Iterator<T> iterator;

		public SortedStream(Stream<T> stream, Comparator<? super T> comparator) {
			this.stream = stream;
			this.comparator = comparator;
		}

		@Override
		protected Holder<T> fetch() {
			if (iterator == null) {
				ArrayList<T> list = new ArrayList<T>();
				while (stream.hasNext()) {
					list.add(stream.next());
				}
				Collections.sort(list, comparator);
				iterator = list.iterator();
			}

			return iterator.hasNext()
					? Holder.of(iterator.next())
					: Holder.none();
		}

		@Override
		protected void internalClose() {
			stream.close();
		}

	}

	static class PeekStream<T> extends Stream<T> {
		private final Stream<T> stream;
		private final Consumer<? super T> action;

		public PeekStream(Stream<T> stream, Consumer<? super T> action) {
			this.stream = stream;
			this.action = action;
		}

		@Override
		protected Holder<T> fetch() {
			if (stream.hasNext()) {
				T value = stream.next();
				action.accept(value);
				return Holder.of(value);
			}
			return Holder.none();
		}

		@Override
		protected void internalClose() {
			stream.close();
		}
	}

	static class LimitStream<T> extends Stream<T> {
		private final Stream<T> stream;
		private final long count;
		private long index;

		public LimitStream(Stream<T> stream, long count) {
			this.stream = stream;
			this.count = count;
			this.index = 0;
		}

		@Override
		protected Holder<T> fetch() {
			if (index < count && stream.hasNext()) {
				index++;
				return Holder.of(stream.next());
			}
			return Holder.none();
		}

		@Override
		protected void internalClose() {
			stream.close();
		}

	}

	static class SkipStream<T> extends Stream<T> {
		private final Stream<T> stream;
		private final long count;
		private long index;

		public SkipStream(Stream<T> stream, long count) {
			this.stream = stream;
			this.count = count;
			this.index = 0;
		}

		@Override
		protected Holder<T> fetch() {
			while (index < count && stream.hasNext()) {
				index++;
				stream.next();
			}

			return stream.hasNext()
					? Holder.of(stream.next())
					: Holder.none();
		}

		@Override
		protected void internalClose() {
			stream.close();
		}

	}

	static class TakeWhileStream<T> extends Stream<T> {
		private final Stream<T> stream;
		private final Predicate<? super T> predicate;

		public TakeWhileStream(Stream<T> stream, Predicate<? super T> predicate) {
			this.stream = stream;
			this.predicate = predicate;
		}

		@Override
		protected Holder<T> fetch() {
			if (stream.hasNext()) {
				T value = stream.next();
				if (predicate.test(value)) {
					return Holder.of(value);
				}
			}
			return Holder.none();
		}

		@Override
		protected void internalClose() {
			stream.close();
		}
	}

	static class PartitionCountStream<T> extends Stream<Stream<T>> {
		private final Stream<T> stream;
		private final int count;
		private int index;

		public PartitionCountStream(Stream<T> stream, int count) {
			this.stream = stream;
			this.count = count;
			this.index = count;
		}

		@SuppressWarnings("resource")
		@Override
		protected Holder<Stream<T>> fetch() {
			while (index < count && stream.hasNext()) {
				stream.next();
				index++;
			}

			if (stream.hasNext()) {
				index = 0;
				return Holder.of(new PartitionCountInternalStream<T>(this));
			}

			return Holder.<Stream<T>>none();
		}

		Holder<T> internalFetch() {
			if (index < count && stream.hasNext()) {
				index++;
				return Holder.of(stream.next());
			}
			return Holder.none();
		}

		@Override
		protected void internalClose() {
			stream.close();
		}
	}

	static class PartitionCountInternalStream<T> extends Stream<T> {
		private final PartitionCountStream<T> outer;

		public PartitionCountInternalStream(PartitionCountStream<T> outer) {
			this.outer = outer;
		}

		@Override
		protected Holder<T> fetch() {
			return outer.internalFetch();
		}

		@Override
		protected void internalClose() {
		}
	}

	static class PartitionByStream<T, K> extends Stream<Stream<T>> {
		private final Stream<T> stream;
		private final Function<T, K> keyFactory;
		private Holder<K> keyHolder;

		public PartitionByStream(Stream<T> stream, Function<T, K> keyFactory) {
			this.stream = stream;
			this.keyFactory = keyFactory;
			this.keyHolder = Holder.none();
		}

		@SuppressWarnings("resource")
		@Override
		protected Holder<Stream<T>> fetch() {
			if (keyHolder.exists()) {
				while (stream.hasNext()
						&& Objects.equals(keyHolder.value(), keyFactory.apply(stream.peek()))) {
					stream.next();
				}
				keyHolder = Holder.none();
			}

			if (stream.hasNext()) {
				keyHolder = Holder.of(keyFactory.apply(stream.peek()));
				return Holder.of(new PartitionByInternalStream<T, K>(this));
			}

			return Holder.<Stream<T>>none();
		}
		
		Holder<T> internalFetch() {
			if (stream.hasNext()
					&& Objects.equals(keyHolder.value(), keyFactory.apply(stream.peek()))) {
				return Holder.of(stream.next());
			}
			return Holder.none();
		}

		@Override
		protected void internalClose() {
			stream.close();
		}
	}

	static class PartitionByInternalStream<T, K> extends Stream<T> {
		private final PartitionByStream<T, K> outer;

		public PartitionByInternalStream(PartitionByStream<T, K> outer) {
			this.outer = outer;
		}

		@Override
		protected Holder<T> fetch() {
			return outer.internalFetch();
		}

		@Override
		protected void internalClose() {
		}
	}

	public static <T> Stream<T> concat(final Stream<T> a, final Stream<T> b) {
		return new ConcatStream<T>(a, b);
	}

	public static <T> Stream<T> empty() {
		return new EmptyStream<T>();
	}

	public static <T> Stream<T> generate(final Supplier<T> supplier) {
		return new GenerateStream<T>(supplier);
	}

	public static <T> Stream<T> iterate(final T seed, final Predicate<? super T> hasNext, final UnaryOperator<T> next) {
		return new IterateFinateStream<T>(seed, hasNext, next);
	}

	public static <T> Stream<T> iterate(final T seed, final UnaryOperator<T> next) {
		return new IterateInfiniteStream<T>(seed, next);
	}

	@SafeVarargs
	public static <T> Stream<T> of(final T... values) {
		return new ArrayStream<T>(values);
	}

	public static <T> Stream<T> of(final Iterable<T> iterable) {
		return new IterableStream<T>(iterable);
	}

	public static <T> Stream<T> of(final Iterator<T> iterator) {
		return new IteratorStream<T>(iterator);
	}

	public Stream<T> filter(final Predicate<T> predicate) {
		return new FilterStream<T>(this, predicate);
	}

	public <R> Stream<R> map(final Function<T, R> mapper) {
		return new MapStream<T, R>(this, mapper);
	}

	public <R> Stream<R> flatMap(final Function<T, Stream<R>> mapper) {
		return new FlatMapStream<T, R>(this, mapper);
	}

	public Stream<T> distinct() {
		return new DistinctStream<T>(this);
	}

	public <K extends Comparable<K>> Stream<T> sorted(final Function<T, K> keySelector) {
		return sorted(new Comparator<T>() {
			@Override
			public int compare(T x, T y) {
				return keySelector.apply(x).compareTo(keySelector.apply(y));
			}
		});
	}

	public Stream<T> sorted(final Comparator<? super T> comparator) {
		return new SortedStream<T>(this, comparator);
	}

	public Stream<T> peek(final Consumer<? super T> action) {
		return new PeekStream<T>(this, action);
	}

	public Stream<T> limit(final long count) {
		return new LimitStream<T>(this, count);
	}

	public Stream<T> skip(final long count) {
		return new SkipStream<T>(this, count);
	}

	public Stream<T> takeWhile(final Predicate<? super T> predicate) {
		return new TakeWhileStream<T>(this, predicate);
	}

	public <K> Stream<Stream<T>> partitionBy(Function<T, K> keyFactory) {
		return new PartitionByStream<T, K>(this, keyFactory);
	}

	public <K> Stream<Stream<T>> partition(int count) {
		return new PartitionCountStream<T>(this, count);
	}

	@SuppressWarnings("resource")
	public <K> Stream<ArrayList<T>> group(int count) {
		return new PartitionCountStream<T>(this, count).map(x -> x.toList());
	}

	@SuppressWarnings("resource")
	public <K> Stream<ArrayList<T>> groupBy(Function<T, K> keyFactory) {
		return new PartitionByStream<T, K>(this, keyFactory).map(x -> x.toList());
	}

	public void forEach(final Consumer<? super T> action) {
		try (Stream<T> stream = this) {
			while (stream.hasNext()) {
				action.accept(stream.next());
			}
		}
	}

	public Object[] toArray() {
		try (Stream<T> stream = this) {
			ArrayList<T> list = new ArrayList<T>();
			while (stream.hasNext()) {
				list.add(stream.next());
			}
			return list.toArray();
		}
	}

	public <A> A[] toArray(Function<Integer, A[]> generator) {
		try (Stream<T> stream = this) {
			ArrayList<T> list = new ArrayList<T>();
			while (stream.hasNext()) {
				list.add(stream.next());
			}
			return list.toArray(generator.apply(list.size()));
		}
	}

	public T reduce(T seed, BinaryOperator<T> accumulator) {
		try (Stream<T> stream = this) {
			T result = seed;
			while (stream.hasNext()) {
				result = accumulator.apply(result, stream.next());
			}
			return result;
		}
	}

	public Optional<T> reduce(BinaryOperator<T> accumulator) {
		try (Stream<T> stream = this) {
			T value = null;

			while (stream.hasNext()) {
				if (value == null) {
					value = stream.next();
				} else {
					value = accumulator.apply(value, stream.next());
				}
			}

			return Optional.of(value);
		}
	}

	public ArrayList<T> toList() {
		try (Stream<T> stream = this) {
			ArrayList<T> list = new ArrayList<T>();
			while (stream.hasNext()) {
				list.add(stream.next());
			}
			return list;
		}
	}

	public LinkedHashSet<T> toSet() {
		try (Stream<T> stream = this) {
			LinkedHashSet<T> set = new LinkedHashSet<T>();
			while (stream.hasNext()) {
				set.add(stream.next());
			}
			return set;
		}
	}

	public <K, V> LinkedHashMap<K, V> toMap(Function<T, K> keySelector, Function<T, V> valueSelector) {
		try (Stream<T> stream = this) {
			LinkedHashMap<K, V> map = new LinkedHashMap<K, V>();
			while (stream.hasNext()) {
				T item = stream.next();
				map.put(keySelector.apply(item), valueSelector.apply(item));
			}
			return map;
		}
	}

	public Optional<T> min(Comparator<? super T> comparator) {
		try (Stream<T> stream = this) {
			T result = null;
			while (stream.hasNext()) {
				T value = stream.next();
				if (result == null || comparator.compare(value, result) < 0) {
					result = value;
				}
			}
			return Optional.of(result);
		}
	}

	public Optional<T> max(Comparator<? super T> comparator) {
		try (Stream<T> stream = this) {
			T result = null;
			while (stream.hasNext()) {
				T value = stream.next();
				if (result == null || comparator.compare(result, value) < 0) {
					result = value;
				}
			}
			return Optional.of(result);
		}
	}

	public long count() {
		try (Stream<T> stream = this) {
			long count = 0L;
			while (stream.hasNext()) {
				stream.next();
				count++;
			}
			return count;
		}
	}

	public boolean anyMatch(Predicate<? super T> predicate) {
		try (Stream<T> stream = this) {
			while (stream.hasNext()) {
				if (predicate.test(stream.next())) {
					return true;
				}
			}
			return false;
		}
	}

	public boolean allMatch(Predicate<? super T> predicate) {
		try (Stream<T> stream = this) {
			while (stream.hasNext()) {
				if (!predicate.test(stream.next())) {
					return false;
				}
			}
			return true;
		}
	}

	public boolean noneMatch(Predicate<? super T> predicate) {
		try (Stream<T> stream = this) {
			while (stream.hasNext()) {
				if (predicate.test(stream.next())) {
					return false;
				}
			}
			return true;
		}
	}

	public Optional<T> findFirst() {
		try (Stream<T> stream = this) {
			if (stream.hasNext()) {
				return Optional.of(stream.next());
			}
			return Optional.empty();
		}
	}

	public Optional<T> findAny() {
		try (Stream<T> stream = this) {
			if (stream.hasNext()) {
				return Optional.of(stream.next());
			}
			return Optional.empty();
		}
	}
}
