package com.whattoeat.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.whattoeat.IntegrationTest;
import com.whattoeat.room.web.RoomApiClient.TestRoom;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ProposalApiTest {

	@Autowired
	MockMvc mockMvc;

	RoomApiClient api;

	/** startedRoomWithGuests로 들인 손님들의 쿠키 */
	List<Cookie> guests;

	@BeforeEach
	void setUp() {
		api = new RoomApiClient(mockMvc);
	}

	@Nested
	@DisplayName("시작")
	class Start {

		@Test
		@DisplayName("방장이 시작하면 제안 단계가 되고, 방장이 정한 제안 시간 뒤에 끝난다 (방장 혼자여도 시작할 수 있다)")
		void hostStartsProposal() throws Exception {
			TestRoom room = api.createRoom("{\"proposalSeconds\": 90}");

			String body = api.nextPhase(room.code(), room.host(), "WAITING")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.room.status").value("PROPOSING"))
				.andExpect(jsonPath("$.room.doneCount").value(0))
				.andReturn()
				.getResponse()
				.getContentAsString();

			Instant endsAt = Instant.parse(JsonPath.read(body, "$.room.phaseEndsAt"));
			Instant serverTime = Instant.parse(JsonPath.read(body, "$.room.serverTime"));
			// 시작한 순간과 응답을 만든 순간 사이의 아주 짧은 시간만큼만 90초보다 짧을 수 있다.
			assertThat(Duration.between(serverTime, endsAt)).isBetween(Duration.ofSeconds(89), Duration.ofSeconds(90));
		}

		@Test
		@DisplayName("방장이 아니면 시작할 수 없다")
		void guestCannotStart() throws Exception {
			TestRoom room = api.createRoom(null);
			Cookie guest = api.join(room.code());

			api.nextPhase(room.code(), guest, "WAITING")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_HOST"));
		}

		@Test
		@DisplayName("방장이 시작을 두 번 눌러도 한 단계만 넘어간다")
		void doublePressMovesOnlyOnePhase() throws Exception {
			TestRoom room = api.createRoom(null);
			api.join(room.code());
			api.nextPhase(room.code(), room.host(), "WAITING").andExpect(status().isOk());

			api.nextPhase(room.code(), room.host(), "WAITING")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PHASE_ALREADY_CHANGED"));
			api.getRoom(room.code(), room.host()).andExpect(jsonPath("$.room.status").value("PROPOSING"));
		}

		@Test
		@DisplayName("지금 단계를 보내지 않거나 없는 단계 이름을 보내면 400이다")
		void requiresValidFromPhase() throws Exception {
			TestRoom room = api.createRoom(null);

			api.nextPhase(room.code(), room.host(), "NOT_A_PHASE")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}

	}

	@Nested
	@DisplayName("제안")
	class Propose {

		@Test
		@DisplayName("제안한 메뉴는 모두의 목록에 보이고, 내가 낸 메뉴라는 건 내 응답(me)에만 있다")
		void proposalIsVisibleToAllButOwnershipOnlyToMe() throws Exception {
			TestRoom room = startedRoomWithGuests(1);
			Cookie guest = guests.getFirst();

			String body = api.propose(room.code(), guest, "제육")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.me.proposal").value("제육"))
				.andExpect(jsonPath("$.me.done").value(true))
				.andExpect(jsonPath("$.room.menus[*].name", contains("제육")))
				.andExpect(jsonPath("$.room.doneCount").value(1))
				.andReturn()
				.getResponse()
				.getContentAsString();
			// 목록의 메뉴 중 어느 것이 내 것인지는 내 응답에만 있다.
			assertThat(JsonPath.<String>read(body, "$.me.menuId")).isEqualTo(JsonPath.read(body, "$.room.menus[0].id"));

			api.getRoom(room.code(), room.host())
				.andExpect(jsonPath("$.room.menus[*].name", contains("제육")))
				.andExpect(jsonPath("$.me.proposal", nullValue()))
				.andExpect(jsonPath("$.me.menuId", nullValue()))
				.andExpect(jsonPath("$.me.done").value(false));
		}

		@Test
		@DisplayName("한 사람이 두 번 제안하면 메뉴가 새로 생기지 않고 바뀐다 (한 사람 한 메뉴)")
		void secondProposalReplacesTheFirst() throws Exception {
			TestRoom room = startedRoomWithGuests(1);
			Cookie guest = guests.getFirst();

			api.propose(room.code(), guest, "제육").andExpect(status().isOk());
			api.propose(room.code(), guest, "김치찌개")
				.andExpect(jsonPath("$.room.menus[*].name", contains("김치찌개")))
				.andExpect(jsonPath("$.me.proposal").value("김치찌개"));
		}

		@Test
		@DisplayName("목록은 올라온 순서가 아니라 가나다순이고, 같은 이름도 낼 수 있다")
		void menusAreSortedAndMayRepeat() throws Exception {
			TestRoom room = startedRoomWithGuests(4);

			api.propose(room.code(), guests.get(0), "짜장면");
			api.propose(room.code(), guests.get(1), "김밥");
			api.propose(room.code(), guests.get(2), "라면");
			api.propose(room.code(), guests.get(3), "김밥")
				.andExpect(jsonPath("$.room.menus[*].name", contains("김밥", "김밥", "라면", "짜장면")));
		}

		@Test
		@DisplayName("메뉴 이름의 앞뒤 공백은 지우고, 비어 있거나 30자를 넘으면 400이다")
		void validatesMenuName() throws Exception {
			TestRoom room = startedRoomWithGuests(1);
			Cookie guest = guests.getFirst();

			api.propose(room.code(), guest, "   ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_MENU_NAME"));
			api.propose(room.code(), guest, "가".repeat(31)).andExpect(status().isBadRequest());
			api.propose(room.code(), guest, "  할매집 제육  ").andExpect(jsonPath("$.me.proposal").value("할매집 제육"));
		}

		@Test
		@DisplayName("제안 단계가 아니면 제안할 수 없다 (시작 전, 제안이 끝난 뒤)")
		void rejectsOutsideProposalPhase() throws Exception {
			TestRoom room = api.createRoom(null);
			Cookie guest = api.join(room.code());

			api.propose(room.code(), guest, "제육")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_PROPOSAL_PHASE"));

			api.nextPhase(room.code(), room.host(), "WAITING");
			api.propose(room.code(), room.host(), "김밥");
			api.nextPhase(room.code(), room.host(), "PROPOSING").andExpect(jsonPath("$.room.status").value("FINISHED"));

			api.propose(room.code(), guest, "제육").andExpect(status().isConflict());
			api.allGood(room.code(), guest).andExpect(status().isConflict());
		}

		@Test
		@DisplayName("이 방의 참여자가 아니면 제안할 수 없다")
		void rejectsNonParticipant() throws Exception {
			TestRoom room = startedRoomWithGuests(0);
			TestRoom otherRoom = api.createRoom(null);

			api.propose(room.code(), otherRoom.host(), "제육")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));
		}

	}

	@Nested
	@DisplayName("다 좋음")
	class AllGood {

		@Test
		@DisplayName("다 좋음을 누르면 이미 낸 메뉴가 취소되고, 끝낸 사람으로 센다")
		void allGoodCancelsMyProposal() throws Exception {
			TestRoom room = startedRoomWithGuests(1);
			Cookie guest = guests.getFirst();
			api.propose(room.code(), guest, "제육");

			api.allGood(room.code(), guest)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.me.allGood").value(true))
				.andExpect(jsonPath("$.me.proposal", nullValue()))
				.andExpect(jsonPath("$.room.menus", empty()))
				.andExpect(jsonPath("$.room.doneCount").value(1));
		}

		@Test
		@DisplayName("다 좋음을 눌렀다가 단계가 끝나기 전에 다시 제안할 수 있다")
		void canProposeAfterAllGood() throws Exception {
			TestRoom room = startedRoomWithGuests(1);
			Cookie guest = guests.getFirst();
			api.allGood(room.code(), guest);

			api.propose(room.code(), guest, "라면")
				.andExpect(jsonPath("$.me.allGood").value(false))
				.andExpect(jsonPath("$.me.proposal").value("라면"))
				.andExpect(jsonPath("$.room.doneCount").value(1));
		}

	}

	@Nested
	@DisplayName("다음 단계로 넘어가기")
	class NextPhase {

		@Test
		@DisplayName("모두 제안하거나 다 좋음을 누르면 시간이 남아 있어도 바로 합치기 단계로 간다")
		void movesOnWhenEveryoneIsDone() throws Exception {
			TestRoom room = startedRoomWithGuests(2);
			api.propose(room.code(), room.host(), "제육");
			api.propose(room.code(), guests.get(0), "김밥").andExpect(jsonPath("$.room.status").value("PROPOSING"));

			api.allGood(room.code(), guests.get(1))
				.andExpect(jsonPath("$.room.status").value("MERGING"))
				.andExpect(jsonPath("$.room.phaseEndsAt", notNullValue()))
				.andExpect(jsonPath("$.room.menus[*].name", contains("김밥", "제육")));
		}

		@Test
		@DisplayName("아무도 메뉴를 제안하지 않으면 랜덤 선택 단계로 간다")
		void movesToRandomPickWhenNobodyProposed() throws Exception {
			TestRoom room = startedRoomWithGuests(1);
			api.allGood(room.code(), room.host());

			api.allGood(room.code(), guests.getFirst()).andExpect(jsonPath("$.room.status").value("RANDOM_PICK"));
		}

		@Test
		@DisplayName("방장이 다음 단계로를 누르면 아직 안 끝낸 사람이 있어도 넘어간다")
		void hostCanMoveOnEarly() throws Exception {
			TestRoom room = startedRoomWithGuests(2);
			api.propose(room.code(), guests.get(0), "제육");
			api.propose(room.code(), guests.get(1), "김밥");

			api.nextPhase(room.code(), room.host(), "PROPOSING").andExpect(jsonPath("$.room.status").value("MERGING"));
		}

		@Test
		@DisplayName("늦게 들어온 사람도 제안할 수 있고, 모두 끝냈는지 셀 때 포함된다")
		void lateJoinerCountsTowardsEveryoneDone() throws Exception {
			TestRoom room = startedRoomWithGuests(1);
			api.propose(room.code(), room.host(), "제육");
			Cookie lateComer = api.join(room.code());

			api.propose(room.code(), guests.getFirst(), "김밥")
				.andExpect(jsonPath("$.room.status").value("PROPOSING"))
				.andExpect(jsonPath("$.room.doneCount").value(2))
				.andExpect(jsonPath("$.room.participantCount").value(3));
			api.propose(room.code(), lateComer, "라면").andExpect(jsonPath("$.room.status").value("MERGING"));
		}

		@Test
		@DisplayName("메뉴가 하나뿐이면 합치기도 투표도 없이 바로 그 메뉴로 결정된다")
		void decidesSingleMenuRightAway() throws Exception {
			TestRoom room = startedRoomWithGuests(1);
			api.allGood(room.code(), guests.getFirst());

			api.propose(room.code(), room.host(), "제육")
				.andExpect(jsonPath("$.room.status").value("FINISHED"))
				.andExpect(jsonPath("$.room.resultMenu.name").value("제육"))
				.andExpect(jsonPath("$.room.voteScores", empty()))
				.andExpect(jsonPath("$.room.mergeGroups", empty()));
		}

		@Test
		@DisplayName("메뉴가 결정된 뒤에는 다음 단계가 없어서 방장도 넘길 수 없다")
		void noNextPhaseAfterFinished() throws Exception {
			TestRoom room = startedRoomWithGuests(0);
			api.propose(room.code(), room.host(), "제육").andExpect(jsonPath("$.room.status").value("FINISHED"));

			api.nextPhase(room.code(), room.host(), "FINISHED")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NO_NEXT_PHASE"));
		}

	}

	@Test
	@DisplayName("메뉴 목록에는 누가 냈는지가 없고, 메뉴 번호는 올라온 순서가 드러나지 않는 무작위 값이다")
	void menuListRevealsNoProposer() throws Exception {
		TestRoom room = startedRoomWithGuests(2);
		api.propose(room.code(), guests.get(0), "제육");

		String body = api.getRoom(room.code(), guests.get(1)).andReturn().getResponse().getContentAsString();

		List<Map<String, Object>> menus = JsonPath.read(body, "$.room.menus");
		assertThat(menus).hasSize(1).allSatisfy(menu -> assertThat(menu).containsOnlyKeys("id", "name"));
		assertThat(menus.getFirst().get("name")).isEqualTo("제육");
		// DB 번호(1, 2, 3...)가 아니라 무작위 UUID다.
		assertThat((String) menus.getFirst().get("id")).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
		assertThat(JsonPath.<String>read(body, "$.me.proposal")).isNull();
	}

	/** 방을 만들고 손님 n명을 들인 뒤 시작한다. 손님 쿠키는 guests에 담긴다. */
	private TestRoom startedRoomWithGuests(int guestCount) throws Exception {
		TestRoom room = api.createRoom(null);
		guests = new ArrayList<>();
		for (int i = 0; i < guestCount; i++) {
			guests.add(api.join(room.code()));
		}
		api.nextPhase(room.code(), room.host(), "WAITING").andExpect(status().isOk());
		return room;
	}

}
