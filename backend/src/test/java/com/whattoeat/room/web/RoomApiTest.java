package com.whattoeat.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import com.whattoeat.IntegrationTest;
import com.whattoeat.room.domain.ParticipantToken;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class RoomApiTest {

	private static final Pattern CODE = Pattern.compile("\"code\":\"([0-9A-Za-z]{10})\"");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Nested
	@DisplayName("방 만들기")
	class CreateRoom {

		@Test
		@DisplayName("시간을 보내지 않으면 제안, 합치기, 투표는 5분, 랜덤 선택은 1분이고, 만든 사람이 방장이다")
		void createsRoomWithDefaultsAndCreatorIsHost() throws Exception {
			CreatedRoom room = createRoom(null);

			mockMvc.perform(get("/api/rooms/" + room.code()).cookie(room.cookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.room.status").value("WAITING"))
				.andExpect(jsonPath("$.room.phaseDurations.proposalSeconds").value(300))
				.andExpect(jsonPath("$.room.phaseDurations.mergeSeconds").value(300))
				.andExpect(jsonPath("$.room.phaseDurations.voteSeconds").value(300))
				.andExpect(jsonPath("$.room.phaseDurations.randomPickSeconds").value(60))
				.andExpect(jsonPath("$.room.participantCount").value(1))
				.andExpect(jsonPath("$.me.host").value(true));
		}

		@Test
		@DisplayName("방장이 정한 단계별 시간을 저장한다")
		void savesChosenDurations() throws Exception {
			CreatedRoom room = createRoom("""
					{"proposalSeconds": 30, "mergeSeconds": 3600, "voteSeconds": 90, "randomPickSeconds": 120}
					""");

			mockMvc.perform(get("/api/rooms/" + room.code()).cookie(room.cookie()))
				.andExpect(jsonPath("$.room.phaseDurations.proposalSeconds").value(30))
				.andExpect(jsonPath("$.room.phaseDurations.mergeSeconds").value(3600))
				.andExpect(jsonPath("$.room.phaseDurations.voteSeconds").value(90))
				.andExpect(jsonPath("$.room.phaseDurations.randomPickSeconds").value(120));
		}

		@Test
		@DisplayName("0초는 \"제한 없음\"으로 저장되고, 시작해도 끝나는 시각이 없다")
		void savesNoLimitAndStartsWithoutDeadline() throws Exception {
			CreatedRoom room = createRoom("""
					{"proposalSeconds": 0, "mergeSeconds": 0, "voteSeconds": 0, "randomPickSeconds": 0}
					""");

			mockMvc.perform(post("/api/rooms/" + room.code() + "/next-phase").cookie(room.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"from\": \"WAITING\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.room.status").value("PROPOSING"))
				.andExpect(jsonPath("$.room.phaseDurations.proposalSeconds").value(0))
				.andExpect(jsonPath("$.room.phaseDurations.randomPickSeconds").value(0))
				.andExpect(jsonPath("$.room.phaseEndsAt", nullValue()))
				// API에는 초만 보낸다. 서버 코드의 도우미(proposalLimit 등)는 나가지 않는다.
				.andExpect(jsonPath("$.room.phaseDurations.proposalLimit").doesNotExist());
		}

		@ParameterizedTest(name = "{0}초는 거절한다")
		@ValueSource(ints = { 29, 3601, 45 })
		@DisplayName("단계 시간이 규칙(30초~60분, 30초 단위)을 벗어나면 400으로 거절한다")
		void rejectsInvalidDuration(int seconds) throws Exception {
			mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
				.content("{\"voteSeconds\": " + seconds + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PHASE_DURATION"));
		}

		@Test
		@DisplayName("DB도 단계 시간 규칙을 지킨다: 0초(제한 없음)와 60분까지는 받고, 규칙에 없는 시간은 막는다")
		void databaseFollowsPhaseDurationRules() throws Exception {
			CreatedRoom room = createRoom(null);

			jdbcTemplate.update("update room set proposal_seconds = 0, merge_seconds = 3600 where code = ?",
					room.code());

			assertThat(jdbcTemplate.queryForObject("select proposal_seconds from room where code = ?", Integer.class,
					room.code()))
				.isZero();
			assertThat(jdbcTemplate.queryForObject("select merge_seconds from room where code = ?", Integer.class,
					room.code()))
				.isEqualTo(3600);
			assertThatThrownBy(
					() -> jdbcTemplate.update("update room set vote_seconds = 15 where code = ?", room.code()))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("chk_room_vote_seconds");
			assertThatThrownBy(
					() -> jdbcTemplate.update("update room set random_pick_seconds = 3630 where code = ?", room.code()))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("chk_room_random_pick_seconds");
		}

		@Test
		@DisplayName("요청 형식이 깨져 있으면 400으로 거절한다")
		void rejectsMalformedJson() throws Exception {
			mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content("{\"voteSeconds\": \"1분\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}

		@Test
		@DisplayName("참여자 쿠키는 HttpOnly이고 그 방 주소로만 보내진다")
		void issuesHttpOnlyCookieScopedToRoom() throws Exception {
			MvcResult result = mockMvc.perform(post("/api/rooms"))
				.andExpect(status().isCreated())
				.andReturn();
			String code = codeOf(result);
			String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

			assertThat(setCookie).startsWith(ParticipantCookie.NAME + "=")
				.contains("HttpOnly")
				.contains("SameSite=Lax")
				.contains("Path=/api/rooms/" + code);
			assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/rooms/" + code);
		}

		@Test
		@DisplayName("DB에는 토큰 원본이 아니라 해시만 저장한다")
		void storesOnlyTokenHash() throws Exception {
			CreatedRoom room = createRoom(null);
			String rawToken = room.cookie().getValue();

			String stored = jdbcTemplate.queryForObject("""
					select p.token_hash from participant p join room r on p.room_id = r.id where r.code = ?
					""", String.class, room.code());

			assertThat(stored).isNotEqualTo(rawToken).isEqualTo(ParticipantToken.hash(rawToken)).hasSize(64);
		}

	}

	@Nested
	@DisplayName("링크로 참여")
	class Join {

		@Test
		@DisplayName("새로 들어온 사람은 방장이 아닌 참여자가 되고 쿠키를 받는다")
		void newVisitorJoinsAsGuest() throws Exception {
			CreatedRoom room = createRoom(null);

			mockMvc.perform(post("/api/rooms/" + room.code() + "/participants"))
				.andExpect(status().isCreated())
				.andExpect(header().exists(HttpHeaders.SET_COOKIE))
				.andExpect(jsonPath("$.me.host").value(false))
				.andExpect(jsonPath("$.room.participantCount").value(2));
		}

		@Test
		@DisplayName("같은 브라우저(같은 쿠키)로 다시 들어오면 새 참여자를 만들지 않는다 (새로고침해도 같은 참여자)")
		void sameCookieKeepsSameParticipant() throws Exception {
			CreatedRoom room = createRoom(null);
			Cookie guest = join(room.code());

			mockMvc.perform(post("/api/rooms/" + room.code() + "/participants").cookie(guest))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
				.andExpect(jsonPath("$.me.host").value(false))
				.andExpect(jsonPath("$.room.participantCount").value(2));
		}

		@Test
		@DisplayName("방장이 다시 들어와도 방장 그대로다")
		void hostStaysHostOnRejoin() throws Exception {
			CreatedRoom room = createRoom(null);

			mockMvc.perform(post("/api/rooms/" + room.code() + "/participants").cookie(room.cookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.me.host").value(true))
				.andExpect(jsonPath("$.room.participantCount").value(1));
		}

		@Test
		@DisplayName("없는 방이면 404다")
		void unknownRoomIsNotFound() throws Exception {
			mockMvc.perform(post("/api/rooms/NoSuchRoom/participants"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
		}

		@Test
		@DisplayName("방 코드는 대소문자를 구분한다")
		void roomCodeIsCaseSensitive() throws Exception {
			CreatedRoom room = createRoom(null);
			String swappedCase = swapCase(room.code());

			mockMvc.perform(post("/api/rooms/" + swappedCase + "/participants"))
				.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("방장을 포함해 50명이 차면 51번째 사람은 409로 거절한다")
		void rejectsWhenRoomIsFull() throws Exception {
			CreatedRoom room = createRoom(null);
			for (int i = 0; i < 49; i++) {
				join(room.code());
			}

			mockMvc.perform(post("/api/rooms/" + room.code() + "/participants"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ROOM_FULL"));
		}

		@Test
		@DisplayName("메뉴가 결정되어 끝난 방에는 새로 들어올 수 없고, 이미 참여한 사람은 다시 들어와 결과를 볼 수 있다")
		void cannotJoinFinishedRoom() throws Exception {
			CreatedRoom room = createRoom(null);
			mockMvc.perform(post("/api/rooms/" + room.code() + "/next-phase").cookie(room.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"from\": \"WAITING\"}")).andExpect(status().isOk());
			// 혼자 메뉴를 내면 바로 끝나고, 메뉴가 하나라 투표 없이 결정된다.
			mockMvc.perform(put("/api/rooms/" + room.code() + "/me/proposal").cookie(room.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"menuName\": \"kimbap\"}")).andExpect(jsonPath("$.room.status").value("FINISHED"));

			mockMvc.perform(post("/api/rooms/" + room.code() + "/participants"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VOTING_ENDED"));
			mockMvc.perform(post("/api/rooms/" + room.code() + "/participants").cookie(room.cookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.room.result.name").value("kimbap"));
		}

		@Test
		@DisplayName("정원이 찬 방이라도 이미 참여한 사람은 다시 들어올 수 있다")
		void existingParticipantCanRejoinFullRoom() throws Exception {
			CreatedRoom room = createRoom(null);
			for (int i = 0; i < 49; i++) {
				join(room.code());
			}

			mockMvc.perform(post("/api/rooms/" + room.code() + "/participants").cookie(room.cookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.room.participantCount").value(50));
		}

	}

	@Nested
	@DisplayName("참여자 확인과 익명성")
	class ParticipantCheck {

		@Test
		@DisplayName("쿠키 없이 방을 조회하면 403이다")
		void requiresParticipantCookie() throws Exception {
			CreatedRoom room = createRoom(null);

			mockMvc.perform(get("/api/rooms/" + room.code()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));
		}

		@Test
		@DisplayName("다른 방의 쿠키로는 이 방의 참여자로 인정하지 않는다")
		void otherRoomCookieIsNotAccepted() throws Exception {
			CreatedRoom roomA = createRoom(null);
			CreatedRoom roomB = createRoom(null);

			mockMvc.perform(get("/api/rooms/" + roomB.code()).cookie(roomA.cookie()))
				.andExpect(status().isForbidden());
			mockMvc.perform(post("/api/rooms/" + roomB.code() + "/participants").cookie(roomA.cookie()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.me.host").value(false));
		}

		@Test
		@DisplayName("응답에는 방 상태와 본인 정보만 있고, 다른 참여자나 누가 방장인지는 없다")
		void responseRevealsNoOneElse() throws Exception {
			CreatedRoom room = createRoom(null);
			Cookie guest = join(room.code());

			String body = mockMvc.perform(get("/api/rooms/" + room.code()).cookie(guest))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.me.host").value(false))
				.andReturn()
				.getResponse()
				.getContentAsString();

			// 응답에 정해진 항목 말고는 아무것도 없어야 한다. 항목이 하나라도 늘면 이 테스트가 알려준다.
			// connectedCount(지금 접속 중)는 숫자뿐이고 누가 접속 중인지는 없어서 넣어도 된다.
			Map<String, Object> response = JsonPath.read(body, "$");
			Map<String, Object> roomState = JsonPath.read(body, "$.room");
			Map<String, Object> me = JsonPath.read(body, "$.me");
			assertThat(response).containsOnlyKeys("room", "me");
			assertThat(roomState).containsOnlyKeys("code", "status", "phaseDurations", "participantCount",
					"connectedCount", "phaseEndsAt", "serverTime", "menus", "doneCount", "mergeGroups", "voteCandidates",
					"voteScores", "revoteScores", "pickTurn", "result");
			assertThat(me).containsOnlyKeys("host", "proposal", "allGood", "menuId", "mergedInto", "pendingGroupId",
					"sentGroupId", "requestableMenuIds", "ballot", "picking", "done");
		}

	}

	private CreatedRoom createRoom(String body) throws Exception {
		var request = post("/api/rooms");
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		MvcResult result = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
		return new CreatedRoom(codeOf(result), cookieOf(result));
	}

	private Cookie join(String code) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/rooms/" + code + "/participants"))
			.andExpect(status().isCreated())
			.andReturn();
		return cookieOf(result);
	}

	private static String codeOf(MvcResult result) throws Exception {
		Matcher matcher = CODE.matcher(result.getResponse().getContentAsString());
		assertThat(matcher.find()).as("응답에 방 코드가 있어야 한다").isTrue();
		return matcher.group(1);
	}

	private static Cookie cookieOf(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie(ParticipantCookie.NAME);
		assertThat(cookie).as("참여자 쿠키를 받아야 한다").isNotNull();
		return new Cookie(cookie.getName(), cookie.getValue());
	}

	private static String swapCase(String code) {
		StringBuilder swapped = new StringBuilder();
		for (char c : code.toCharArray()) {
			swapped.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
		}
		String result = swapped.toString();
		// 숫자로만 된 코드는 대소문자를 바꿔도 같아지므로, 그런 경우엔 테스트할 수 없다(사실상 일어나지 않음).
		assertThat(result).isNotEqualTo(code);
		return result;
	}

	private record CreatedRoom(String code, Cookie cookie) {
	}

}
