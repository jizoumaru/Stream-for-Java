package com.github.jizoumaru;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public interface Stream2<T> extends Iterator<T>, AutoCloseable {
	public static <T> Stream2<T> from(Iterable<T> iterable) {
		return from(iterable.iterator());
	}

	public static <T> Stream2<T> from(Iterator<T> iterator) {
		return new AbstractStream<>() {
			@Override
			protected void computeNext() {
				if (iterator.hasNext()) {
					setNext(iterator.next());
				}
			}
		};
	}

	interface ThrowsSupplier<T> {
		T get() throws Exception;
	}

	public static <T> Stream2<T> from(ThrowsSupplier<Stream<T>> supplier) {
		return new AbstractStream<>() {
			Stream<T> stream;
			Spliterator<T> spliterator;

			@Override
			protected void computeNext() {
				if (spliterator == null) {
					try {
						stream = supplier.get();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					spliterator = stream.spliterator();
				}
				spliterator.tryAdvance(this::setNext);
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	public static <T> Stream2<T> ofNullable(T element) {
		if (element == null) {
			return empty();
		} else {
			return of(element);
		}
	}

	public static <T> Stream2<T> empty() {
		return new AbstractStream<>();
	}

	public static <T> Stream2<T> generate(Supplier<T> supplier) {
		return new AbstractStream<>() {
			@Override
			protected void computeNext() {
				setNext(supplier.get());
			}
		};
	}

	public static <T> Stream2<T> iterate(T seed, UnaryOperator<T> next) {
		return new AbstractStream<>() {
			T element = seed;

			@Override
			protected void computeNext() {
				setNext(element);
				element = next.apply(element);
			}
		};
	}

	public static <T> Stream2<T> iterate(T seed, Predicate<T> predicate, UnaryOperator<T> next) {
		return new AbstractStream<>() {
			T element = seed;

			@Override
			protected void computeNext() {
				if (!predicate.test(element)) {
					return;
				}
				setNext(element);
				element = next.apply(element);
			}
		};
	}

	public static <T> Stream2<T> concat(Stream2<T> left, Stream2<T> right) {
		return new AbstractStream<>() {
			Stream2<T> stream = left;

			@Override
			protected void computeNext() {
				while (!stream.hasNext()) {
					if (stream == right) {
						return;
					}
					stream = right;
				}
				setNext(stream.next());
			}

			@Override
			protected void onClose() {
				try (var _left = left;
						var _right = right) {
				}
			}
		};
	}

	@SafeVarargs
	public static <T> Stream2<T> of(T... elements) {
		return new AbstractStream<>() {
			int index = 0;

			@Override
			protected void computeNext() {
				if (index < elements.length) {
					setNext(elements[index++]);
				}
			}
		};
	}

	public static Stream2<Integer> range(int start, int end) {
		return new AbstractStream<>() {
			int element = start;

			@Override
			protected void computeNext() {
				if (element < end) {
					setNext(element++);
				}
			}
		};
	}

	void close();

	public static class AbstractStream<T> implements Stream2<T> {
		private enum State {
			NOT_READY, READY, CLOSED
		}

		private T element = null;
		private State state = State.NOT_READY;

		@Override
		public final boolean hasNext() {
			if (state == State.NOT_READY) {
				computeNext();

				if (state == State.NOT_READY) {
					close();
				}
			}
			return state == State.READY;
		}

		protected final void setNext(T element) {
			this.element = element;
			this.state = State.READY;
		}

		protected void computeNext() {
		}

		@Override
		public final T next() {
			if (hasNext()) {
				var element = this.element;
				this.element = null;
				this.state = State.NOT_READY;
				return element;
			}
			throw new NoSuchElementException();
		}

		protected void onClose() {
		}

		@Override
		public final void close() {
			if (state != State.CLOSED) {
				element = null;
				state = State.CLOSED;
				onClose();
			}
		}
	}

	default Stream2<T> filter(Predicate<T> predicate) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;

			@Override
			protected void computeNext() {
				while (stream.hasNext()) {
					var element = stream.next();

					if (predicate.test(element)) {
						setNext(element);
						return;
					}
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default <U> Stream2<U> map(Function<T, U> function) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;

			@Override
			protected void computeNext() {
				if (stream.hasNext()) {
					var element = stream.next();
					setNext(function.apply(element));
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default <U> Stream2<U> flatMap(Function<T, Stream2<U>> function) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;
			@SuppressWarnings("resource")
			Stream2<U> inner = new AbstractStream<>();

			@Override
			protected void computeNext() {
				while (!inner.hasNext()) {
					if (!stream.hasNext()) {
						return;
					}
					inner = function.apply(stream.next());
				}
				setNext(inner.next());
			}

			@Override
			protected void onClose() {
				try (var _stream = stream;
						var _inner = inner) {
				}
			}
		};
	}

	default Stream2<T> limit(long n) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;
			int index = 0;

			@Override
			protected void computeNext() {
				if (index < n && stream.hasNext()) {
					setNext(stream.next());
					index++;
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default Stream2<T> skip(long n) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;
			int index = 0;

			@Override
			protected void computeNext() {
				while (index < n && stream.hasNext()) {
					stream.next();
					index++;
				}
				if (stream.hasNext()) {
					setNext(stream.next());
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default Stream2<T> takeWhile(Predicate<? super T> predicate) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;

			@Override
			protected void computeNext() {
				if (stream.hasNext()) {
					var element = stream.next();

					if (predicate.test(element)) {
						setNext(element);
					}
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default Stream2<T> dropWhile(Predicate<? super T> predicate) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;
			boolean dropped = false;

			@Override
			protected void computeNext() {
				if (!dropped) {
					dropped = true;

					while (stream.hasNext()) {
						var element = stream.next();

						if (!predicate.test(element)) {
							setNext(element);
							return;
						}
					}
				} else {
					if (stream.hasNext()) {
						setNext(stream.next());
					}
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default List<T> toList() {
		try (var stream = Stream2.this) {
			var list = new ArrayList<T>();
			while (stream.hasNext()) {
				list.add(stream.next());
			}
			return list;
		}
	}

	default Object[] toArray() {
		return toList().toArray();
	}

	default T[] toArray(IntFunction<T[]> generator) {
		return toList().toArray(generator);
	}

	default void forEach(Consumer<T> consumer) {
		try (var stream = Stream2.this) {
			while (stream.hasNext()) {
				consumer.accept(stream.next());
			}
		}
	}

	default boolean allMatch(Predicate<? super T> predicate) {
		try (var stream = Stream2.this) {
			while (stream.hasNext()) {
				if (!predicate.test(stream.next())) {
					return false;
				}
			}
			return true;
		}
	}

	default boolean anyMatch(Predicate<? super T> predicate) {
		try (var stream = Stream2.this) {
			while (stream.hasNext()) {
				if (predicate.test(stream.next())) {
					return true;
				}
			}
			return false;
		}
	}

	default boolean noneMatch(Predicate<? super T> predicate) {
		try (var stream = Stream2.this) {
			while (stream.hasNext()) {
				if (predicate.test(stream.next())) {
					return false;
				}
			}
			return true;
		}
	}

	default <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> consumer) {
		try (var stream = Stream2.this) {
			var r = supplier.get();
			while (stream.hasNext()) {
				consumer.accept(r, stream.next());
			}
			return r;
		}
	}

	default long count() {
		try (var stream = Stream2.this) {
			var c = 0L;
			while (stream.hasNext()) {
				stream.next();
				c++;
			}
			return c;
		}
	}

	default Optional<T> findFirst() {
		try (var stream = Stream2.this) {
			if (stream.hasNext()) {
				return Optional.of(stream.next());
			} else {
				return Optional.empty();
			}
		}
	}

	default Stream2<T> distinct() {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;
			Iterator<T> iterator;

			@Override
			protected void computeNext() {
				if (iterator == null) {
					var set = new LinkedHashSet<T>();
					while (stream.hasNext()) {
						set.add(stream.next());
					}
					iterator = set.iterator();
				}
				if (iterator.hasNext()) {
					setNext(iterator.next());
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default Optional<T> max(Comparator<? super T> comparator) {
		try (var stream = Stream2.this) {
			if (!stream.hasNext()) {
				return Optional.empty();
			}
			var m = stream.next();
			while (stream.hasNext()) {
				var element = stream.next();
				if (comparator.compare(element, m) > 0) {
					m = element;
				}
			}
			return Optional.of(m);
		}
	}

	default Optional<T> min(Comparator<? super T> comparator) {
		try (var stream = Stream2.this) {
			if (!stream.hasNext()) {
				return Optional.empty();
			}
			var m = stream.next();
			while (stream.hasNext()) {
				var element = stream.next();
				if (comparator.compare(element, m) < 0) {
					m = element;
				}
			}
			return Optional.of(m);
		}
	}

	default Stream2<T> peek(Consumer<? super T> consumer) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;

			@Override
			protected void computeNext() {
				if (stream.hasNext()) {
					var element = stream.next();
					consumer.accept(element);
					setNext(element);
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default Optional<T> reduce(BinaryOperator<T> operator) {
		try (var stream = Stream2.this) {
			if (!stream.hasNext()) {
				return Optional.empty();
			}
			var element = stream.next();
			while (stream.hasNext()) {
				element = operator.apply(element, stream.next());
			}
			return Optional.of(element);
		}
	}

	default T reduce(T identity, BinaryOperator<T> operator) {
		try (var stream = Stream2.this) {
			var element = identity;
			while (stream.hasNext()) {
				element = operator.apply(element, stream.next());
			}
			return element;
		}
	}

	default <U> U reduce(U identity, BiFunction<U, ? super T, U> function) {
		try (var stream = Stream2.this) {
			var element = identity;
			while (stream.hasNext()) {
				element = function.apply(element, stream.next());
			}
			return element;
		}
	}

	default Stream2<T> sorted() {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;
			Iterator<T> iterator;

			@SuppressWarnings("unchecked")
			@Override
			protected void computeNext() {
				if (iterator == null) {
					var list = stream.toList();
					Collections.sort(list, (Comparator<? super T>) Comparator.naturalOrder());
					iterator = list.iterator();
				}
				if (iterator.hasNext()) {
					setNext(iterator.next());
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default Stream2<T> sorted(Comparator<? super T> comparator) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;
			Iterator<T> iterator;

			@Override
			protected void computeNext() {
				if (iterator == null) {
					var list = stream.toList();
					Collections.sort(list, comparator);
					iterator = list.iterator();
				}
				if (iterator.hasNext()) {
					setNext(iterator.next());
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default <R> Stream2<R> mapConcurrent(Function<Callable<R>, Future<R>> submit, int concurrency, Function<? super T, ? extends R> function) {
		return mapConcurrentOrdered(submit, concurrency, function);
	}

	default <R> Stream2<R> mapConcurrentOrdered(Function<Callable<R>, Future<R>> submit, int concurrency, Function<? super T, ? extends R> function) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;
			LinkedList<Future<R>> queue;

			@Override
			protected void computeNext() {
				if (queue == null) {
					queue = new LinkedList<Future<R>>();

					for (var i = 0; i < concurrency && stream.hasNext(); i++) {
						var element = stream.next();
						queue.addLast(submit.apply(() -> function.apply(element)));
					}
				}

				if (queue.isEmpty()) {
					return;
				}

				try {
					setNext(queue.removeFirst().get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e);
				} catch (ExecutionException e) {
					if (e.getCause() instanceof RuntimeException) {
						throw (RuntimeException) e.getCause();
					} else if (e.getCause() instanceof Error) {
						throw (Error) e.getCause();
					} else {
						throw new RuntimeException(e.getCause());
					}
				}

				if (stream.hasNext()) {
					var element = stream.next();
					queue.addLast(submit.apply(() -> function.apply(element)));
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	class QueueingTask<T> extends FutureTask<T> implements Callable<Object> {
		private final BlockingQueue<QueueingTask<T>> queue;

		public QueueingTask(BlockingQueue<QueueingTask<T>> queue, Callable<T> task) {
			super(task);
			this.queue = queue;
		}

		@Override
		public Object call() throws Exception {
			run();
			return null;
		}

		@Override
		protected void done() {
			queue.add(this);
		}
	}

	default <R> Stream2<R> mapConcurrentUnordered(Function<Callable<Object>, Future<Object>> submit, int concurrency, Function<? super T, ? extends R> function) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;
			BlockingQueue<QueueingTask<R>> queue;
			int count;

			@Override
			protected void computeNext() {
				if (queue == null) {
					queue = new LinkedBlockingQueue<>();
					count = 0;

					while (count < concurrency && stream.hasNext()) {
						var element = stream.next();
						submit.apply(new QueueingTask<>(queue, () -> function.apply(element)));
						count++;
					}
				}

				if (count <= 0) {
					return;
				}

				try {
					setNext(queue.take().get());
					count--;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e);
				} catch (ExecutionException e) {
					if (e.getCause() instanceof RuntimeException) {
						throw (RuntimeException) e.getCause();
					} else if (e.getCause() instanceof Error) {
						throw (Error) e.getCause();
					} else {
						throw new RuntimeException(e.getCause());
					}
				}

				if (stream.hasNext()) {
					var element = stream.next();
					submit.apply(new QueueingTask<>(queue, () -> function.apply(element)));
					count++;
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default <R> Stream2<R> scan(Supplier<R> seed, BiFunction<? super R, ? super T, ? extends R> function) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;
			boolean init = true;
			R element;

			@Override
			protected void computeNext() {
				if (init) {
					init = false;
					element = seed.get();
				}
				if (stream.hasNext()) {
					element = function.apply(element, stream.next());
					setNext(element);
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default Stream2<List<T>> windowFixed(int size) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;

			@Override
			protected void computeNext() {
				var list = new ArrayList<T>(size);

				for (var i = 0; i < size && stream.hasNext(); i++) {
					list.add(stream.next());
				}

				if (!list.isEmpty()) {
					setNext(list);
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default Stream2<List<T>> windowSliding(int size) {
		return new AbstractStream<>() {
			Stream2<T> stream = Stream2.this;
			ArrayDeque<T> queue;

			@Override
			protected void computeNext() {
				if (queue == null) {
					queue = new ArrayDeque<>();

					for (var i = 0; i < size && stream.hasNext(); i++) {
						queue.addLast(stream.next());
					}

					if (!queue.isEmpty()) {
						setNext(new ArrayList<>(queue));
					}
				} else {
					if (stream.hasNext()) {
						queue.removeFirst();
						queue.addLast(stream.next());
						setNext(new ArrayList<>(queue));
					}
				}
			}

			@Override
			protected void onClose() {
				stream.close();
			}
		};
	}

	default <K> Map<K, List<T>> groupingBy(Function<? super T, ? extends K> function) {
		try (var stream = Stream2.this) {
			var map = new LinkedHashMap<K, List<T>>();
			while (stream.hasNext()) {
				var element = stream.next();
				var key = function.apply(element);
				map.computeIfAbsent(key, k -> new ArrayList<>()).add(element);
			}
			return map;
		}
	}

	default String joining() {
		try (var stream = Stream2.this) {
			var builder = new StringBuilder();
			while (stream.hasNext()) {
				builder.append(stream.next());
			}
			return builder.toString();
		}
	}

	default String joining(CharSequence delimiter) {
		try (var stream = Stream2.this) {
			var builder = new StringBuilder();
			if (stream.hasNext()) {
				builder.append(stream.next());
			}
			while (stream.hasNext()) {
				builder.append(delimiter);
				builder.append(stream.next());
			}
			return builder.toString();
		}
	}

	default String joining(CharSequence delimiter, CharSequence start, CharSequence end) {
		try (var stream = Stream2.this) {
			var builder = new StringBuilder();
			builder.append(start);
			if (stream.hasNext()) {
				builder.append(stream.next());
			}
			while (stream.hasNext()) {
				builder.append(delimiter);
				builder.append(stream.next());
			}
			builder.append(end);
			return builder.toString();
		}
	}

	default double summingDouble(ToDoubleFunction<? super T> function) {
		try (var stream = Stream2.this) {
			var s = 0D;
			while (stream.hasNext()) {
				s += function.applyAsDouble(stream.next());
			}
			return s;
		}
	}

	default int summingInt(ToIntFunction<? super T> function) {
		try (var stream = Stream2.this) {
			var s = 0;
			while (stream.hasNext()) {
				s += function.applyAsInt(stream.next());
			}
			return s;
		}
	}

	default long summingLong(ToLongFunction<? super T> function) {
		try (var stream = Stream2.this) {
			var s = 0L;
			while (stream.hasNext()) {
				s += function.applyAsLong(stream.next());
			}
			return s;
		}
	}

	default double averagingDouble(ToDoubleFunction<? super T> function) {
		try (var stream = Stream2.this) {
			var s = 0D;
			var c = 0D;
			while (stream.hasNext()) {
				s += function.applyAsDouble(stream.next());
				c++;
			}
			return s / c;
		}
	}

	default int averagingInt(ToIntFunction<? super T> function) {
		try (var stream = Stream2.this) {
			var s = 0;
			var c = 0;
			while (stream.hasNext()) {
				s += function.applyAsInt(stream.next());
				c++;
			}
			return s / c;
		}
	}

	default long averagingLong(ToLongFunction<? super T> function) {
		try (var stream = Stream2.this) {
			var s = 0L;
			var c = 0L;
			while (stream.hasNext()) {
				s += function.applyAsLong(stream.next());
				c++;
			}
			return s / c;
		}
	}

	default <C extends Collection<T>> C toCollection(Supplier<C> supplier) {
		try (var stream = Stream2.this) {
			var c = supplier.get();
			while (stream.hasNext()) {
				c.add(stream.next());
			}
			return c;
		}
	}

	default <K> Map<K, T> toMap(Function<? super T, ? extends K> function) {
		try (var stream = Stream2.this) {
			var map = new LinkedHashMap<K, T>();
			while (stream.hasNext()) {
				var element = stream.next();
				var key = function.apply(element);
				map.put(key, element);
			}
			return map;
		}
	}

	default <K, V> Map<K, V> toMap(Function<? super T, ? extends K> toKey, Function<? super T, ? extends V> toValue) {
		try (var stream = Stream2.this) {
			var map = new LinkedHashMap<K, V>();
			while (stream.hasNext()) {
				var element = stream.next();
				var key = toKey.apply(element);
				var value = toValue.apply(element);
				map.put(key, value);
			}
			return map;
		}
	}

	default Set<T> toSet() {
		try (var stream = Stream2.this) {
			var set = new LinkedHashSet<T>();
			while (stream.hasNext()) {
				set.add(stream.next());
			}
			return set;
		}
	}
}
