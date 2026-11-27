package com.whattoeat.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.whattoeat.IntegrationTest;
import com.whattoeat.room.web.RoomApiClient.MergingRoom;
import com.whattoeat.room.web.RoomApiClient.VotingRoom;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 투표: 한 사람 최대 2표, 표마다 +1 또는 -1, 한 메뉴에 1표. 제출하거나 "다 좋음"을 누르면 바꿀 수 없다.
 * 모두 끝내면 점수를 세서, 1등이 하나면 그 메뉴로 결정한다.
 */
@IntegrationTest
class VoteApiTest {

	@Autowired
	MockMvc mockMvc;

	RoomApiClient api;

	@BeforeEach
	void setUp() {
		api = new RoomApiClient(mockMvc);
	}

	@Nested
	@DisplayName("투표 단계")
	class VotingPhase {

		@Test
		@DisplayName("합치기가 끝나고 메뉴가 2개 이상 남으면 투표 단계가 되고, 방장이 정한 투표 시간 뒤에 끝난다")
		void startsWithVoteTimer() throws Exception {
			VotingRoom room = api.votingRoom("{\"voteSeconds\": 90}", "김밥", "라면");

			String body = api.roomJson(room.code(), room.host());

			Instant endsAt = Instant.parse(JsonPath.read(body, "$.room.phaseEndsAt"));
			Instant serverTime = Instant.parse(JsonPath.read(body, "$.room.serverTime"));
			assertThat(Duration.between(serverTime, endsAt)).isBetween(Duration.ofSeconds(89), Duration.ofSeconds(90));
		}

		@Test
		@DisplayName("투표 중에는 점수가 보이지 않고, 몇 명이 끝냈는지만 보인다")
		void scoresAreHiddenWhileVoting() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");
			api.vote(room.code(), room.person(0), Map.of(menuId(room, "김밥"), 1)).andExpect(status().isOk());

