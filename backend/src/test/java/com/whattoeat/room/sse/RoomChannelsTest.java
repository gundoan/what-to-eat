package com.whattoeat.room.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattoeat.room.sse.RoomChannels.RoomChannel;
import com.whattoeat.room.sse.RoomChannels.Subscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RoomChannelsTest {

	private static final String ROOM = "AbCdE12345";

	private final RoomChannels channels = new RoomChannels();

	@Test
	@DisplayName("한 사람이 연결(탭)을 여러 개 열어도 한 명으로 세고, 그 사람의 연결이 모두 끊겨야 접속 중에서 빠진다")
	void countsEachParticipantOnce() {
		Subscriber firstTab = subscriber(1);
		Subscriber secondTab = subscriber(1);
		channels.add(ROOM, firstTab);
		channels.add(ROOM, secondTab);
		channels.add(ROOM, subscriber(2));

		assertThat(channels.connectedParticipantIds(ROOM)).containsExactlyInAnyOrder(1L, 2L);

		channels.remove(ROOM, firstTab);
		assertThat(channels.connectedParticipantIds(ROOM)).as("다른 탭이 아직 열려 있다").containsExactlyInAnyOrder(1L, 2L);

		channels.remove(ROOM, secondTab);
		assertThat(channels.connectedParticipantIds(ROOM)).containsExactly(2L);
	}

	@Test
	@DisplayName("접속 중인 사람은 방마다 따로 센다")
	void countsPerRoom() {
		channels.add(ROOM, subscriber(1));
		channels.add("ZzZzZ99999", subscriber(2));

		assertThat(channels.connectedParticipantIds(ROOM)).containsExactly(1L);
		assertThat(channels.connectedParticipantIds("NoOne00000")).isEmpty();
	}

	@Test
	@DisplayName("모두가 마지막으로 받은 접속 중 인원과 지금 인원이 다를 때만 다시 보내야 한다")
	void tellsWhenConnectedCountChanged() {
		RoomChannel channel = channels.add(ROOM, subscriber(1));
		assertThat(channel.connectedCountChanged()).as("아직 아무것도 보내지 않았다").isTrue();

		channel.markSent(1);
		assertThat(channel.connectedCountChanged()).isFalse();

		channels.add(ROOM, subscriber(1));
		assertThat(channel.connectedCountChanged()).as("같은 사람이 탭을 하나 더 열었을 뿐이다").isFalse();

		channels.add(ROOM, subscriber(2));
		assertThat(channel.connectedCountChanged()).as("새 사람이 들어왔다").isTrue();
	}

	private static Subscriber subscriber(long participantId) {
		return new Subscriber(participantId, new SseEmitter());
	}

}
