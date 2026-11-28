package com.whattoeat.room.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import com.whattoeat.IntegrationTest;
import com.whattoeat.room.web.ParticipantCookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * SSE는 연결을 열어둔 채로 데이터가 계속 오는 방식이라, 실제 서버에 실제 HTTP로 연결해서 확인한다.
 */
@IntegrationTest
class RoomEventsTest {

	private static final Pattern CODE = Pattern.compile("\"code\":\"([0-9A-Za-z]{10})\"");

	private static final Duration WAIT = Duration.ofSeconds(10);

	@LocalServerPort
	int port;

	@Autowired
	RoomEventBroadcaster broadcaster;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	@DisplayName("SSE로 연결하면 현재 화면 정보를 바로 받고, 누가 들어오면 바뀐 인원 수를 받는다")
	void receivesCurrentStateThenUpdatesWhenSomeoneJoins() throws Exception {
		HttpResponse<String> created = post("/api/rooms", null);
		String code = codeOf(created.body());
		String hostCookie = cookieOf(created);

		try (SseTestConnection events = connect(code, hostCookie)) {
			String initial = events.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			assertThat(JsonPath.<String>read(initial, "$.room.code")).isEqualTo(code);
			assertThat(JsonPath.<Integer>read(initial, "$.room.participantCount")).isEqualTo(1);

			HttpResponse<String> joined = post("/api/rooms/" + code + "/participants", null);
			assertThat(joined.statusCode()).isEqualTo(201);

			String updated = events.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			assertThat(JsonPath.<Integer>read(updated, "$.room.participantCount")).isEqualTo(2);
			assertThat(connectedCountOf(updated)).as("참여는 했지만 아직 방 화면(SSE)을 열지 않았다").isEqualTo(1);
		}
	}

