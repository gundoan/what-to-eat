package com.whattoeat.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

}
