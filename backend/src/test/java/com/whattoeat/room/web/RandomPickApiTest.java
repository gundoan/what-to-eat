package com.whattoeat.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jayway.jsonpath.JsonPath;
import com.whattoeat.IntegrationTest;
import com.whattoeat.room.web.RoomApiClient.TestRoom;
import com.whattoeat.room.web.RoomApiClient.VotingRoom;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 랜덤 선택: 아무도 메뉴를 내지 않았거나 아무도 투표하지 않으면, 서버가 참여자 중 한 명을 무작위로 뽑고 그 사람이 메뉴를 정한다.
 * 뽑힌 사람만 정할 수 있고(넘기기 없음), 시간 안에 안 정하거나 방장이 "다른 사람 뽑기"를 누르면 다시 뽑는다.
 */
@IntegrationTest
class RandomPickApiTest {

	@Autowired
	MockMvc mockMvc;

	RoomApiClient api;

	@BeforeEach
	void setUp() {
		api = new RoomApiClient(mockMvc);
	}

	@Nested
	@DisplayName("랜덤 선택 시작")
	class Start {

		@Test
		@DisplayName("아무도 메뉴를 내지 않으면 랜덤 선택이 되고, 뽑힌 한 사람에게만 정하라는 정보가 간다")
		void nobodyProposedStartsRandomPick() throws Exception {
			VotingRoom room = api.randomPickRoom("{\"randomPickSeconds\": 90}", 3);

			Cookie picker = api.picker(room.code(), room.people());

			String body = api.roomJson(room.code(), picker);
			assertThat(JsonPath.<Integer>read(body, "$.room.pickTurn")).isEqualTo(1);
			Instant endsAt = Instant.parse(JsonPath.read(body, "$.room.phaseEndsAt"));
			Instant serverTime = Instant.parse(JsonPath.read(body, "$.room.serverTime"));
			assertThat(Duration.between(serverTime, endsAt)).isBetween(Duration.ofSeconds(89), Duration.ofSeconds(90));
		}

		@Test
		@DisplayName("투표에서 모두 다 좋음을 눌러도 랜덤 선택이 되고, 한 사람이 뽑힌다")
		void nobodyVotedStartsRandomPick() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면");
			api.voteAllGood(room.code(), room.person(0));
			api.voteAllGood(room.code(), room.person(1)).andExpect(jsonPath("$.room.status").value("RANDOM_PICK"));

			api.picker(room.code(), room.people());
		}

