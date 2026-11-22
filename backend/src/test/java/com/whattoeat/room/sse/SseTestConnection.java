package com.whattoeat.room.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 테스트에서 SSE 연결을 열어두고 줄 단위로 읽는 도구.
 * SSE는 "event:이름" 줄과 "data:내용" 줄이 오고 빈 줄로 이벤트가 끝난다. ":"로 시작하는 줄은 주석(하트비트)이다.
 */
final class SseTestConnection implements AutoCloseable {

	private final Stream<String> lines;

	private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

	private final Thread reader;

	private SseTestConnection(Stream<String> lines) {
		this.lines = lines;
		this.reader = Thread.ofVirtual().start(() -> {
			try {
				lines.forEach(queue::add);
			}
			catch (RuntimeException e) {
				// 테스트가 끝나 연결을 닫으면 읽기가 중단되며 예외가 난다. 정상이다.
			}
		});
	}

	static SseTestConnection open(HttpClient http, URI uri, String cookie) throws Exception {
		HttpResponse<Stream<String>> response = http.send(HttpRequest.newBuilder(uri)
			.header("Accept", "text/event-stream")
			.header("Cookie", cookie)
			.GET()
			.build(), HttpResponse.BodyHandlers.ofLines());
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("text/event-stream"));
		return new SseTestConnection(response.body());
	}

	/** 원하는 이름의 이벤트가 올 때까지 기다렸다가 data 부분을 돌려준다. */
	String nextEventData(String eventName, Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		String currentEvent = null;
		while (true) {
			long remaining = deadline - System.nanoTime();
			String line = queue.poll(Math.max(remaining, 0), TimeUnit.NANOSECONDS);
			if (line == null) {
				throw new AssertionError("'" + eventName + "' 이벤트를 " + timeout.toSeconds() + "초 안에 받지 못했다");
			}
			if (line.startsWith("event:")) {
				currentEvent = line.substring("event:".length()).trim();
			}
			else if (line.startsWith("data:") && eventName.equals(currentEvent)) {
				return line.substring("data:".length());
			}
			else if (line.isEmpty()) {
				currentEvent = null;
			}
		}
	}

	@Override
	public void close() {
		lines.close();
		reader.interrupt();
	}

}
