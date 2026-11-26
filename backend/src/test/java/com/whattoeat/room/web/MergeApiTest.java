package com.whattoeat.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

@IntegrationTest
class MergeApiTest {

	@Autowired
	MockMvc mockMvc;

	RoomApiClient api;

	@BeforeEach
	void setUp() {
		api = new RoomApiClient(mockMvc);
	}

	@Nested
	@DisplayName("합치기 단계 열기")
	class Open {

		@Test
		@DisplayName("제안이 끝났을 때 메뉴가 2개 이상이면 합치기 단계가 되고, 방장이 정한 합치기 시간 뒤에 끝난다")
		void opensWithMergeTimer() throws Exception {
			MergingRoom room = mergingRoomWith("{\"mergeSeconds\": 90}", "김밥", "라면");

			String body = api.roomJson(room.code(), room.host());

			Instant endsAt = Instant.parse(JsonPath.read(body, "$.room.phaseEndsAt"));
			Instant serverTime = Instant.parse(JsonPath.read(body, "$.room.serverTime"));
			assertThat(Duration.between(serverTime, endsAt)).isBetween(Duration.ofSeconds(89), Duration.ofSeconds(90));
		}

		@Test
		@DisplayName("이름이 같은 메뉴가 없어도 합치기 단계는 열린다 (직접 합치기는 그룹과 상관없이 할 수 있으므로)")
		void opensEvenWithoutGroups() throws Exception {
			MergingRoom room = mergingRoom("김밥", "라면");

			api.getRoom(room.code(), room.host())
				.andExpect(jsonPath("$.room.status").value("MERGING"))
				.andExpect(jsonPath("$.room.mergeGroups", empty()))
				.andExpect(jsonPath("$.room.doneCount").value(0))
				.andExpect(jsonPath("$.me.pendingGroupId", nullValue()))
				.andExpect(jsonPath("$.me.done").value(false));
		}

		@Test
		@DisplayName("띄어쓰기와 대소문자만 다른 메뉴끼리 그룹이 생기고, 그룹에 든 사람에게만 질문이 간다")
		void groupsSameNamesAndAsksOnlyMembers() throws Exception {
			MergingRoom room = mergingRoom("제육 볶음", "김밥", "제육볶음", "Pizza", "pizza");

			String body = api.roomJson(room.code(), room.person(1));
			List<List<String>> groupMenus = new ArrayList<>();
			for (int i = 0; i < JsonPath.<List<Object>>read(body, "$.room.mergeGroups").size(); i++) {
				groupMenus.add(JsonPath.read(body, "$.room.mergeGroups[" + i + "].menus[*].name"));
			}
			assertThat(groupMenus).hasSize(2)
				.anySatisfy(names -> assertThat(names).containsExactlyInAnyOrder("제육 볶음", "제육볶음"))
				.anySatisfy(names -> assertThat(names).containsExactlyInAnyOrder("Pizza", "pizza"));
			assertThat(JsonPath.<List<String>>read(body, "$.room.mergeGroups[*].status")).containsOnly("PENDING");

			String jeyukGroup = pendingGroupOf(room, 0);
			String pizzaGroup = pendingGroupOf(room, 3);
			assertThat(pendingGroupOf(room, 2)).isEqualTo(jeyukGroup);
			assertThat(pendingGroupOf(room, 4)).isEqualTo(pizzaGroup).isNotEqualTo(jeyukGroup);
			assertThat(pendingGroupOf(room, 1)).as("그룹에 들지 않은 사람에게는 질문이 없다").isNull();
		}

	}

	@Nested
	@DisplayName("답하기")
	class Answer {

