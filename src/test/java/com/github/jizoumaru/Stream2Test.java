package com.github.jizoumaru;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

public class Stream2Test {
	@Test
	public void testRange() {
		assertEquals(List.of(1, 2, 3), Stream2.range(1, 4).toList());
	}

	@Test
	public void testAllMatch() {
		assertEquals(true, Stream2.of().allMatch("A"::equals));
		assertEquals(true, Stream2.of("A").allMatch("A"::equals));
		assertEquals(false, Stream2.of("B").allMatch("A"::equals));
		assertEquals(true, Stream2.of("A", "A").allMatch("A"::equals));
		assertEquals(false, Stream2.of("A", "B").allMatch("A"::equals));
	}

	@Test
	public void testAnyMatch() {
		assertEquals(false, Stream2.of().anyMatch("A"::equals));
		assertEquals(true, Stream2.of("A").anyMatch("A"::equals));
		assertEquals(false, Stream2.of("B").anyMatch("A"::equals));
		assertEquals(true, Stream2.of("A", "B").anyMatch("A"::equals));
		assertEquals(false, Stream2.of("B", "B").anyMatch("A"::equals));
	}

	@Test
	public void testNoneMatch() {
		assertEquals(true, Stream2.of().noneMatch("A"::equals));
		assertEquals(true, Stream2.of("B").noneMatch("A"::equals));
		assertEquals(false, Stream2.of("A").noneMatch("A"::equals));
		assertEquals(true, Stream2.of("B", "B").noneMatch("A"::equals));
		assertEquals(false, Stream2.of("A", "B").noneMatch("A"::equals));
	}

	@Test
	public void testMap() {
		assertEquals(List.of("A", "B"), Stream2.of("A", "B").map(x -> x).toList());
		assertEquals(List.of("X", "X"), Stream2.of("A", "B").map(x -> "X").toList());
	}

	@Test
	public void testFlatMap() {
		assertEquals(List.of("A", "B"), Stream2.of("A", "B").flatMap(Stream2::of).toList());
		assertEquals(List.of("X", "X"), Stream2.of("A", "B").flatMap(x -> Stream2.of("X")).toList());
	}

	@Test
	public void testFilter() {
		assertEquals(List.of("A", "B"), Stream2.of("A", "B").filter(x -> true).toList());
		assertEquals(List.of(), Stream2.of("A", "B").filter(x -> false).toList());
	}

	@Test
	public void testFindFirst() {
		assertEquals(Optional.empty(), Stream2.of().findFirst());
		assertEquals(Optional.of("A"), Stream2.of("A", "B").findFirst());
	}

	@Test
	public void testMax() {
		assertEquals(Optional.empty(), Stream2.<String>of().max(Comparator.naturalOrder()));
		assertEquals(Optional.of("B"), Stream2.of("A", "B").max(Comparator.naturalOrder()));
		assertEquals(Optional.of("B"), Stream2.of("B", "A").max(Comparator.naturalOrder()));
	}

	@Test
	public void testMin() {
		assertEquals(Optional.empty(), Stream2.<String>of().min(Comparator.naturalOrder()));
		assertEquals(Optional.of("A"), Stream2.of("A", "B").min(Comparator.naturalOrder()));
		assertEquals(Optional.of("A"), Stream2.of("B", "A").min(Comparator.naturalOrder()));
	}

	@Test
	public void testLimit() {
		assertEquals(List.of("A"), Stream2.of("A", "B").limit(1).toList());
		assertEquals(List.of("A", "B"), Stream2.of("A", "B").limit(2).toList());
		assertEquals(List.of("A", "B"), Stream2.of("A", "B").limit(3).toList());
	}

	@Test
	public void testSkip() {
		assertEquals(List.of("B"), Stream2.of("A", "B").skip(1).toList());
		assertEquals(List.of(), Stream2.of("A", "B").skip(2).toList());
		assertEquals(List.of(), Stream2.of("A", "B").skip(3).toList());
	}

	@Test
	public void testJoining() {
		assertEquals("AB", Stream2.of("A", "B").joining());
		assertEquals("A,B", Stream2.of("A", "B").joining(","));
		assertEquals("[A,B]", Stream2.of("A", "B").joining(",", "[", "]"));
	}

	@Test
	public void testReduce() {
		assertEquals(Optional.empty(), Stream2.<String>of().reduce(String::concat));
		assertEquals(Optional.of("AB"), Stream2.of("A", "B").reduce(String::concat));
		assertEquals("X", Stream2.<String>of().reduce("X", String::concat));
		assertEquals("XAB", Stream2.<String>of("A", "B").reduce("X", String::concat));
		assertEquals(0, Stream2.<String>of().reduce(0, (l, s) -> l + s.length()));
		assertEquals(2, Stream2.of("A", "B").reduce(0, (l, s) -> l + s.length()));
	}

	@Test
	public void testScan() {
		assertEquals(List.of("XA", "XAB"), Stream2.of("A", "B").scan(() -> "X", String::concat).toList());
	}

	@Test
	public void testDropWhile() {
		assertEquals(List.of("B", "B"), Stream2.of("A", "A", "B", "B").dropWhile("A"::equals).toList());
	}

	@Test
	public void testTakeWhile() {
		assertEquals(List.of("A", "A"), Stream2.of("A", "A", "B", "B").takeWhile("A"::equals).toList());
	}

	@Test
	public void testWindowFixed() {
		assertEquals(List.of(List.of("A", "B"), List.of("C")), Stream2.of("A", "B", "C").windowFixed(2).toList());
	}

