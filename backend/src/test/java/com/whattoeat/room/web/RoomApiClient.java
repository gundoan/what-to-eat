package com.whattoeat.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

}
