package com.whattoeat.room.sse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.whattoeat.room.application.RoomPresence;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 방마다 열려 있는 SSE 연결 목록. "지금 접속 중"인 사람도 이 목록으로 센다.
 * 서버 한 대의 메모리에만 있다. 서버를 여러 대로 늘리면 서버끼리 이벤트와 접속 정보를 나눠야 한다.
 */
@Component
class RoomChannels implements RoomPresence {

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

	@Override
	public Set<Long> connectedParticipantIds(String roomCode) {
		return find(roomCode).map(RoomChannel::participantIds).orElse(Set.of());
	}

	/** SSE 연결 하나와 그 연결을 연 참여자. 한 사람이 탭을 여러 개 열면 연결도 여러 개다. */
	record Subscriber(long participantId, SseEmitter emitter) {
	}

	/** 한 방의 연결들. 같은 방에 대한 읽기와 보내기는 이 객체로 동기화해서 한 번에 하나씩 한다. */
	static final class RoomChannel {

		private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

		/**
		 * 이 방에 마지막으로 보낸 방 상태에 들어 있던 "지금 접속 중" 인원. 아직 아무것도 보내지 않았으면 -1이다.
		 * 이 객체로 동기화한 채로만 읽고 쓴다.
		 */
		private long sentConnectedCount = -1;

		/** 지금 연결들. 도는 동안 연결이 늘거나 줄어도 안전하다(돌기 시작한 순간의 목록을 돈다). */
		List<Subscriber> subscribers() {
			return Collections.unmodifiableList(subscribers);
		}

		/** 지금 연결된 참여자 번호들. 탭을 여러 개 연 사람도 한 번만 들어간다. */
		Set<Long> participantIds() {
			return subscribers.stream().map(Subscriber::participantId).collect(Collectors.toUnmodifiableSet());
		}

		/** 연결이 늘거나 줄어서, 모두가 마지막으로 받은 "지금 접속 중" 인원이 지금과 달라졌는지. 남은 연결이 없으면 보낼 사람도 없다. */
		boolean connectedCountChanged() {
			return !subscribers.isEmpty() && participantIds().size() != sentConnectedCount;
		}

		/** 방 상태를 보냈다. 그 상태에 들어 있던 "지금 접속 중" 인원을 적어 둔다. */
		void markSent(long connectedCount) {
			this.sentConnectedCount = connectedCount;
		}

	}

}
