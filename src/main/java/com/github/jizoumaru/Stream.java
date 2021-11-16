package com.github.jizoumaru;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;

public abstract class Stream<T> implements Iterator<T> {

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

	public static class Nullable<T> {
		public static <T> Nullable<T> of(T value) {
			return new Nullable<>(value, true);
		}

		public static <T> Nullable<T> none() {
			return new Nullable<>(null, false);
		}

		private final T value;
		private final boolean exist;

		private Nullable(T value, boolean exist) {
			this.value = value;
			this.exist = exist;
		}

		public T value() {
			if (exist) {
				return value;
			} else {
				throw new NoSuchElementException();
			}
		}

		public boolean exists() {
			return exist;
		}
	}

	public static <T> Stream<T> concat(final Stream<T> a, final Stream<T> b) {
		return new Stream<T>() {
			@Override
			protected Nullable<T> get() {
				if (a.hasNext()) {
					return Nullable.of(a.next());
				}

				if (b.hasNext()) {
					return Nullable.of(b.next());
				}

				return Nullable.none();
			}
		};
	}

	public static <T> Stream<T> empty() {
		return new Stream<T>() {
			@Override
			protected Nullable<T> get() {
				return Nullable.none();
			}
		};
	}

	public static <T> Stream<T> generate(final Supplier<T> s) {
		return new Stream<T>() {
			@Override
			protected Nullable<T> get() {
				return Nullable.of(s.get());
			}
		};
	}

	public static <T> Stream<T> iterate(final T seed, final Predicate<? super T> hasNext, final UnaryOperator<T> next) {
		return new Stream<T>() {
			Nullable<T> val;

			@Override
			protected Nullable<T> get() {
				if (val == null) {
					val = Nullable.of(seed);
				} else {
					val = Nullable.of(next.apply(val.value()));
				}

				if (hasNext.test(val.value())) {
					return val;
				} else {
					return Nullable.none();
				}
			}
		};
	}

	public static <T> Stream<T> iterate(final T seed, final UnaryOperator<T> f) {
		return new Stream<T>() {
			Nullable<T> val;

			@Override
			protected Nullable<T> get() {
				if (val == null) {
					val = Nullable.of(seed);
				} else {
					val = Nullable.of(f.apply(val.value()));
				}
				return val;
			}
		};
	}

	public static <T> Stream<T> of(final T t) {
		return new Stream<T>() {
			Nullable<T> val;

			@Override
			protected Nullable<T> get() {
				if (val == null) {
					val = Nullable.of(t);
				} else {
					val = Nullable.none();
				}
				return val;
			}
		};

	}

	@SafeVarargs
	public static <T> Stream<T> of(final T... values) {
		return new Stream<T>() {
			int i = 0;

			@Override
			protected Nullable<T> get() {
				if (i < values.length) {
					return Nullable.of(values[i++]);
				} else {
					return Nullable.none();
				}
			}
		};

	}

	public static <T> Stream<T> of(final Iterator<T> iterator) {
		return new Stream<T>() {
			@Override
			protected Nullable<T> get() {
				if (iterator.hasNext()) {
					return Nullable.of(iterator.next());
				} else {
					return Nullable.none();
				}
			}
		};
	}

	private Nullable<T> value;

	@Override
	public boolean hasNext() {
		if (value == null) {
			value = get();
		}
		return value.exists();
	}

	@Override
	public T next() {
		if (hasNext()) {
			Nullable<T> ret = value;
			value = null;
			return ret.value();
		} else {
			throw new NoSuchElementException();
		}
	}

	@Override
	public void remove() {
		throw new RuntimeException("not suported");
	}

	protected abstract Nullable<T> get();

	public Stream<T> filter(final Predicate<? super T> predicate) {
		return new Stream<T>() {
			@Override
			protected Nullable<T> get() {
				while (Stream.this.hasNext()) {
					T val = Stream.this.next();
					if (predicate.test(val)) {
						return Nullable.of(val);
					}
				}
				return Nullable.none();
			}
		};
	}

	public <R> Stream<R> map(final Function<T, R> mapper) {
		return new Stream<R>() {
			@Override
			protected Nullable<R> get() {
				if (Stream.this.hasNext()) {
					return Nullable.of(mapper.apply(Stream.this.next()));
				}
				return Nullable.none();
			}
		};
	}

	public <R> Stream<R> flatMap(final Function<T, Stream<R>> mapper) {
		return new Stream<R>() {
			Stream<R> inner;

			@Override
			protected Nullable<R> get() {
				while (inner == null || !inner.hasNext()) {
					if (!Stream.this.hasNext()) {
						return Nullable.none();
					}
					inner = mapper.apply(Stream.this.next());
				}
				return Nullable.of(inner.next());
			}
		};
	}

	public Stream<T> distinct() {
		return new Stream<T>() {
			Iterator<T> iter;

			@Override
			protected Nullable<T> get() {
				if (iter == null) {
					LinkedHashSet<T> set = new LinkedHashSet<>();
					while (Stream.this.hasNext()) {
						set.add(Stream.this.next());
					}
					iter = set.iterator();
				}

				if (iter.hasNext()) {
					return Nullable.of(iter.next());
				} else {
					return Nullable.none();
				}
			}
		};
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
		return new Stream<T>() {
			Iterator<T> iter;

			@Override
			protected Nullable<T> get() {
				if (iter == null) {
					ArrayList<T> list = new ArrayList<T>();
					while (Stream.this.hasNext()) {
						list.add(Stream.this.next());
					}
					Collections.sort(list, comparator);
					iter = list.iterator();
				}
				if (iter.hasNext()) {
					return Nullable.of(iter.next());
				} else {
					return Nullable.none();
				}
			}
		};
	}

