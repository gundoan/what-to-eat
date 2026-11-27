package com.whattoeat.room.sse;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.whattoeat.room.application.RoomChangedEvent;
import com.whattoeat.room.application.RoomStateReader;
import com.whattoeat.room.application.RoomView;
import com.whattoeat.room.sse.RoomChannels.RoomChannel;
import com.whattoeat.room.sse.RoomChannels.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 방마다 연결된 SSE 연결(RoomChannels)에, 방 상태가 바뀌면 모두에게 최신 화면 정보를 보낸다.
 *
 * <ul>
 * <li>바뀐 부분이 아니라 화면 정보 전체(방 상태 + 받는 사람 본인의 정보)를 보낸다. 연결이 끊겼다 다시 붙어도 화면이 어긋나지 않는다.</li>
 * <li>방 상태는 모두에게 같고, 본인 정보(me)는 연결마다 그 연결을 연 참여자 것만 붙인다. 다른 사람의 정보는 보내지 않는다.</li>
 * <li>보낼 때마다 DB에서 최신 상태를 다시 읽고, 같은 방에 대한 읽기와 보내기는 한 번에 하나씩 한다.
 * 그래서 늦게 읽은(더 최신인) 상태가 먼저 읽은 상태보다 먼저 도착하는 일이 없다.</li>
 * <li>방 상태에는 "지금 접속 중"인 사람 수도 들어 있다. 새 사람이 방 화면을 열거나, 끊긴 연결을 정리해서 이 숫자가 바뀌면 모두에게 다시 보낸다.</li>
 * </ul>
 */
@Component
public class RoomEventBroadcaster {

	private static final Logger log = LoggerFactory.getLogger(RoomEventBroadcaster.class);

	static final String ROOM_EVENT = "room";

	/** 연결을 이 시간 뒤에 서버가 닫는다. 브라우저(EventSource)는 자동으로 다시 연결한다. */
	private static final long CONNECTION_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

	/** 하트비트 간격(초). 끊긴 연결을 알아채는 시간도 이것에 달려 있다. */
	private static final long HEARTBEAT_SECONDS = 10;

	private final RoomChannels channels;

	private final RoomStateReader roomStateReader;

	RoomEventBroadcaster(RoomChannels channels, RoomStateReader roomStateReader) {
		this.channels = channels;
		this.roomStateReader = roomStateReader;
	}

	/**
	 * 새 연결을 등록하고, 연결되자마자 현재 화면 정보를 한 번 보낸다.
	 *
	 * @param participantId 연결을 연 참여자. 쿠키로 확인한 값이라 다른 사람 행세를 할 수 없다
	 */
	public SseEmitter subscribe(String roomCode, long participantId) {
		Subscriber subscriber = new Subscriber(participantId, new SseEmitter(CONNECTION_TIMEOUT_MILLIS));
		RoomChannel channel = channels.add(roomCode, subscriber);
		SseEmitter emitter = subscriber.emitter();
		emitter.onCompletion(() -> channels.remove(roomCode, subscriber));
		// 시간 초과 때 직접 닫지 않으면 Spring이 이미 응답 중인 연결에 에러 응답을 쓰려다 경고를 남긴다.
		emitter.onTimeout(emitter::complete);
		emitter.onError(error -> channels.remove(roomCode, subscriber));

		synchronized (channel) {
			if (channel.connectedCountChanged()) {
				// 새 사람이 방 화면을 열어서 "지금 접속 중"이 늘었다. 모두에게 보낸다(새 연결도 이걸로 현재 화면을 받는다).
				sendToAll(roomCode, channel);
			}
			else {
				// 이미 접속 중인 사람이 탭을 하나 더 열었거나 다시 연결했다. 인원은 그대로라 새 연결에만 보낸다.
				roomStateReader.read(roomCode)
					.flatMap(views -> views.viewOf(participantId))
					.ifPresent(view -> send(roomCode, subscriber, view));
			}
		}
		return emitter;
	}

	/**
	 * 방 상태를 바꾼 트랜잭션이 커밋된 뒤에만 보낸다. 커밋 전에 보내면 롤백된 상태를 보여줄 수 있다.
	 * 이미 커밋된 요청이 실패한 것처럼 보이지 않도록, 여기서 생기는 예외는 밖으로 던지지 않는다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRoomChanged(RoomChangedEvent event) {
		try {
			broadcast(event.roomCode());
		}
		catch (RuntimeException e) {
			log.warn("방 {} 상태를 SSE로 보내지 못했다", event.roomCode(), e);
		}
	}

	/** 방의 모든 연결에 최신 화면 정보를 보낸다. */
	public void broadcast(String roomCode) {
		channels.find(roomCode).ifPresent(channel -> {
			synchronized (channel) {
				sendToAll(roomCode, channel);
			}
		});
	}

	/**
	 * 오래 조용한 연결은 중간의 프록시가 끊을 수 있어서, 10초마다 내용 없는 주석 줄을 보낸다.
	 * 서버는 끊긴 연결에 무언가를 보내 봐야 끊긴 것을 알 수 있어서, 끊긴 연결도 이때 정리된다.
	 * 정리해서 "지금 접속 중"이 줄었으면 남은 사람들에게 바뀐 인원을 알린다.
	 * 간격이 짧을수록 탭을 닫은 사람이 "지금 접속 중"에서 빨리 빠진다. 보내는 양은 연결마다 10초에 한 줄이라 아주 적다.
	 */
	@Scheduled(fixedRate = HEARTBEAT_SECONDS, timeUnit = TimeUnit.SECONDS)
	public void sendHeartbeat() {
		channels.forEach((roomCode, channel) -> {
			synchronized (channel) {
				for (Subscriber subscriber : channel.subscribers()) {
					try {
						subscriber.emitter().send(SseEmitter.event().comment("heartbeat"));
					}
					catch (IOException | IllegalStateException e) {
						channels.remove(roomCode, subscriber);
					}
				}
				if (channel.connectedCountChanged()) {
					sendToAll(roomCode, channel);
				}
			}
		});
	}

	/**
	 * 방 상태와 모든 사람의 정보를 한 번만 읽고, 연결마다 그 연결 주인의 정보를 붙여 보낸다. 채널로 동기화한 채로 불러야 한다.
	 * 보내다가 끊긴 연결이 나와서 인원이 바뀌면, 다음 하트비트 때 바뀐 인원을 다시 보낸다.
	 */
	private void sendToAll(String roomCode, RoomChannel channel) {
		roomStateReader.read(roomCode).ifPresent(views -> {
			for (Subscriber subscriber : channel.subscribers()) {
				views.viewOf(subscriber.participantId()).ifPresent(view -> send(roomCode, subscriber, view));
			}
			channel.markSent(views.room().connectedCount());
		});
	}

	private void send(String roomCode, Subscriber subscriber, RoomView view) {
		try {
			subscriber.emitter().send(SseEmitter.event().name(ROOM_EVENT).data(view, MediaType.APPLICATION_JSON));
		}
		catch (IOException | IllegalStateException e) {
			// 브라우저가 이미 떠났거나 연결이 닫힌 경우다.
			channels.remove(roomCode, subscriber);
		}
	}

}
