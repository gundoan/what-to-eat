package com.whattoeat.room.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import com.whattoeat.IntegrationTest;
import com.whattoeat.room.web.ParticipantCookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
