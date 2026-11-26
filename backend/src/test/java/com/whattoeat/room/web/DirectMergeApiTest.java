package com.whattoeat.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.whattoeat.IntegrationTest;
import com.whattoeat.room.web.RoomApiClient.MergingRoom;
import com.whattoeat.room.web.RoomApiClient.TestRoom;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 직접 합치기: 내 메뉴를 다른 메뉴에 합치자고 요청하고, 요청받은 사람이 답한다. 받아들여지면 요청받은 메뉴가 대표로 남는다.
 */
@IntegrationTest
class DirectMergeApiTest {

	@Autowired
	MockMvc mockMvc;

	RoomApiClient api;

	@BeforeEach
	void setUp() {
		api = new RoomApiClient(mockMvc);
	}

	@Nested
	@DisplayName("요청과 답")
	class RequestAndAnswer {

		@Test
		@DisplayName("요청하면 요청받은 사람에게 질문이 가고, 직접 요청은 모두에게 메뉴 이름으로 보인다")
		void requestAsksTheTarget() throws Exception {
			MergingRoom room = api.mergingRoom(null, "할매집 제육", "제육볶음", "김밥");
			String target = menuIdOf(room, 1);

			String groupId = sentGroupOf(api.requestMerge(room.code(), room.person(0), target)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.me.pendingGroupId", nullValue()))
				.andExpect(jsonPath("$.me.requestableMenuIds", empty())));

			api.getRoom(room.code(), room.person(2))
				.andExpect(jsonPath("$.room.mergeGroups[0].id").value(groupId))
				.andExpect(jsonPath("$.room.mergeGroups[0].kind").value("DIRECT"))
				.andExpect(jsonPath("$.room.mergeGroups[0].targetMenuId").value(target))
				.andExpect(jsonPath("$.room.mergeGroups[0].status").value("PENDING"))
				.andExpect(jsonPath("$.room.mergeGroups[0].answeredCount").value(1));
			api.getRoom(room.code(), room.person(1)).andExpect(jsonPath("$.me.pendingGroupId").value(groupId));
		}

		@Test
		@DisplayName("요청받은 사람이 동의하면 요청받은 메뉴가 대표로 남고, 요청한 메뉴가 거기에 합쳐진다")
		void acceptedRequestKeepsTheTarget() throws Exception {
			MergingRoom room = api.mergingRoom(null, "할매집 제육", "제육볶음");
			String target = menuIdOf(room, 1);
			String groupId = sentGroupOf(api.requestMerge(room.code(), room.person(0), target));

			api.answerMerge(room.code(), room.person(1), groupId, true)
				.andExpect(jsonPath("$.room.mergeGroups[0].status").value("MERGED"))
				.andExpect(jsonPath("$.room.mergeGroups[0].representative").value("제육볶음"))
				.andExpect(jsonPath("$.room.menus[*].name", contains("제육볶음")))
				.andExpect(jsonPath("$.me.menuId").value(target));
			api.getRoom(room.code(), room.person(0))
				.andExpect(jsonPath("$.me.mergedInto").value("제육볶음"))
				.andExpect(jsonPath("$.me.sentGroupId", nullValue()))
				.andExpect(jsonPath("$.me.done").value(true));
		}

		@Test
		@DisplayName("거절하면 두 메뉴가 그대로 남고, 같은 두 메뉴 사이에는 방향과 상관없이 다시 요청할 수 없다")
		void declinedPairCannotBeRequestedAgain() throws Exception {
			MergingRoom room = api.mergingRoom(null, "할매집 제육", "제육볶음", "김밥");
			String groupId = sentGroupOf(api.requestMerge(room.code(), room.person(0), menuIdOf(room, 1)));

			api.answerMerge(room.code(), room.person(1), groupId, false)
				.andExpect(jsonPath("$.room.mergeGroups[0].status").value("NOT_MERGED"))
				.andExpect(jsonPath("$.room.menus.length()").value(3));

			api.requestMerge(room.code(), room.person(0), menuIdOf(room, 1))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_DECLINED"));
			api.requestMerge(room.code(), room.person(1), menuIdOf(room, 0))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_DECLINED"));
			api.requestMerge(room.code(), room.person(0), menuIdOf(room, 2)).andExpect(status().isOk());
		}

