package com.whattoeat.room.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	@DisplayName("SSE로 연결하면 현재 방 상태를 바로 받고, 누가 들어오면 바뀐 인원 수를 받는다")
	void receivesCurrentStateThenUpdatesWhenSomeoneJoins() throws Exception {
		HttpResponse<String> created = post("/api/rooms", null);
		String code = codeOf(created.body());
		String hostCookie = cookieOf(created);

		try (SseTestConnection events = SseTestConnection.open(http, uri("/api/rooms/" + code + "/events"),
				hostCookie)) {
			String initial = events.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			assertThat(initial).contains("\"code\":\"" + code + "\"").contains("\"participantCount\":1");

			HttpResponse<String> joined = post("/api/rooms/" + code + "/participants", null);
			assertThat(joined.statusCode()).isEqualTo(201);

			String updated = events.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			assertThat(updated).contains("\"participantCount\":2");
			// 모두에게 같은 내용이 가므로, 누가 방장인지 같은 개인 정보가 들어 있으면 안 된다.
			assertThat(updated).doesNotContain("\"host\"").doesNotContain("\"me\"");
		}
	}

	@Test
	@DisplayName("방장이 시작하면 제안 단계 상태를, 누가 메뉴를 내면 바뀐 메뉴 목록을 SSE로 받는다")
	void receivesPhaseChangeAndMenus() throws Exception {
		HttpResponse<String> created = post("/api/rooms", null);
		String code = codeOf(created.body());
		String hostCookie = cookieOf(created);
		String guestCookie = cookieOf(post("/api/rooms/" + code + "/participants", null));

		try (SseTestConnection events = SseTestConnection.open(http, uri("/api/rooms/" + code + "/events"),
				guestCookie)) {
			events.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT); // 연결 직후의 현재 상태

			assertThat(send("POST", "/api/rooms/" + code + "/next-phase", hostCookie, "{\"from\": \"WAITING\"}")
				.statusCode()).isEqualTo(200);
			String started = events.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			assertThat(started).contains("\"status\":\"PROPOSING\"").contains("\"phaseEndsAt\":\"");

			assertThat(send("PUT", "/api/rooms/" + code + "/me/proposal", hostCookie, "{\"menuName\": \"제육\"}")
				.statusCode()).isEqualTo(200);
			String proposed = events.nextEventData(RoomEventBroadcaster.ROOM_EVENT, WAIT);
			assertThat(proposed).contains("\"menus\":[\"제육\"]").contains("\"doneCount\":1");
			// 누가 냈는지는 SSE에도 없다.
			assertThat(proposed).doesNotContain("\"proposal\"").doesNotContain("\"host\"");
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