		@Test
		@DisplayName("그룹의 모두가 동의하면 바로 합쳐지고, 동의한 메뉴 중 하나만 대표로 남는다")
		void mergesWhenEveryoneAgrees() throws Exception {
			MergingRoom room = mergingRoom("제육볶음", "제육 볶음", "김밥");
			String group = pendingGroupOf(room, 0);

			api.answerMerge(room.code(), room.person(0), group, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.me.pendingGroupId", nullValue()))
				.andExpect(jsonPath("$.room.mergeGroups[0].status").value("PENDING"))
				.andExpect(jsonPath("$.room.mergeGroups[0].answeredCount").value(1));
			api.answerMerge(room.code(), room.person(1), group, true).andExpect(status().isOk());

			String body = api.roomJson(room.code(), room.person(2));
			String representative = JsonPath.read(body, "$.room.mergeGroups[0].representative");
			assertThat(JsonPath.<String>read(body, "$.room.mergeGroups[0].status")).isEqualTo("MERGED");
			assertThat(representative).isIn("제육볶음", "제육 볶음");
			assertThat(JsonPath.<List<String>>read(body, "$.room.menus[*].name"))
				.containsExactlyInAnyOrder(representative, "김밥");

			// 두 제안자 중 한 사람은 메뉴가 남고, 다른 사람은 메뉴가 대표에 합쳐져 투표만 하면 된다.
			List<String> owners = List.of(api.roomJson(room.code(), room.person(0)),
					api.roomJson(room.code(), room.person(1)));
			assertThat(owners).filteredOn(me -> JsonPath.read(me, "$.me.menuId") != null).hasSize(1);
			assertThat(owners).filteredOn(me -> JsonPath.read(me, "$.me.menuId") == null)
				.singleElement()
				.satisfies(me -> {
					assertThat(JsonPath.<String>read(me, "$.me.mergedInto")).isEqualTo(representative);
					assertThat(JsonPath.<Boolean>read(me, "$.me.done")).isTrue();
				});
		}

		@Test
		@DisplayName("일부만 동의하면 동의한 메뉴끼리만 합치고, 거절한 사람의 메뉴는 따로 남는다")
		void mergesOnlyAgreedMenus() throws Exception {
			MergingRoom room = mergingRoom("제육볶음", "제육볶음", "제육볶음");
			String group = pendingGroupOf(room, 0);

			api.answerMerge(room.code(), room.person(0), group, true);
			api.answerMerge(room.code(), room.person(1), group, true);
			api.answerMerge(room.code(), room.person(2), group, false)
				.andExpect(jsonPath("$.room.mergeGroups[0].status").value("MERGED"))
				.andExpect(jsonPath("$.room.menus[*].name", containsInAnyOrder("제육볶음", "제육볶음")))
				.andExpect(jsonPath("$.me.mergedInto", nullValue()))
				.andExpect(jsonPath("$.me.done").value(false));
		}

		@Test
		@DisplayName("동의한 사람이 1명뿐이면 합치지 않는다")
		void doesNotMergeWithSingleAgreement() throws Exception {
			MergingRoom room = mergingRoom("제육볶음", "제육 볶음");
			String group = pendingGroupOf(room, 0);

			api.answerMerge(room.code(), room.person(0), group, true);
			api.answerMerge(room.code(), room.person(1), group, false)
				.andExpect(jsonPath("$.room.mergeGroups[0].status").value("NOT_MERGED"))
				.andExpect(jsonPath("$.room.mergeGroups[0].representative", nullValue()))
				.andExpect(jsonPath("$.room.menus[*].name", containsInAnyOrder("제육볶음", "제육 볶음")));
		}

		@Test
		@DisplayName("한 번 한 답은 바꿀 수 없고, 같은 답을 다시 보내면(두 번 누르기) 그대로 성공한다")
		void answerCannotBeChanged() throws Exception {
			MergingRoom room = mergingRoom("제육볶음", "제육 볶음", "김밥");
			String group = pendingGroupOf(room, 0);
			api.answerMerge(room.code(), room.person(0), group, true).andExpect(status().isOk());

			api.answerMerge(room.code(), room.person(0), group, false)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_ANSWERED"));
			api.answerMerge(room.code(), room.person(0), group, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.room.mergeGroups[0].answeredCount").value(1));
		}

		@Test
		@DisplayName("그룹에 들지 않은 사람은 답할 수 없다")
		void outsiderCannotAnswer() throws Exception {
			MergingRoom room = mergingRoom("제육볶음", "제육 볶음", "김밥");

			api.answerMerge(room.code(), room.person(2), pendingGroupOf(room, 0), true)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_MERGE_MEMBER"));
		}

		@Test
		@DisplayName("없는 질문이나 다른 방의 질문에는 답할 수 없다")
		void unknownGroupIsNotFound() throws Exception {
			MergingRoom room = mergingRoom("제육볶음", "제육 볶음");
			MergingRoom otherRoom = mergingRoom("라면", "라 면");

			api.answerMerge(room.code(), room.person(0), "no-such-group", true)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MERGE_GROUP_NOT_FOUND"));
			api.answerMerge(room.code(), room.person(0), pendingGroupOf(otherRoom, 0), true)
				.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("답(agree)을 보내지 않으면 400이다")
		void requiresAnswer() throws Exception {
			MergingRoom room = mergingRoom("제육볶음", "제육 볶음");

			mockMvc.perform(put("/api/rooms/" + room.code() + "/me/merge-answers/" + pendingGroupOf(room, 0))
				.cookie(room.person(0))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}

	}

	@Nested
	@DisplayName("합치기 끝냄")
	class Finish {

		@Test
		@DisplayName("메뉴가 남은 사람은 합치기 끝냄을 눌러야 끝낸 것이고, 모두 끝내면 시간이 남아 있어도 바로 투표 단계로 간다")
		void movesToVotingWhenEveryoneFinishes() throws Exception {
			MergingRoom room = mergingRoom("김밥", "라면");

			api.finishMerge(room.code(), room.person(0))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.me.done").value(true))
				.andExpect(jsonPath("$.room.doneCount").value(1))
				.andExpect(jsonPath("$.room.status").value("MERGING"));
			api.finishMerge(room.code(), room.person(1))
				.andExpect(jsonPath("$.room.status").value("VOTING"))
				.andExpect(jsonPath("$.room.phaseEndsAt", nullValue()))
				.andExpect(jsonPath("$.room.menus[*].name", containsInAnyOrder("김밥", "라면")));
		}

		@Test
		@DisplayName("답하지 않은 질문이 있는 채로 합치기 끝냄을 누르면 '합치지 않기'로 답한 것으로 본다")
		void finishingDeclinesWaitingQuestion() throws Exception {
			MergingRoom room = mergingRoom("제육볶음", "제육 볶음", "김밥");
			api.answerMerge(room.code(), room.person(0), pendingGroupOf(room, 0), true);

			api.finishMerge(room.code(), room.person(1))
				.andExpect(jsonPath("$.me.pendingGroupId", nullValue()))
				.andExpect(jsonPath("$.me.done").value(true))
				// 동의한 사람이 1명뿐이 되어 합치지 않는다.
				.andExpect(jsonPath("$.room.mergeGroups[0].status").value("NOT_MERGED"));
		}

		@Test
		@DisplayName("합치기 끝냄을 두 번 눌러도 그대로다")
		void finishingTwiceChangesNothing() throws Exception {
			MergingRoom room = mergingRoom("김밥", "라면", "짜장면");
			api.finishMerge(room.code(), room.person(0)).andExpect(jsonPath("$.room.doneCount").value(1));

			api.finishMerge(room.code(), room.person(0))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.room.doneCount").value(1));
		}

		@Test
		@DisplayName("메뉴를 내지 않은 사람과 메뉴가 합쳐진 사람은 누르지 않아도 끝낸 것으로 센다")
		void peopleWithoutMenuAreDone() throws Exception {
			TestRoom created = api.createRoom(null);
			Cookie host = created.host();
			Cookie guest = api.join(created.code());
			Cookie allGood = api.join(created.code());
			api.nextPhase(created.code(), host, "WAITING");
			api.propose(created.code(), host, "제육볶음");
			api.propose(created.code(), guest, "제육 볶음");
			api.allGood(created.code(), allGood)
				.andExpect(jsonPath("$.room.status").value("MERGING"))
				.andExpect(jsonPath("$.room.doneCount").value(1))
				.andExpect(jsonPath("$.me.done").value(true));
			String group = JsonPath.read(api.roomJson(created.code(), host), "$.me.pendingGroupId");

			api.answerMerge(created.code(), host, group, true);
			api.answerMerge(created.code(), guest, group, true).andExpect(jsonPath("$.room.doneCount").value(2));

			// 메뉴가 남은 사람(대표 메뉴를 낸 사람)만 누르면 모두 끝난다.
			Cookie representativeOwner = JsonPath.read(api.roomJson(created.code(), host), "$.me.menuId") != null
					? host : guest;
			api.finishMerge(created.code(), representativeOwner).andExpect(jsonPath("$.room.status").value("VOTING"));
		}

		@Test
		@DisplayName("합치기 단계에 늦게 들어온 사람은 낼 메뉴가 없어서 끝낸 사람으로 센다")
		void lateJoinerIsDone() throws Exception {
			MergingRoom room = mergingRoom("김밥", "라면");

			api.getRoom(room.code(), api.join(room.code()))
				.andExpect(jsonPath("$.me.done").value(true))
				.andExpect(jsonPath("$.room.participantCount").value(3))
				.andExpect(jsonPath("$.room.doneCount").value(1));
			api.finishMerge(room.code(), room.person(0));
			api.finishMerge(room.code(), room.person(1)).andExpect(jsonPath("$.room.status").value("VOTING"));
		}

	}

	@Nested
	@DisplayName("단계 넘어가기")
	class NextPhase {

		@Test
		@DisplayName("방장이 다음 단계로를 누르면, 결과가 안 나온 그룹은 답하지 않은 사람을 '합치지 않기'로 보고 결과를 낸 뒤 투표 단계로 간다")
		void hostClosesWaitingGroups() throws Exception {
			MergingRoom room = mergingRoom("제육볶음", "제육 볶음", "제육볶음");
			String group = pendingGroupOf(room, 0);
			api.answerMerge(room.code(), room.person(0), group, true);
			api.answerMerge(room.code(), room.person(1), group, true);

			api.nextPhase(room.code(), room.host(), "MERGING")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.room.status").value("VOTING"))
				.andExpect(jsonPath("$.room.mergeGroups[0].status").value("MERGED"))
				.andExpect(jsonPath("$.room.mergeGroups[0].answeredCount").value(3))
				.andExpect(jsonPath("$.room.menus.length()").value(2));
		}

		@Test
		@DisplayName("합치기 단계가 아니면 답하거나 합치기 끝냄을 누를 수 없다")
		void rejectsOutsideMergePhase() throws Exception {
			TestRoom proposing = api.createRoom(null);
			api.nextPhase(proposing.code(), proposing.host(), "WAITING");
			api.finishMerge(proposing.code(), proposing.host())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_MERGE_PHASE"));

			MergingRoom room = mergingRoom("제육볶음", "제육 볶음");
			String group = pendingGroupOf(room, 0);
			api.nextPhase(room.code(), room.host(), "MERGING").andExpect(jsonPath("$.room.status").value("VOTING"));
			api.answerMerge(room.code(), room.person(1), group, true)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_MERGE_PHASE"));
			api.finishMerge(room.code(), room.person(1)).andExpect(status().isConflict());
		}

	}

	@Test
	@DisplayName("합치기 그룹에는 누가 어떻게 답했는지가 없고, 답한 사람 수만 있다")
	void groupRevealsNoAnswers() throws Exception {
		MergingRoom room = mergingRoom("제육볶음", "제육 볶음");
		api.answerMerge(room.code(), room.person(0), pendingGroupOf(room, 0), true);

		String body = api.roomJson(room.code(), room.person(1));

		Map<String, Object> group = JsonPath.read(body, "$.room.mergeGroups[0]");
		assertThat(group).containsOnlyKeys("id", "kind", "status", "menus", "targetMenuId", "representative",
				"answeredCount");
		List<Map<String, Object>> menus = JsonPath.read(body, "$.room.mergeGroups[0].menus");
		assertThat(menus).allSatisfy(menu -> assertThat(menu).containsOnlyKeys("id", "name"));
	}

	/** 각자 메뉴를 하나씩 내서 합치기 단계가 된 방. menus[i]를 낸 사람이 person(i)이고, 첫 번째가 방장이다. */
	private MergingRoom mergingRoom(String... menus) throws Exception {
		return api.mergingRoom(null, menus);
	}

	private MergingRoom mergingRoomWith(String createBody, String... menus) throws Exception {
		return api.mergingRoom(createBody, menus);
	}

	private String pendingGroupOf(MergingRoom room, int person) throws Exception {
		return api.read(room.code(), room.person(person), "$.me.pendingGroupId");
	}

}
