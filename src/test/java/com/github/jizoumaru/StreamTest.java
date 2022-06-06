package com.github.jizoumaru;

public class StreamTest {
	public static void main(String[] args) {
		Stream.iterate(0, x -> x + 1)
			.skip(0)
			.filter(x -> true)
			.map(x -> x)
			.flatMap(x -> Stream.of(x))
			.limit(10)
			.forEach(System.out::println);
	}
}