		@Test
		@DisplayName("이미 다른 메뉴가 합쳐진 대표 메뉴에 요청하면, 대표 메뉴를 낸 사람이 답한다")
		void representativeOwnerAnswers() throws Exception {
			MergingRoom room = api.mergingRoom(null, "제육볶음", "제육 볶음", "할매집 제육");
			int representativeOwner = mergeAutoGroupOfFirstTwo(room);
			String groupId = sentGroupOf(
					api.requestMerge(room.code(), room.person(2), menuIdOf(room, representativeOwner)));

			api.getRoom(room.code(), room.person(representativeOwner))
				.andExpect(jsonPath("$.me.pendingGroupId").value(groupId));
			api.answerMerge(room.code(), room.person(representativeOwner), groupId, true)
				.andExpect(jsonPath("$.room.menus.length()").value(1));
		}

		@Test
		@DisplayName("다른 메뉴가 합쳐진 대표 메뉴도 요청을 보낼 수 있고, 받아들여지면 먼저 합쳐진 사람에게도 최종 메뉴가 보인다")
		void mergedMenusFollowTheirRepresentative() throws Exception {
			MergingRoom room = api.mergingRoom(null, "제육볶음", "제육 볶음", "할매집 제육");
			int representativeOwner = mergeAutoGroupOfFirstTwo(room);
			int mergedOwner = 1 - representativeOwner;
			String groupId = sentGroupOf(
					api.requestMerge(room.code(), room.person(representativeOwner), menuIdOf(room, 2)));

			api.answerMerge(room.code(), room.person(2), groupId, true)
				.andExpect(jsonPath("$.room.menus[*].name", contains("할매집 제육")));
			api.getRoom(room.code(), room.person(mergedOwner)).andExpect(jsonPath("$.me.mergedInto").value("할매집 제육"));
			api.getRoom(room.code(), room.person(representativeOwner))
				.andExpect(jsonPath("$.me.mergedInto").value("할매집 제육"));
		}

		@Test
		@DisplayName("자동 그룹에서 안 합쳐진 메뉴끼리도 직접 요청은 할 수 있다")
		void canRequestAfterAutoGroupWasNotMerged() throws Exception {
			MergingRoom room = api.mergingRoom(null, "제육볶음", "제육 볶음");
			String autoGroup = api.read(room.code(), room.person(0), "$.me.pendingGroupId");
			api.answerMerge(room.code(), room.person(0), autoGroup, true);
			api.answerMerge(room.code(), room.person(1), autoGroup, false)
				.andExpect(jsonPath("$.room.mergeGroups[0].status").value("NOT_MERGED"));

			api.requestMerge(room.code(), room.person(0), menuIdOf(room, 1)).andExpect(status().isOk());
		}

	}

	@Nested
	@DisplayName("요청할 수 없는 경우")
	class Rejections {

		@Test
		@DisplayName("메뉴를 내지 않은 사람은 요청할 수 없다")
		void requiresMyMenu() throws Exception {
			TestRoom created = api.createRoom(null);
			Cookie guest = api.join(created.code());
			Cookie allGood = api.join(created.code());
			api.nextPhase(created.code(), created.host(), "WAITING");
			api.propose(created.code(), created.host(), "김밥");
			api.propose(created.code(), guest, "라면");
			api.allGood(created.code(), allGood).andExpect(jsonPath("$.room.status").value("MERGING"));
			String target = api.read(created.code(), guest, "$.me.menuId");

			api.requestMerge(created.code(), allGood, target)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NO_MENU_TO_MERGE"));
			api.getRoom(created.code(), allGood).andExpect(jsonPath("$.me.requestableMenuIds", empty()));
		}

