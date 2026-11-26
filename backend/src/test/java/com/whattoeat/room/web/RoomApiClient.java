package com.whattoeat.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 테스트에서 "방 만들기 → 참여 → 시작 → 제안"을 짧게 부르기 위한 도우미. 실제 API를 MockMvc로 부른다.
 */
final class RoomApiClient {

	private static final Pattern CODE = Pattern.compile("\"code\":\"([0-9A-Za-z]{10})\"");

	private final MockMvc mockMvc;

	RoomApiClient(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	/** 방을 만들고 방 코드와 방장 쿠키를 돌려준다. */
	TestRoom createRoom(String body) throws Exception {
		var request = post("/api/rooms");
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		MvcResult result = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
		Matcher matcher = CODE.matcher(result.getResponse().getContentAsString());
		assertThat(matcher.find()).as("응답에 방 코드가 있어야 한다").isTrue();
		return new TestRoom(matcher.group(1), cookieOf(result));
	}

	/** 새 참여자로 들어가서 그 사람의 쿠키를 돌려준다. */
	Cookie join(String code) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/rooms/" + code + "/participants"))
			.andExpect(status().isCreated())
			.andReturn();
		return cookieOf(result);
	}

	ResultActions nextPhase(String code, Cookie cookie, String from) throws Exception {
		return mockMvc.perform(post("/api/rooms/" + code + "/next-phase").cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"from\": \"" + from + "\"}"));
	}

	ResultActions propose(String code, Cookie cookie, String menuName) throws Exception {
		return mockMvc.perform(put("/api/rooms/" + code + "/me/proposal").cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"menuName\": " + jsonString(menuName) + "}"));
	}

	ResultActions allGood(String code, Cookie cookie) throws Exception {
		return mockMvc.perform(post("/api/rooms/" + code + "/me/all-good").cookie(cookie));
	}

	ResultActions getRoom(String code, Cookie cookie) throws Exception {
		return mockMvc.perform(get("/api/rooms/" + code).cookie(cookie));
	}

	ResultActions answerMerge(String code, Cookie cookie, String groupId, boolean agree) throws Exception {
		return mockMvc.perform(put("/api/rooms/" + code + "/me/merge-answers/" + groupId).cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"agree\": " + agree + "}"));
	}

	ResultActions finishMerge(String code, Cookie cookie) throws Exception {
		return mockMvc.perform(post("/api/rooms/" + code + "/me/merge-done").cookie(cookie));
	}

	ResultActions requestMerge(String code, Cookie cookie, String targetMenuId) throws Exception {
		return mockMvc.perform(post("/api/rooms/" + code + "/me/merge-requests").cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"targetMenuId\": " + jsonString(targetMenuId) + "}"));
	}

	/** 투표지를 JSON 그대로 낸다. 규칙을 어기는 투표지를 보내 볼 때 쓴다. */
	ResultActions ballot(String code, Cookie cookie, String json) throws Exception {
		return mockMvc.perform(put("/api/rooms/" + code + "/me/ballot").cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	/** 표를 낸다. 예: vote(code, cookie, Map.of(김밥 번호, 1, 라면 번호, -1)) */
	ResultActions vote(String code, Cookie cookie, Map<String, Integer> pointsByMenuId) throws Exception {
		String votes = pointsByMenuId.entrySet()
			.stream()
			.map(entry -> "{\"menuId\": \"" + entry.getKey() + "\", \"point\": " + entry.getValue() + "}")
			.collect(Collectors.joining(", "));
		return ballot(code, cookie, "{\"votes\": [" + votes + "]}");
	}

	/** 투표의 "다 좋음"(투표하지 않음) */
	ResultActions voteAllGood(String code, Cookie cookie) throws Exception {
		return ballot(code, cookie, "{\"allGood\": true}");
	}

	/** 남은 메뉴 중 이름이 name인 메뉴의 번호. 테스트에서는 이름이 겹치지 않게 만든다. */
	String menuIdNamed(String code, Cookie cookie, String name) throws Exception {
		List<String> ids = read(code, cookie, "$.room.menus[?(@.name == '" + name + "')].id");
		assertThat(ids).as("'" + name + "' 메뉴가 하나 있어야 한다").hasSize(1);
		return ids.getFirst();
	}

	/** 방을 조회해서 응답 본문(JSON)을 돌려준다. */
	String roomJson(String code, Cookie cookie) throws Exception {
		return getRoom(code, cookie).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 방을 조회해서 JSON 안의 값 하나를 꺼낸다. 예: read(code, cookie, "$.me.menuId") */
	<T> T read(String code, Cookie cookie, String jsonPath) throws Exception {
		return JsonPath.read(roomJson(code, cookie), jsonPath);
	}

	/**
	 * 각자 메뉴를 하나씩 내서 합치기 단계가 된 방을 만든다. menus[i]를 낸 사람이 person(i)이고, 첫 번째가 방장이다.
	 *
	 * @param createBody 방 만들기 요청 본문(단계 시간). null이면 모두 1분
	 */
	MergingRoom mergingRoom(String createBody, String... menus) throws Exception {
		TestRoom room = createRoom(createBody);
		List<Cookie> people = new ArrayList<>(List.of(room.host()));
		for (int i = 1; i < menus.length; i++) {
			people.add(join(room.code()));
		}
		nextPhase(room.code(), room.host(), "WAITING").andExpect(status().isOk());
		for (int i = 0; i < menus.length; i++) {
			propose(room.code(), people.get(i), menus[i]).andExpect(status().isOk());
		}
		// 모두 메뉴를 냈으므로 바로 합치기 단계로 넘어갔다.
		getRoom(room.code(), room.host()).andExpect(jsonPath("$.room.status").value("MERGING"));
		return new MergingRoom(room.code(), people);
	}

	/**
	 * 각자 메뉴를 하나씩 내고 모두 "합치기 끝냄"을 눌러 투표 단계가 된 방을 만든다. 메뉴 이름은 겹치지 않게 넣는다.
	 * menus[i]를 낸 사람이 person(i)이고, 첫 번째가 방장이다.
	 */
	VotingRoom votingRoom(String createBody, String... menus) throws Exception {
		MergingRoom merging = mergingRoom(createBody, menus);
		for (Cookie person : merging.people()) {
			finishMerge(merging.code(), person).andExpect(status().isOk());
		}
		getRoom(merging.code(), merging.host()).andExpect(jsonPath("$.room.status").value("VOTING"));
		return new VotingRoom(merging.code(), merging.people());
	}

	private static Cookie cookieOf(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie(ParticipantCookie.NAME);
		assertThat(cookie).as("참여자 쿠키를 받아야 한다").isNotNull();
		return new Cookie(cookie.getName(), cookie.getValue());
	}

	/** 테스트용 메뉴 이름을 JSON 문자열로 바꾼다. 따옴표, 역슬래시, 줄바꿈만 처리하면 충분하다. */
	private static String jsonString(String value) {
		if (value == null) {
			return "null";
		}
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t")
				+ "\"";
	}

	record TestRoom(String code, Cookie host) {
	}

	record MergingRoom(String code, List<Cookie> people) {

		Cookie host() {
			return people.getFirst();
		}

		Cookie person(int index) {
			return people.get(index);
		}

	}

	record VotingRoom(String code, List<Cookie> people) {

		Cookie host() {
			return people.getFirst();
		}

		Cookie person(int index) {
			return people.get(index);
		}

	}

}