	public Stream<T> peek(final Consumer<? super T> action) {
		return new Stream<T>() {
			@Override
			protected Nullable<T> get() {
				if (Stream.this.hasNext()) {
					T val = Stream.this.next();
					action.accept(val);
					return Nullable.of(val);
				}
				return Nullable.none();
			}
		};
	}

	public Stream<T> limit(final long maxSize) {
		return new Stream<T>() {
			long i = 0L;

			@Override
			protected Nullable<T> get() {
				if (i < maxSize && Stream.this.hasNext()) {
					i++;
					return Nullable.of(Stream.this.next());
				}
				return Nullable.none();
			}
		};
	}

	public Stream<T> skip(final long n) {
		return new Stream<T>() {
			long i = 0L;

			@Override
			protected Nullable<T> get() {
				while (i < n && Stream.this.hasNext()) {
					i++;
					Stream.this.next();
				}
				if (Stream.this.hasNext()) {
					return Nullable.of(Stream.this.next());
				} else {
					return Nullable.none();
				}
			}
		};

	}

	public Stream<T> takeWhile(final Predicate<? super T> predicate) {
		return new Stream<T>() {
			@Override
			protected Nullable<T> get() {
				if (Stream.this.hasNext()) {
					T val = Stream.this.next();
					if (predicate.test(val)) {
						return Nullable.of(val);
					}
				}
				return Nullable.none();
			}
		};
	}

	public void forEach(final Consumer<? super T> action) {
		while (Stream.this.hasNext()) {
			action.accept(Stream.this.next());
		}
	}

	public Object[] toArray() {
		ArrayList<T> list = new ArrayList<T>();
		while (Stream.this.hasNext()) {
			list.add(Stream.this.next());
		}
		return list.toArray();
	}

	public <A> A[] toArray(Function<Integer, A[]> generator) {
		ArrayList<T> list = new ArrayList<T>();
		while (Stream.this.hasNext()) {
			list.add(Stream.this.next());
		}
		return list.toArray(generator.apply(list.size()));
	}

	public T reduce(T identity, BinaryOperator<T> accumulator) {
		T ret = identity;
		while (Stream.this.hasNext()) {
			ret = accumulator.apply(ret, Stream.this.next());
		}
		return ret;
	}

	public Optional<T> reduce(BinaryOperator<T> accumulator) {
		T val = null;

		while (Stream.this.hasNext()) {
			if (val == null) {
				val = Stream.this.next();
			} else {
				val = accumulator.apply(val, Stream.this.next());
			}
		}

		return Optional.of(val);
	}

	public ArrayList<T> toList() {
		ArrayList<T> list = new ArrayList<T>();
		while (Stream.this.hasNext()) {
			list.add(Stream.this.next());
		}
		return list;
	}
	
	public LinkedHashSet<T> toSet() {
		LinkedHashSet<T> set = new LinkedHashSet<T>();
		while (Stream.this.hasNext()) {
			set.add(Stream.this.next());
		}
		return set;
	}

	public <K, V> LinkedHashMap<K, V> toMap(Function<T, K> keySelector, Function<T, V> valueSelector) {
		LinkedHashMap<K, V> map = new LinkedHashMap<K, V>();
		while (Stream.this.hasNext()) {
			T item = Stream.this.next();
			map.put(keySelector.apply(item), valueSelector.apply(item));
		}
		return map;
	}

	public Optional<T> min(Comparator<? super T> comparator) {
		T ret = null;
		while (Stream.this.hasNext()) {
			T val = Stream.this.next();
			if (ret == null || comparator.compare(val, ret) < 0) {
				ret = val;
			}
		}
		return Optional.of(ret);
	}

	public Optional<T> max(Comparator<? super T> comparator) {
		T ret = null;
		while (Stream.this.hasNext()) {
			T val = Stream.this.next();
			if (ret == null || comparator.compare(ret, val) < 0) {
				ret = val;
			}
		}
		return Optional.of(ret);
	}

	public long count() {
		long c = 0L;
		while (Stream.this.hasNext()) {
			Stream.this.next();
			c++;
		}
		return c;
	}

	public boolean anyMatch(Predicate<? super T> predicate) {
		while (Stream.this.hasNext()) {
			if (predicate.test(Stream.this.next())) {
				return true;
			}
		}
		return false;
	}

	public boolean allMatch(Predicate<? super T> predicate) {
		while (Stream.this.hasNext()) {
			if (!predicate.test(Stream.this.next())) {
				return false;
			}
		}
		return true;
	}

	public boolean noneMatch(Predicate<? super T> predicate) {
		while (Stream.this.hasNext()) {
			if (predicate.test(Stream.this.next())) {
				return false;
			}
		}
		return true;
	}

	public Optional<T> findFirst() {
		if (Stream.this.hasNext()) {
			return Optional.of(Stream.this.next());
		}
		return Optional.empty();
	}

	public Optional<T> findAny() {
		if (Stream.this.hasNext()) {
			return Optional.of(Stream.this.next());
		}
		return Optional.empty();
	}

}