		@Test
		@DisplayName("내 메뉴나 없는 메뉴에는 요청할 수 없고, 요청할 메뉴를 보내지 않으면 400이다")
		void requiresAnotherExistingMenu() throws Exception {
			MergingRoom room = api.mergingRoom(null, "김밥", "라면");

			api.requestMerge(room.code(), room.person(0), menuIdOf(room, 0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_MERGE_WITH_OWN_MENU"));
			api.requestMerge(room.code(), room.person(0), "no-such-menu")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
			mockMvc.perform(post("/api/rooms/" + room.code() + "/me/merge-requests").cookie(room.person(0))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}

		@Test
		@DisplayName("결과가 안 나온 자동 그룹에 든 메뉴는 요청을 보낼 수도 받을 수도 없고, 결과가 나오면 할 수 있다")
		void waitingAutoGroupBlocksRequests() throws Exception {
			MergingRoom room = api.mergingRoom(null, "제육볶음", "제육 볶음", "김밥");

			api.requestMerge(room.code(), room.person(0), menuIdOf(room, 2))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MENU_BUSY"));
			api.requestMerge(room.code(), room.person(2), menuIdOf(room, 0))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MENU_BUSY"));
			api.getRoom(room.code(), room.person(2)).andExpect(jsonPath("$.me.requestableMenuIds", empty()));

			String autoGroup = api.read(room.code(), room.person(0), "$.me.pendingGroupId");
			api.answerMerge(room.code(), room.person(0), autoGroup, false);
			api.answerMerge(room.code(), room.person(1), autoGroup, false);

			api.getRoom(room.code(), room.person(2))
				.andExpect(jsonPath("$.me.requestableMenuIds", containsInAnyOrder(menuIdOf(room, 0), menuIdOf(room, 1))));
			api.requestMerge(room.code(), room.person(2), menuIdOf(room, 0)).andExpect(status().isOk());
		}

		@Test
		@DisplayName("한 메뉴는 한 번에 요청 하나에만 걸린다 (보내는 쪽, 받는 쪽 모두)")
		void oneRequestPerMenuAtATime() throws Exception {
			MergingRoom room = api.mergingRoom(null, "김밥", "라면", "짜장면", "우동");
			api.requestMerge(room.code(), room.person(0), menuIdOf(room, 1)).andExpect(status().isOk());

			assertBusy(api.requestMerge(room.code(), room.person(2), menuIdOf(room, 1))); // 이미 요청받은 메뉴
			assertBusy(api.requestMerge(room.code(), room.person(2), menuIdOf(room, 0))); // 이미 요청을 보낸 메뉴
			assertBusy(api.requestMerge(room.code(), room.person(0), menuIdOf(room, 3))); // 보낸 사람이 또 보내기
			assertBusy(api.requestMerge(room.code(), room.person(1), menuIdOf(room, 3))); // 받은 사람이 다른 곳에 보내기
			api.requestMerge(room.code(), room.person(2), menuIdOf(room, 3)).andExpect(status().isOk());
		}

		@Test
		@DisplayName("합치기를 끝낸 사람의 메뉴에는 요청할 수 없고, 끝낸 사람은 요청을 보낼 수 없다")
		void finishedPeopleCannotBeInvolved() throws Exception {
			MergingRoom room = api.mergingRoom(null, "김밥", "라면", "짜장면");
			api.finishMerge(room.code(), room.person(1)).andExpect(status().isOk());

			api.requestMerge(room.code(), room.person(0), menuIdOf(room, 1))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TARGET_FINISHED_MERGING"));
			api.requestMerge(room.code(), room.person(1), menuIdOf(room, 0))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MERGE_ALREADY_FINISHED"));
			api.getRoom(room.code(), room.person(0))
				.andExpect(jsonPath("$.me.requestableMenuIds", contains(menuIdOf(room, 2))));
		}

		@Test
		@DisplayName("합치기 단계가 아니면 요청할 수 없다")
		void rejectsOutsideMergePhase() throws Exception {
			TestRoom proposing = api.createRoom(null);
			api.nextPhase(proposing.code(), proposing.host(), "WAITING");

			api.requestMerge(proposing.code(), proposing.host(), "any-menu")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_MERGE_PHASE"));
		}

	}

	@Nested
	@DisplayName("합치기 끝냄과 단계 넘어가기")
	class FinishAndNextPhase {

		@Test
		@DisplayName("보낸 요청의 답을 기다리는 동안에는 합치기 끝냄을 누를 수 없고, 답이 나오면 누를 수 있다")
		void cannotFinishWhileWaitingForAnswer() throws Exception {
			MergingRoom room = api.mergingRoom(null, "김밥", "라면", "짜장면");
			String groupId = sentGroupOf(api.requestMerge(room.code(), room.person(0), menuIdOf(room, 1)));

			api.finishMerge(room.code(), room.person(0))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("WAITING_FOR_REQUEST_ANSWER"));
			api.getRoom(room.code(), room.person(0)).andExpect(jsonPath("$.me.done").value(false));

			api.answerMerge(room.code(), room.person(1), groupId, false);
			api.finishMerge(room.code(), room.person(0))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.me.done").value(true));
		}

		@Test
		@DisplayName("받은 요청이 있는 채로 합치기 끝냄을 누르면 거절로 처리되고, 보낸 사람도 끝낼 수 있게 된다")
		void finishingDeclinesReceivedRequest() throws Exception {
			MergingRoom room = api.mergingRoom(null, "김밥", "라면");
			api.requestMerge(room.code(), room.person(0), menuIdOf(room, 1));

			api.finishMerge(room.code(), room.person(1))
				.andExpect(jsonPath("$.me.pendingGroupId", nullValue()))
				.andExpect(jsonPath("$.room.mergeGroups[0].status").value("NOT_MERGED"));
			api.finishMerge(room.code(), room.person(0)).andExpect(jsonPath("$.room.status").value("VOTING"));
		}

		@Test
		@DisplayName("방장이 다음 단계로를 누르면 답하지 않은 요청은 거절로 처리된다")
		void hostDeclinesWaitingRequests() throws Exception {
			MergingRoom room = api.mergingRoom(null, "김밥", "라면", "짜장면");
			api.requestMerge(room.code(), room.person(1), menuIdOf(room, 2));

			api.nextPhase(room.code(), room.host(), "MERGING")
				.andExpect(jsonPath("$.room.status").value("VOTING"))
				.andExpect(jsonPath("$.room.mergeGroups[0].status").value("NOT_MERGED"))
				.andExpect(jsonPath("$.room.mergeGroups[0].answeredCount").value(2))
				.andExpect(jsonPath("$.room.menus.length()").value(3))
				.andExpect(jsonPath("$.me.requestableMenuIds", empty()));
		}

	}

	@Test
	@DisplayName("직접 요청에는 누가 보냈는지가 없고, 두 메뉴와 요청받은 메뉴만 있다")
	void requestRevealsNoOne() throws Exception {
		MergingRoom room = api.mergingRoom(null, "할매집 제육", "제육볶음", "김밥");
		api.requestMerge(room.code(), room.person(0), menuIdOf(room, 1)).andExpect(jsonPath("$.me.sentGroupId", notNullValue()));

		String body = api.roomJson(room.code(), room.person(2));

		Map<String, Object> group = JsonPath.read(body, "$.room.mergeGroups[0]");
		assertThat(group).containsOnlyKeys("id", "kind", "status", "menus", "targetMenuId", "representative",
				"answeredCount");
		List<Map<String, Object>> menus = JsonPath.read(body, "$.room.mergeGroups[0].menus");
		assertThat(menus).allSatisfy(menu -> assertThat(menu).containsOnlyKeys("id", "name"));
		assertThat(JsonPath.<String>read(body, "$.me.sentGroupId")).as("보낸 사람이 아니면 내 정보에 없다").isNull();
		assertThat(JsonPath.<String>read(body, "$.me.pendingGroupId")).as("받은 사람이 아니면 질문이 없다").isNull();
	}

	/**
	 * person(0)과 person(1)의 메뉴가 이름이 같아 생긴 자동 그룹을 둘 다 동의해서 합친다.
	 *
	 * @return 대표 메뉴를 낸 사람의 번호(0 또는 1). 대표는 무작위로 뽑힌다
	 */
	private int mergeAutoGroupOfFirstTwo(MergingRoom room) throws Exception {
		String autoGroup = api.read(room.code(), room.person(0), "$.me.pendingGroupId");
		api.answerMerge(room.code(), room.person(0), autoGroup, true);
		api.answerMerge(room.code(), room.person(1), autoGroup, true)
			.andExpect(jsonPath("$.room.mergeGroups[0].status").value("MERGED"));
		return menuIdOf(room, 0) != null ? 0 : 1;
	}

	private String menuIdOf(MergingRoom room, int person) throws Exception {
		return api.read(room.code(), room.person(person), "$.me.menuId");
	}

	private static String sentGroupOf(ResultActions request) throws Exception {
		String body = request.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String groupId = JsonPath.read(body, "$.me.sentGroupId");
		assertThat(groupId).as("보낸 요청의 번호가 내 정보에 있어야 한다").isNotNull();
		return groupId;
	}

	private static void assertBusy(ResultActions request) throws Exception {
		request.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MENU_BUSY"));
	}

}