			api.getRoom(room.code(), room.person(1))
				.andExpect(jsonPath("$.room.status").value("VOTING"))
				.andExpect(jsonPath("$.room.voteScores", empty()))
				.andExpect(jsonPath("$.room.doneCount").value(1))
				.andExpect(jsonPath("$.me.ballot", nullValue()));
		}

	}

	@Nested
	@DisplayName("표 내기")
	class Submit {

		@Test
		@DisplayName("1표나 2표를 낼 수 있고, 낸 표는 내 정보에만 보인다")
		void submittedBallotIsOnlyMine() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");
			String kimbap = menuId(room, "김밥");
			String ramen = menuId(room, "라면");

			api.vote(room.code(), room.person(0), Map.of(kimbap, 1, ramen, -1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.me.done").value(true))
				.andExpect(jsonPath("$.me.ballot.allGood").value(false))
				.andExpect(jsonPath("$.me.ballot.votes[*].menuId", containsInAnyOrder(kimbap, ramen)));
			api.vote(room.code(), room.person(1), Map.of(ramen, 1)).andExpect(status().isOk());
		}

		@Test
		@DisplayName("자기가 제안한 메뉴에도 1표를 줄 수 있다")
		void canVoteForOwnMenu() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");

			api.vote(room.code(), room.person(0), Map.of(menuId(room, "김밥"), 1)).andExpect(status().isOk());
		}

		@Test
		@DisplayName("3표, 0표, 한 메뉴에 2표, +1이나 -1이 아닌 점수는 낼 수 없다")
		void rejectsInvalidBallots() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");
			String kimbap = menuId(room, "김밥");
			String ramen = menuId(room, "라면");
			String jjajang = menuId(room, "짜장면");
			Cookie voter = room.person(0);

			assertInvalid(api.vote(room.code(), voter, Map.of(kimbap, 1, ramen, 1, jjajang, -1)));
			assertInvalid(api.ballot(room.code(), voter, "{\"votes\": []}"));
			assertInvalid(api.ballot(room.code(), voter, "{\"votes\": [{\"menuId\": \"" + kimbap
					+ "\", \"point\": 1}, {\"menuId\": \"" + kimbap + "\", \"point\": -1}]}"));
			assertInvalid(api.vote(room.code(), voter, Map.of(kimbap, 2)));
			assertInvalid(api.vote(room.code(), voter, Map.of(kimbap, 0)));
			api.getRoom(room.code(), voter).andExpect(jsonPath("$.me.done").value(false));
		}

		@Test
		@DisplayName("없는 메뉴나 다른 메뉴에 합쳐져 사라진 메뉴에는 표를 줄 수 없다")
		void rejectsUnknownOrMergedMenus() throws Exception {
			MergingRoom merging = api.mergingRoom(null, "제육볶음", "제육 볶음", "김밥");
			String group = api.read(merging.code(), merging.person(0), "$.me.pendingGroupId");
			api.answerMerge(merging.code(), merging.person(0), group, true);
			api.answerMerge(merging.code(), merging.person(1), group, true);
			for (Cookie person : merging.people()) {
				api.finishMerge(merging.code(), person);
			}
			String body = api.roomJson(merging.code(), merging.host());
			assertThat(JsonPath.<String>read(body, "$.room.status")).isEqualTo("VOTING");
			List<String> groupMenuIds = JsonPath.read(body, "$.room.mergeGroups[0].menus[*].id");
			List<String> remainingIds = JsonPath.read(body, "$.room.menus[*].id");
			String mergedAway = groupMenuIds.stream().filter(id -> !remainingIds.contains(id)).findFirst().orElseThrow();

			api.vote(merging.code(), merging.person(2), Map.of(mergedAway, 1))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
			api.vote(merging.code(), merging.person(2), Map.of("no-such-menu", 1)).andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("한 번 낸 표는 바꿀 수 없고, 같은 표를 다시 보내면(두 번 누르기) 그대로 성공한다")
		void ballotCannotBeChanged() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");
			String kimbap = menuId(room, "김밥");
			api.vote(room.code(), room.person(0), Map.of(kimbap, 1)).andExpect(status().isOk());

			api.vote(room.code(), room.person(0), Map.of(kimbap, -1))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_VOTED"));
			api.vote(room.code(), room.person(0), Map.of(kimbap, 1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.room.doneCount").value(1));
		}

		@Test
		@DisplayName("다 좋음을 누르면 투표하지 않은 것이 되고, 다 좋음도 표처럼 바꿀 수 없다")
		void allGoodIsFinalToo() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");
			String kimbap = menuId(room, "김밥");

			api.voteAllGood(room.code(), room.person(0))
				.andExpect(jsonPath("$.me.ballot.allGood").value(true))
				.andExpect(jsonPath("$.me.ballot.votes", empty()))
				.andExpect(jsonPath("$.me.done").value(true));
			api.vote(room.code(), room.person(0), Map.of(kimbap, 1))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_VOTED"));

			api.vote(room.code(), room.person(1), Map.of(kimbap, 1));
			api.voteAllGood(room.code(), room.person(1))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_VOTED"));
		}

		@Test
		@DisplayName("다 좋음과 표를 함께 보내면 거절된다")
		void rejectsAllGoodWithVotes() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면");

			assertInvalid(api.ballot(room.code(), room.person(0),
					"{\"allGood\": true, \"votes\": [{\"menuId\": \"" + menuId(room, "김밥") + "\", \"point\": 1}]}"));
		}

		@Test
		@DisplayName("투표 단계가 아니면 표를 낼 수 없다")
		void rejectsOutsideVotingPhase() throws Exception {
			MergingRoom room = api.mergingRoom(null, "김밥", "라면");

			api.voteAllGood(room.code(), room.person(0))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_VOTING_PHASE"));
		}

		@Test
		@DisplayName("투표 중에 늦게 들어온 사람도 투표할 수 있고, 모두 끝냈는지 셀 때 포함된다")
		void lateJoinerCanVote() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면");
			Cookie lateComer = api.join(room.code());
			String ramen = menuId(room, "라면");

			api.vote(room.code(), room.person(0), Map.of(ramen, 1));
			api.vote(room.code(), room.person(1), Map.of(ramen, 1))
				.andExpect(jsonPath("$.room.status").value("VOTING"))
				.andExpect(jsonPath("$.room.doneCount").value(2))
				.andExpect(jsonPath("$.room.participantCount").value(3));
			api.vote(room.code(), lateComer, Map.of(ramen, -1)).andExpect(jsonPath("$.room.status").value("FINISHED"));
		}

	}

	@Nested
	@DisplayName("결과")
	class Result {

		@Test
		@DisplayName("모두 내면 시간이 남아 있어도 바로 점수를 세고, 1등이 하나면 그 메뉴로 결정된다")
		void decidesUniqueLeader() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");
			String kimbap = menuId(room, "김밥");
			String ramen = menuId(room, "라면");
			String jjajang = menuId(room, "짜장면");

			api.vote(room.code(), room.person(0), Map.of(kimbap, 1, ramen, 1));
			api.vote(room.code(), room.person(1), Map.of(kimbap, 1, jjajang, -1));
			api.vote(room.code(), room.person(2), Map.of(ramen, -1))
				.andExpect(jsonPath("$.room.status").value("FINISHED"))
				.andExpect(jsonPath("$.room.phaseEndsAt", nullValue()))
				.andExpect(jsonPath("$.room.result.name").value("김밥"))
				.andExpect(jsonPath("$.room.voteScores[*].menu.name", contains("김밥", "라면", "짜장면")))
				.andExpect(jsonPath("$.room.voteScores[*].total", contains(2, 0, -1)))
				.andExpect(jsonPath("$.room.voteScores[1].plus").value(1))
				.andExpect(jsonPath("$.room.voteScores[1].minus").value(1));
		}

		@Test
		@DisplayName("전부 마이너스여도 가장 덜 싫은 메뉴로 결정된다")
		void leastDislikedWins() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");
			String kimbap = menuId(room, "김밥");
			String ramen = menuId(room, "라면");
			String jjajang = menuId(room, "짜장면");

			api.vote(room.code(), room.person(0), Map.of(kimbap, -1, ramen, -1));
			api.vote(room.code(), room.person(1), Map.of(kimbap, -1));
			api.vote(room.code(), room.person(2), Map.of(ramen, -1, jjajang, -1))
				.andExpect(jsonPath("$.room.result.name").value("짜장면"));
		}

		@Test
		@DisplayName("표를 하나도 못 받은 메뉴는 0점이라, 다른 메뉴가 모두 마이너스면 그 메뉴로 결정된다")
		void menuWithoutVotesCanWin() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");

			api.vote(room.code(), room.person(0), Map.of(menuId(room, "김밥"), -1));
			api.vote(room.code(), room.person(1), Map.of(menuId(room, "라면"), -1));
			api.voteAllGood(room.code(), room.person(2)).andExpect(jsonPath("$.room.result.name").value("짜장면"));
		}

		@Test
		@DisplayName("일부만 다 좋음을 누르면 나머지 사람들의 표로 결정된다")
		void allGoodLeavesItToOthers() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");

			api.voteAllGood(room.code(), room.person(0));
			api.vote(room.code(), room.person(1), Map.of(menuId(room, "라면"), 1));
			api.voteAllGood(room.code(), room.person(2))
				.andExpect(jsonPath("$.room.status").value("FINISHED"))
				.andExpect(jsonPath("$.room.result.name").value("라면"));
		}

		@Test
		@DisplayName("1등이 동점이면 재투표 단계로 가고, 점수가 공개된다 (재투표는 기능 5)")
		void tieGoesToRevote() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");

			api.vote(room.code(), room.person(0), Map.of(menuId(room, "김밥"), 1));
			api.vote(room.code(), room.person(1), Map.of(menuId(room, "라면"), 1));
			api.voteAllGood(room.code(), room.person(2))
				.andExpect(jsonPath("$.room.status").value("REVOTING"))
				.andExpect(jsonPath("$.room.result", nullValue()))
				.andExpect(jsonPath("$.room.voteScores[*].total", contains(1, 1, 0)));
		}

		@Test
		@DisplayName("모두 다 좋음을 누르면(아무도 투표하지 않으면) 랜덤 선택으로 간다")
		void everyoneAllGoodGoesToRandomPick() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면");

			api.voteAllGood(room.code(), room.person(0));
			api.voteAllGood(room.code(), room.person(1))
				.andExpect(jsonPath("$.room.status").value("RANDOM_PICK"))
				.andExpect(jsonPath("$.room.voteScores", empty()));
		}

		@Test
		@DisplayName("방장이 다음 단계로를 누르면 지금까지 낸 표로 결정된다")
		void hostEndsVotingEarly() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면", "짜장면");
			api.vote(room.code(), room.person(1), Map.of(menuId(room, "짜장면"), 1));

			api.nextPhase(room.code(), room.host(), "VOTING")
				.andExpect(jsonPath("$.room.status").value("FINISHED"))
				.andExpect(jsonPath("$.room.result.name").value("짜장면"));
		}

		@Test
		@DisplayName("결과에는 메뉴별 점수만 있고, 누가 어떻게 찍었는지는 없다")
		void resultRevealsNoVoters() throws Exception {
			VotingRoom room = api.votingRoom(null, "김밥", "라면");
			api.vote(room.code(), room.person(0), Map.of(menuId(room, "김밥"), 1));

			String body = api.vote(room.code(), room.person(1), Map.of(menuId(room, "김밥"), 1))
				.andReturn()
				.getResponse()
				.getContentAsString();

			List<Map<String, Object>> scores = JsonPath.read(body, "$.room.voteScores");
			assertThat(scores).allSatisfy(score -> assertThat(score).containsOnlyKeys("menu", "plus", "minus", "total"));
			Map<String, Object> result = JsonPath.read(body, "$.room.result");
			assertThat(result).containsOnlyKeys("name", "reason");
		}

	}

	private String menuId(VotingRoom room, String name) throws Exception {
		return api.menuIdNamed(room.code(), room.host(), name);
	}

	private static void assertInvalid(ResultActions request) throws Exception {
		request.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BALLOT"));
	}

}