	@Test
	@DisplayName("다른 사람이 방 화면을 열면 모두가 '지금 접속 중'이 늘어난 것을 받고, 닫으면 줄어든 것을 받는다. 참여 인원은 그대로다")
	void connectedCountFollowsConnections() throws Exception {
		HttpResponse<String> created = post("/api/rooms", null);
		String code = codeOf(created.body());
		String hostCookie = cookieOf(created);
		String guestCookie = cookieOf(post("/api/rooms/" + code + "/participants", null));

		try (SseTestConnection hostEvents = connect(code, hostCookie)) {
			assertThat(connectedCountOf(hostEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT))).isEqualTo(1);

			try (SseTestConnection guestEvents = connect(code, guestCookie)) {
				assertThat(connectedCountOf(guestEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT)))
					.isEqualTo(2);
				assertThat(connectedCountOf(hostEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT)))
					.isEqualTo(2);
			} // 손님이 방 화면을 닫는다.

			String afterLeave = nextEventAfterHeartbeats(hostEvents);
			assertThat(connectedCountOf(afterLeave)).isEqualTo(1);
			assertThat(JsonPath.<Integer>read(afterLeave, "$.room.participantCount")).as("참여 인원은 줄지 않는다").isEqualTo(2);
		}
	}

	@Test
	@DisplayName("하트비트는 화면의 자바스크립트가 받을 수 있는 'ping' 이벤트로 온다(주석 줄은 자바스크립트가 볼 수 없다)")
	void heartbeatIsANamedEvent() throws Exception {
		HttpResponse<String> created = post("/api/rooms", null);
		String code = codeOf(created.body());

		try (SseTestConnection events = connect(code, cookieOf(created))) {
			events.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);

			broadcaster.sendHeartbeat();

			assertThat(events.nextEventData(RoomEventBroadcaster.HEARTBEAT_EVENT, WAIT)).isEqualTo("ping");
		}
	}

	@Test
	@DisplayName("한 사람이 방 화면을 여러 개(탭) 열어도 '지금 접속 중'은 한 명으로 센다")
	void countsOnePersonOnceEvenWithManyTabs() throws Exception {
		HttpResponse<String> created = post("/api/rooms", null);
		String code = codeOf(created.body());
		String hostCookie = cookieOf(created);
		String guestCookie = cookieOf(post("/api/rooms/" + code + "/participants", null));

		try (SseTestConnection firstTab = connect(code, hostCookie);
				SseTestConnection secondTab = connect(code, hostCookie);
				SseTestConnection guestEvents = connect(code, guestCookie)) {
			assertThat(connectedCountOf(firstTab.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT))).isEqualTo(1);
			assertThat(connectedCountOf(secondTab.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT))).isEqualTo(1);
			assertThat(connectedCountOf(guestEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT))).isEqualTo(2);
		}
	}

	@Test
	@DisplayName("요청에 대한 응답에도 '지금 접속 중'이 있고, 요청한 사람은 아직 방 화면(SSE)을 열기 전이어도 접속 중으로 센다")
	void responseCountsRequesterAsConnected() throws Exception {
		HttpResponse<String> created = post("/api/rooms", null);
		String code = codeOf(created.body());

		try (SseTestConnection hostEvents = connect(code, cookieOf(created))) {
			hostEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);

			// 새로 들어온 사람은 참여 응답을 받은 뒤에 SSE로 연결한다. 그 응답에 자기를 뺀 인원이 보이면 안 된다.
			HttpResponse<String> joined = post("/api/rooms/" + code + "/participants", null);
			assertThat(connectedCountOf(joined.body())).isEqualTo(2);
		}
	}

	@Test
	@DisplayName("SSE로 받는 내 정보(me)는 연결을 연 사람 본인 것이고, 다른 사람의 정보는 오지 않는다")
	void eachConnectionReceivesOnlyItsOwnInfo() throws Exception {
		HttpResponse<String> created = post("/api/rooms", null);
		String code = codeOf(created.body());
		String hostCookie = cookieOf(created);
		String guestCookie = cookieOf(post("/api/rooms/" + code + "/participants", null));
		assertThat(send("POST", "/api/rooms/" + code + "/next-phase", hostCookie, "{\"from\": \"WAITING\"}")
			.statusCode()).isEqualTo(200);

		try (SseTestConnection hostEvents = connect(code, hostCookie);
				SseTestConnection guestEvents = connect(code, guestCookie)) {
			hostEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT); // 연결 직후의 현재 상태
			guestEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			// 손님이 방 화면을 열어서 "지금 접속 중"이 2명이 된 것을 방장도 받는다.
			assertThat(connectedCountOf(hostEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT))).isEqualTo(2);

			assertThat(send("PUT", "/api/rooms/" + code + "/me/proposal", guestCookie, "{\"menuName\": \"김밥\"}")
				.statusCode()).isEqualTo(200);

			String toHost = hostEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			String toGuest = guestEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			// 방 상태는 둘 다 같다.
			assertThat(JsonPath.<List<String>>read(toHost, "$.room.menus[*].name")).containsExactly("김밥");
			assertThat(JsonPath.<List<String>>read(toGuest, "$.room.menus[*].name")).containsExactly("김밥");
			// 내 정보는 각자 자기 것만 온다. 방장 연결에는 손님이 낸 메뉴가, 손님 연결에는 방장이라는 사실이 없다.
			assertThat(JsonPath.<Boolean>read(toHost, "$.me.host")).isTrue();
			assertThat(JsonPath.<String>read(toHost, "$.me.proposal")).isNull();
			assertThat(JsonPath.<Boolean>read(toGuest, "$.me.host")).isFalse();
			assertThat(JsonPath.<String>read(toGuest, "$.me.proposal")).isEqualTo("김밥");
		}
	}

	@Test
	@DisplayName("다른 사람이 마지막으로 답해서 내 그룹의 결과가 나오면, 그 결과와 바뀐 내 정보를 SSE로 받는다")
	void receivesMergeResultCausedBySomeoneElse() throws Exception {
		HttpResponse<String> created = post("/api/rooms", null);
		String code = codeOf(created.body());
		String hostCookie = cookieOf(created);
		String guestCookie = cookieOf(post("/api/rooms/" + code + "/participants", null));
		send("POST", "/api/rooms/" + code + "/next-phase", hostCookie, "{\"from\": \"WAITING\"}");
		send("PUT", "/api/rooms/" + code + "/me/proposal", hostCookie, "{\"menuName\": \"제육볶음\"}");
		String merging = send("PUT", "/api/rooms/" + code + "/me/proposal", guestCookie,
				"{\"menuName\": \"제육 볶음\"}").body();
		String groupId = JsonPath.read(merging, "$.room.mergeGroups[0].id");

		try (SseTestConnection guestEvents = connect(code, guestCookie)) {
			guestEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			send("PUT", "/api/rooms/" + code + "/me/merge-answers/" + groupId, guestCookie, "{\"agree\": true}");
			guestEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT); // 내 답이 반영된 상태

			send("PUT", "/api/rooms/" + code + "/me/merge-answers/" + groupId, hostCookie, "{\"agree\": true}");

			String result = guestEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			assertThat(JsonPath.<String>read(result, "$.room.mergeGroups[0].status")).isEqualTo("MERGED");
			assertThat(JsonPath.<List<Object>>read(result, "$.room.menus")).hasSize(1);
			// 대표로 뽑혔으면 내 메뉴가 남고, 아니면 대표 메뉴에 합쳐졌다는 것을 받는다(대표는 무작위).
			String myMenuId = JsonPath.read(result, "$.me.menuId");
			String mergedInto = JsonPath.read(result, "$.me.mergedInto");
			if (myMenuId == null) {
				assertThat(mergedInto).isEqualTo("제육볶음");
				assertThat(JsonPath.<Boolean>read(result, "$.me.done")).isTrue();
			}
			else {
				assertThat(myMenuId).isEqualTo(JsonPath.read(result, "$.room.menus[0].id"));
				assertThat(mergedInto).isNull();
			}
		}
	}

	@Test
	@DisplayName("직접 합치기 요청을 받으면, 받은 사람의 SSE로 바로 질문이 온다")
	void targetReceivesRequestThroughSse() throws Exception {
		HttpResponse<String> created = post("/api/rooms", null);
		String code = codeOf(created.body());
		String hostCookie = cookieOf(created);
		String guestCookie = cookieOf(post("/api/rooms/" + code + "/participants", null));
		send("POST", "/api/rooms/" + code + "/next-phase", hostCookie, "{\"from\": \"WAITING\"}");
		send("PUT", "/api/rooms/" + code + "/me/proposal", hostCookie, "{\"menuName\": \"할매집 제육\"}");
		String merging = send("PUT", "/api/rooms/" + code + "/me/proposal", guestCookie,
				"{\"menuName\": \"제육볶음\"}").body();
		String guestMenuId = JsonPath.read(merging, "$.me.menuId");

		try (SseTestConnection guestEvents = connect(code, guestCookie)) {
			guestEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);

			assertThat(send("POST", "/api/rooms/" + code + "/me/merge-requests", hostCookie,
					"{\"targetMenuId\": \"" + guestMenuId + "\"}")
				.statusCode()).isEqualTo(200);

			String received = guestEvents.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			assertThat(JsonPath.<String>read(received, "$.room.mergeGroups[0].kind")).isEqualTo("DIRECT");
			assertThat(JsonPath.<String>read(received, "$.me.pendingGroupId"))
				.isEqualTo(JsonPath.read(received, "$.room.mergeGroups[0].id"));
		}
	}

	@Test
	@DisplayName("랜덤 선택이 되면 뽑힌 사람의 SSE에만 '내가 뽑혔다'가 온다")
	void onlyPickerIsToldThroughSse() throws Exception {
		HttpResponse<String> created = post("/api/rooms", null);
		String code = codeOf(created.body());
		String hostCookie = cookieOf(created);
		String guestCookie = cookieOf(post("/api/rooms/" + code + "/participants", null));
		send("POST", "/api/rooms/" + code + "/next-phase", hostCookie, "{\"from\": \"WAITING\"}");

		try (SseTestConnection hostEvents = connect(code, hostCookie);
				SseTestConnection guestEvents = connect(code, guestCookie)) {
			send("POST", "/api/rooms/" + code + "/me/all-good", hostCookie, "{}");
			send("POST", "/api/rooms/" + code + "/me/all-good", guestCookie, "{}");

			String toHost = nextEventWithStatus(hostEvents, "RANDOM_PICK");
			String toGuest = nextEventWithStatus(guestEvents, "RANDOM_PICK");
			boolean hostPicked = JsonPath.read(toHost, "$.me.picking");
			boolean guestPicked = JsonPath.read(toGuest, "$.me.picking");
			assertThat(hostPicked ^ guestPicked).as("둘 중 정확히 한 사람만 뽑혔다는 정보를 받는다").isTrue();
			assertThat(JsonPath.<Integer>read(toGuest, "$.room.pickTurn")).isEqualTo(1);
		}
	}

	@Test
	@DisplayName("방 참여자가 아니면 SSE에 연결할 수 없다")
	void rejectsNonParticipant() throws Exception {
		String code = codeOf(post("/api/rooms", null).body());

		HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/rooms/" + code + "/events"))
			.header("Accept", "text/event-stream")
			.timeout(WAIT)
			.GET()
			.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(403);
	}

	private SseTestConnection connect(String code, String cookie) throws Exception {
		return SseTestConnection.open(http, uri("/api/rooms/" + code + "/events"), cookie);
	}

	/** 방 상태가 status인 이벤트가 올 때까지 앞의 이벤트를 건너뛴다. */
	private static String nextEventWithStatus(SseTestConnection events, String status) throws Exception {
		for (int i = 0; i < 10; i++) {
			String data = events.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			if (status.equals(JsonPath.read(data, "$.room.status"))) {
				return data;
			}
		}
		throw new AssertionError("상태가 " + status + "인 이벤트를 받지 못했다");
	}

	/**
	 * 서버는 끊긴 연결에 무언가를 보내 봐야 끊긴 것을 안다(한두 번은 보내기가 성공한 것처럼 보일 수 있다).
	 * 하트비트를 기다리지 않고 직접 보내면서, 끊긴 연결을 정리한 뒤의 이벤트를 기다린다.
	 */
	private String nextEventAfterHeartbeats(SseTestConnection events) throws Exception {
		for (int i = 0; i < 20; i++) {
			broadcaster.sendHeartbeat();
			Optional<String> data = events.pollEventData(RoomEventBroadcaster.ROOM_EVENT, Duration.ofMillis(250));
			if (data.isPresent()) {
				return data.get();
			}
		}
		throw new AssertionError("끊긴 연결을 정리한 뒤의 이벤트를 받지 못했다");
	}

	private static int connectedCountOf(String json) {
		return JsonPath.read(json, "$.room.connectedCount");
	}

	private HttpResponse<String> post(String path, String cookie) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
			.timeout(WAIT)
			.POST(HttpRequest.BodyPublishers.noBody());
		if (cookie != null) {
			request.header("Cookie", cookie);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> send(String method, String path, String cookie, String json) throws Exception {
		return http.send(HttpRequest.newBuilder(uri(path))
			.timeout(WAIT)
			.header("Cookie", cookie)
			.header("Content-Type", "application/json")
			.method(method, HttpRequest.BodyPublishers.ofString(json))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private static String codeOf(String body) {
		Matcher matcher = CODE.matcher(body);
		assertThat(matcher.find()).as("응답에 방 코드가 있어야 한다: " + body).isTrue();
		return matcher.group(1);
	}

	/** Set-Cookie 헤더에서 "이름=값" 부분만 꺼내 Cookie 헤더로 다시 보낼 수 있게 한다. */
	private static String cookieOf(HttpResponse<String> response) {
		String setCookie = response.headers().firstValue("Set-Cookie").orElseThrow();
		assertThat(setCookie).startsWith(ParticipantCookie.NAME + "=");
		return setCookie.substring(0, setCookie.indexOf(';'));
	}

}
