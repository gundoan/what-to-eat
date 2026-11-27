package com.whattoeat.room.sse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 방마다 열려 있는 SSE 연결 목록.
 * 서버 한 대의 메모리에만 있다. 서버를 여러 대로 늘리면 서버끼리 이벤트를 전달해야 한다.
 */
@Component
class RoomChannels {

	private final Map<String, RoomChannel> channels = new ConcurrentHashMap<>();

	/** 연결을 방에 등록하고, 그 방의 채널을 돌려준다. */
	RoomChannel add(String roomCode, Subscriber subscriber) {
		return channels.compute(roomCode, (code, existing) -> {
			RoomChannel target = existing != null ? existing : new RoomChannel();
			target.subscribers.add(subscriber);
			return target;
		});
	}

	/** 끊긴 연결을 뺀다. 방에 남은 연결이 없으면 채널도 지운다. 이미 빠진 연결이면 아무 일도 없다. */
	void remove(String roomCode, Subscriber subscriber) {
		channels.computeIfPresent(roomCode, (code, channel) -> {
			channel.subscribers.remove(subscriber);
			return channel.subscribers.isEmpty() ? null : channel;
		});
	}

	Optional<RoomChannel> find(String roomCode) {
		return Optional.ofNullable(channels.get(roomCode));
	}

	void forEach(BiConsumer<String, RoomChannel> action) {
		channels.forEach(action);
	}

	/** SSE 연결 하나와 그 연결을 연 참여자. 한 사람이 탭을 여러 개 열면 연결도 여러 개다. */
	record Subscriber(long participantId, SseEmitter emitter) {
	}

	/** 한 방의 연결들. 같은 방에 대한 읽기와 보내기는 이 객체로 동기화해서 한 번에 하나씩 한다. */
	static final class RoomChannel {

		private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

		/** 지금 연결들. 도는 동안 연결이 늘거나 줄어도 안전하다(돌기 시작한 순간의 목록을 돈다). */
		List<Subscriber> subscribers() {
			return Collections.unmodifiableList(subscribers);
		}

	}

}
