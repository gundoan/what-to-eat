package com.whattoeat.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.whattoeat.IntegrationTest;
import com.whattoeat.room.web.RoomApiClient.VotingRoom;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 재투표: 첫 투표가 동점이면 동점 메뉴만 놓고 한 번 더 투표한다. 규칙과 시간은 투표와 같다.
 * 또 동점이면 재투표의 1등끼리 중에서, 아무도 안 찍으면 동점 메뉴 전체 중에서 서버가 무작위로 정한다.
 */
@IntegrationTest
class RevoteApiTest {

	@Autowired
	MockMvc mockMvc;

	RoomApiClient api;

	@BeforeEach
	void setUp() {
		api = new RoomApiClient(mockMvc);
	}

	@Test
	@DisplayName("투표가 동점이면 재투표가 되고, 동점 메뉴만 후보다. 첫 투표 점수는 공개되고 재투표 점수는 숨겨진다")
	void tieStartsRevoteWithTiedMenusOnly() throws Exception {
		VotingRoom room = tiedRoom();

		api.getRoom(room.code(), room.person(2))
			.andExpect(jsonPath("$.room.status").value("REVOTING"))
			.andExpect(jsonPath("$.room.phaseEndsAt", notNullValue()))
			.andExpect(jsonPath("$.room.voteCandidates[*].name", contains("김밥", "라면")))
			.andExpect(jsonPath("$.room.voteScores", hasSize(3)))
			.andExpect(jsonPath("$.room.revoteScores", empty()))
			.andExpect(jsonPath("$.room.doneCount").value(0))
			.andExpect(jsonPath("$.me.ballot", nullValue()));
	}

	@Test
	@DisplayName("재투표에서 동점이 아니었던 메뉴에는 표를 줄 수 없다")
	void rejectsNonCandidates() throws Exception {
		VotingRoom room = tiedRoom();

		api.vote(room.code(), room.person(0), Map.of(menuId(room, "짜장면"), 1))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NOT_A_CANDIDATE"));
	}

	@Test
	@DisplayName("첫 투표에 낸 사람도 재투표에 다시 내고, 재투표의 1등이 하나면 그 메뉴로 결정된다")
	void revoteDecidesUniqueLeader() throws Exception {
		VotingRoom room = tiedRoom();
		String kimbap = menuId(room, "김밥");

		api.vote(room.code(), room.person(0), Map.of(kimbap, 1)).andExpect(jsonPath("$.me.ballot", notNullValue()));
		api.vote(room.code(), room.person(1), Map.of(kimbap, 1));
		api.voteAllGood(room.code(), room.person(2))
			.andExpect(jsonPath("$.room.status").value("FINISHED"))
			.andExpect(jsonPath("$.room.result.name").value("김밥"))
			.andExpect(jsonPath("$.room.result.reason").value("REVOTE"))
			.andExpect(jsonPath("$.room.revoteScores[*].total", contains(2, 0)))
			.andExpect(jsonPath("$.room.voteScores", hasSize(3)));
	}

	@Test
	@DisplayName("재투표도 동점이면 재투표의 1등끼리 중에서 무작위로 정하고, 재투표에서 밀린 메뉴는 뽑히지 않는다")
	void tieAgainPicksAmongRevoteLeaders() throws Exception {
		for (int trial = 0; trial < 5; trial++) {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");
			String kimbap = menuId(room, "김밥");
			String ramen = menuId(room, "라면");
			String jjajang = menuId(room, "짜장면");
			api.vote(room.code(), room.person(0), Map.of(kimbap, 1));
			api.vote(room.code(), room.person(1), Map.of(ramen, 1));
			api.vote(room.code(), room.person(2), Map.of(jjajang, 1))
				.andExpect(jsonPath("$.room.voteCandidates", hasSize(3)));

			api.vote(room.code(), room.person(0), Map.of(kimbap, 1));
			api.vote(room.code(), room.person(1), Map.of(ramen, 1));
			String body = api.vote(room.code(), room.person(2), Map.of(jjajang, -1))
				.andExpect(jsonPath("$.room.result.reason").value("REVOTE_RANDOM"))
				.andReturn()
				.getResponse()
				.getContentAsString();

			assertThat(JsonPath.<String>read(body, "$.room.result.name")).isIn("김밥", "라면");
		}
	}

	@Test
	@DisplayName("재투표에서 아무도 표를 내지 않으면 동점 메뉴 전체 중에서 무작위로 정한다")
	void noRevotesPicksAmongCandidates() throws Exception {
		VotingRoom room = tiedRoom();
		for (Cookie person : room.people().subList(0, 2)) {
			api.voteAllGood(room.code(), person);
		}

		String body = api.voteAllGood(room.code(), room.person(2))
			.andExpect(jsonPath("$.room.status").value("FINISHED"))
			.andExpect(jsonPath("$.room.result.reason").value("REVOTE_RANDOM"))
			.andExpect(jsonPath("$.room.revoteScores", empty()))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(JsonPath.<String>read(body, "$.room.result.name")).isIn("김밥", "라면");
	}

	@Test
	@DisplayName("방장이 다음 단계로를 누르면 지금까지 낸 재투표 표로 결정된다")
	void hostEndsRevoteEarly() throws Exception {
		VotingRoom room = tiedRoom();
		api.vote(room.code(), room.person(1), Map.of(menuId(room, "라면"), 1));

		api.nextPhase(room.code(), room.host(), "REVOTING")
			.andExpect(jsonPath("$.room.status").value("FINISHED"))
			.andExpect(jsonPath("$.room.result.name").value("라면"))
			.andExpect(jsonPath("$.room.result.reason").value("REVOTE"));
	}

	@Test
	@DisplayName("재투표 중에 늦게 들어온 사람도 재투표를 할 수 있고, 모두 끝냈는지 셀 때 포함된다")
	void lateJoinerCanRevote() throws Exception {
		VotingRoom room = tiedRoom();
		Cookie lateComer = api.join(room.code());
		String ramen = menuId(room, "라면");

		for (Cookie person : room.people()) {
			api.vote(room.code(), person, Map.of(ramen, 1));
		}
		api.getRoom(room.code(), lateComer)
			.andExpect(jsonPath("$.room.status").value("REVOTING"))
			.andExpect(jsonPath("$.room.doneCount").value(3))
			.andExpect(jsonPath("$.room.participantCount").value(4));
		api.vote(room.code(), lateComer, Map.of(ramen, -1)).andExpect(jsonPath("$.room.result.name").value("라면"));
	}

	/** 김밥과 라면이 1점씩 동점이라 재투표가 된 방. 메뉴는 김밥, 라면, 짜장면이다. */
	private VotingRoom tiedRoom() throws Exception {
		VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");
		api.vote(room.code(), room.person(0), Map.of(menuId(room, "김밥"), 1));
		api.vote(room.code(), room.person(1), Map.of(menuId(room, "라면"), 1));
		api.voteAllGood(room.code(), room.person(2)).andExpect(jsonPath("$.room.status").value("REVOTING"));
		return room;
	}

	private String menuId(VotingRoom room, String name) throws Exception {
		return api.menuIdNamed(room.code(), room.host(), name);
	}

}