	@Test
	public void testWindowSliding() {
		assertEquals(List.of(List.of("A", "B"), List.of("B", "C")), Stream2.of("A", "B", "C").windowSliding(2).toList());
	}

	@Test
	public void testToArray() {
		assertArrayEquals(new Object[] { "A", "B" }, Stream2.of("A", "B").toArray());
		assertArrayEquals(new String[] { "A", "B" }, Stream2.of("A", "B").toArray(String[]::new));
	}

	@Test
	public void testToSet() {
		assertEquals(Set.of("A", "B"), Stream2.of("A", "B").toSet());
	}

	@Test
	public void testToList() {
		assertEquals(List.of("A", "B"), Stream2.of("A", "B").toList());
	}

	@Test
	public void testToMap() {
		assertEquals(Map.of("KA", "A", "KB", "B"), Stream2.of("A", "B").toMap("K"::concat));
		assertEquals(Map.of("KA", "VA", "KB", "VB"), Stream2.of("A", "B").toMap("K"::concat, "V"::concat));
	}

	@Test
	public void testToCollection() {
		assertEquals(List.of("A", "B"), Stream2.of("A", "B").toCollection(LinkedList::new));
	}

	@Test
	public void testCollect() {
		assertEquals(List.of("A", "B"), Stream2.of("A", "B").collect(LinkedList::new, LinkedList::add));
	}

	@Test
	public void testCount() {
		assertEquals(0, Stream2.of().count());
		assertEquals(2, Stream2.of("A", "B").count());
	}

	@Test
	public void testDistinct() {
		assertEquals(List.of("A", "B"), Stream2.of("A", "B", "A", "B").distinct().toList());
	}

	@Test
	public void testGroupingBy() {
		assertEquals(Map.of("KA", List.of("A", "A"), "KB", List.of("B", "B")), Stream2.of("A", "B", "A", "B").groupingBy("K"::concat));
	}

	@Test
	public void testSummingDouble() {
		assertEquals(6D, Stream2.of(1, 2, 3).summingDouble(Integer::doubleValue));
	}

	@Test
	public void testSummingInt() {
		assertEquals(6, Stream2.of(1, 2, 3).summingInt(Integer::intValue));
	}

	@Test
	public void testSummingLong() {
		assertEquals(6L, Stream2.of(1, 2, 3).summingLong(Integer::longValue));
	}

	@Test
	public void testAveragingDouble() {
		assertEquals(2D, Stream2.of(1, 2, 3).averagingDouble(Integer::doubleValue));
	}

	@Test
	public void testAveragingInt() {
		assertEquals(2, Stream2.of(1, 2, 3).averagingInt(Integer::intValue));
	}

	@Test
	public void testAveragingLong() {
		assertEquals(2L, Stream2.of(1, 2, 3).averagingLong(Integer::longValue));
	}

	@Test
	public void testMapConcurrent() {
		var executor = Executors.newFixedThreadPool(2);
		assertEquals(List.of("XA", "XB", "XC"), Stream2.of("A", "B", "C").mapConcurrent(executor::submit, 2, "X"::concat).toList());
		executor.shutdown();
	}

	@Test
	public void testMapConcurrentUnordered() {
		var executor = Executors.newFixedThreadPool(2);
		assertEquals(Set.of("XA", "XB", "XC"), Stream2.of("A", "B", "C").mapConcurrentUnordered(executor::submit, 2, "X"::concat).toSet());
		executor.shutdown();
	}

	@Test
	public void testSorted() {
		var executor = Executors.newFixedThreadPool(3);
		assertEquals(List.of("A", "B"), Stream2.of("B", "A").sorted().toList());
		assertEquals(List.of("B", "A"), Stream2.of("A", "B").sorted(Comparator.reverseOrder()).toList());
		executor.shutdown();
	}

	@Test
	public void testFrom() {
		assertEquals(List.of("A", "B"), Stream2.from(List.of("A", "B")).toList());
		assertEquals(List.of("A", "B"), Stream2.from(List.of("A", "B").iterator()).toList());
		assertEquals(List.of("A", "B"), Stream2.from(List.of("A", "B")::stream).toList());
	}

	@Test
	public void testGenerate() {
		assertEquals(List.of("A", "A"), Stream2.generate(() -> "A").limit(2).toList());
	}

	@Test
	public void testEmpty() {
		assertEquals(List.of(), Stream2.empty().toList());
	}

	@Test
	public void testConcat() {
		assertEquals(List.of("A", "B"), Stream2.concat(Stream2.of("A"), Stream2.of("B")).toList());
	}

	@Test
	public void testIterate() {
		assertEquals(List.of("A", "BA"), Stream2.iterate("A", "B"::concat).limit(2).toList());
		assertEquals(List.of("A", "BA"), Stream2.iterate("A", x -> x.length() <= 2, "B"::concat).toList());
	}

	@Test
	public void testOf() {
		assertEquals(List.of("A", "B"), Stream2.of("A", "B").toList());
	}

	@Test
	public void testOfNullable() {
		assertEquals(List.of(), Stream2.ofNullable(null).toList());
		assertEquals(List.of("A"), Stream2.ofNullable("A").toList());
	}

	@Test
	public void testForEach() {
		var list = new ArrayList<String>();
		Stream2.of("A", "B").forEach(list::add);
		assertEquals(List.of("A", "B"), list);
	}

	@Test
	public void testPeek() {
		var list = new ArrayList<String>();
		assertEquals(List.of("A", "B"), Stream2.of("A", "B").peek(list::add).toList());
		assertEquals(List.of("A", "B"), list);
	}
}