		@Test
		@DisplayName("뽑힌 사람과 다른 사람이 받는 방 상태는 똑같고(누가 뽑혔는지 없음), 뽑혔다는 것은 본인 정보에만 있다")
		void stateRevealsNoPicker() throws Exception {
			VotingRoom room = api.randomPickRoom(null, 2);
			Cookie picker = api.picker(room.code(), room.people());
			Cookie other = room.people().stream().filter(person -> person != picker).findFirst().orElseThrow();

			String toPicker = api.roomJson(room.code(), picker);
			String toOther = api.roomJson(room.code(), other);

			// 서버 시각은 응답을 만든 순간이라 다르므로 빼고 비교한다.
			assertThat(withoutServerTime(JsonPath.read(toPicker, "$.room")))
				.isEqualTo(withoutServerTime(JsonPath.read(toOther, "$.room")));
			assertThat(JsonPath.<Boolean>read(toPicker, "$.me.picking")).isTrue();
			assertThat(JsonPath.<Boolean>read(toOther, "$.me.picking")).isFalse();
		}

	}

	@Nested
	@DisplayName("메뉴 정하기")
	class Decide {

		@Test
		@DisplayName("뽑힌 사람이 정한 이름으로 결정되고, 메뉴 목록에 없는 이름이어도 된다")
		void pickerDecides() throws Exception {
			VotingRoom room = api.randomPickRoom(null, 3);
			Cookie picker = api.picker(room.code(), room.people());

			api.decidePick(room.code(), picker, "  아무거나 국밥  ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.room.status").value("FINISHED"))
				.andExpect(jsonPath("$.room.result.name").value("아무거나 국밥"))
				.andExpect(jsonPath("$.room.result.reason").value("PICKED"))
				.andExpect(jsonPath("$.me.picking").value(false));
			for (Cookie person : room.people()) {
				api.getRoom(room.code(), person).andExpect(jsonPath("$.room.result.name").value("아무거나 국밥"));
			}
		}

		@Test
		@DisplayName("뽑히지 않은 사람은 정할 수 없다")
		void othersCannotDecide() throws Exception {
			VotingRoom room = api.randomPickRoom(null, 3);
			Cookie picker = api.picker(room.code(), room.people());

			for (Cookie person : room.people()) {
				if (person != picker) {
					api.decidePick(room.code(), person, "김밥")
						.andExpect(status().isForbidden())
						.andExpect(jsonPath("$.code").value("NOT_PICKER"));
				}
			}
		}

		@Test
		@DisplayName("이름은 제안과 같은 규칙(앞뒤 공백을 뺀 1~30자)이다")
		void validatesName() throws Exception {
			VotingRoom room = api.randomPickRoom(null, 1);

			api.decidePick(room.code(), room.host(), "   ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_MENU_NAME"));
			api.decidePick(room.code(), room.host(), "가".repeat(31)).andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("랜덤 선택이 아니면 메뉴를 정할 수 없다")
		void rejectsOutsideRandomPick() throws Exception {
			TestRoom room = api.createRoom(null);

			api.decidePick(room.code(), room.host(), "김밥")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_RANDOM_PICK_PHASE"));
		}

	}

	@Nested
	@DisplayName("다시 뽑기")
	class Redraw {

		@Test
		@DisplayName("방장이 다른 사람 뽑기를 누르면 다른 사람이 뽑히고, 두 번 눌러도 한 번만 다시 뽑는다")
		void hostSkipsOnce() throws Exception {
			VotingRoom room = api.randomPickRoom(null, 3);
			Cookie first = api.picker(room.code(), room.people());

			api.pickSomeoneElse(room.code(), room.host(), 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.room.pickTurn").value(2));
			api.pickSomeoneElse(room.code(), room.host(), 1)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PHASE_ALREADY_CHANGED"));

			Cookie second = api.picker(room.code(), room.people());
			assertThat(second).as("한 바퀴 안에서는 이미 뽑힌 사람을 다시 뽑지 않는다").isNotSameAs(first);
			api.getRoom(room.code(), room.host()).andExpect(jsonPath("$.room.pickTurn").value(2));
		}

		@Test
		@DisplayName("다른 사람 뽑기는 화면에 보이던 차례 번호가 있어야 하고, 방장만 할 수 있다")
		void skipNeedsTurnNumberAndHost() throws Exception {
			VotingRoom room = api.randomPickRoom(null, 2);

			mockMvc.perform(post("/api/rooms/" + room.code() + "/next-phase").cookie(room.host())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"from\": \"RANDOM_PICK\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
			api.pickSomeoneElse(room.code(), room.person(1), 1)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_HOST"));
		}

		@Test
		@DisplayName("한 바퀴 안에서는 모두 한 번씩 뽑히고, 다 돌면 처음부터 다시 뽑는다")
		void cyclesThroughEveryone() throws Exception {
			VotingRoom room = api.randomPickRoom(null, 3);
			Set<String> pickedInFirstCycle = new HashSet<>();

			for (int turn = 1; turn <= 3; turn++) {
				pickedInFirstCycle.add(api.picker(room.code(), room.people()).getValue());
				api.pickSomeoneElse(room.code(), room.host(), turn).andExpect(status().isOk());
			}

			assertThat(pickedInFirstCycle).as("세 번 뽑는 동안 세 사람이 한 번씩 뽑혔다").hasSize(3);
			api.getRoom(room.code(), room.host()).andExpect(jsonPath("$.room.pickTurn").value(4));
			api.picker(room.code(), room.people()); // 2바퀴째에도 누군가 뽑혀 있다
		}

		@Test
		@DisplayName("랜덤 선택 중에도 들어올 수 있고, 들어온 사람은 다음 뽑기 후보가 된다")
		void lateJoinerCanBePicked() throws Exception {
			VotingRoom room = api.randomPickRoom(null, 1);
			Cookie lateComer = api.join(room.code());

			// 방장은 이번 바퀴에 이미 뽑혔으므로, 다음 뽑기에서는 늦게 온 사람만 남아 있다.
			api.pickSomeoneElse(room.code(), room.host(), 1).andExpect(status().isOk());

			api.getRoom(room.code(), lateComer).andExpect(jsonPath("$.me.picking").value(true));
			api.decidePick(room.code(), lateComer, "떡볶이").andExpect(jsonPath("$.room.result.name").value("떡볶이"));
		}

	}

	private static Map<String, Object> withoutServerTime(Map<String, Object> room) {
		Map<String, Object> copy = new HashMap<>(room);
		copy.remove("serverTime");
		return copy;
	}

}
