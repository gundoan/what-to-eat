package com.whattoeat.room.application;

import static com.whattoeat.room.application.Concurrently.runAllAtOnce;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import com.whattoeat.IntegrationTest;
import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.application.RoomResults.CreatedRoom;
import com.whattoeat.room.domain.MergeGroupStatus;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class MergeConcurrencyTest {

	@Autowired
	RoomService roomService;

	@Autowired
	RoomPhaseService phaseService;

	@Autowired
	ProposalService proposalService;

	@Autowired
	MergeService mergeService;

	@Test
	@DisplayName("그룹의 20명이 동시에 동의해도 결과가 바로 한 번 나와서, 대표 메뉴 하나만 남는다")
	void everyoneAgreeingAtOnceMergesExactlyOnce() throws Exception {
		MergingRoom room = mergingRoom(20, i -> "제육볶음");
		String groupId = state(room).mergeGroups().getFirst().id();

		List<Callable<Void>> answers = new ArrayList<>();
		for (String token : room.tokens()) {
			answers.add(() -> {
				mergeService.answer(room.code(), token, groupId, true);
				return null;
			});
		}
		runAllAtOnce(answers); // 하나라도 실패하면 예외로 테스트가 실패한다

		RoomState state = state(room);
		assertThat(state.mergeGroups().getFirst().status()).isEqualTo(MergeGroupStatus.MERGED);
		assertThat(state.menus()).hasSize(1);
		long mergedOwners = room.tokens()
			.stream()
			.filter(token -> roomService.getRoom(room.code(), token).me().mergedInto() != null)
			.count();
		assertThat(mergedOwners).isEqualTo(19);
	}

	@Test
	@DisplayName("50명이 동시에 합치기 끝냄을 눌러도 모두 성공하고, 투표 단계로 정확히 한 번 넘어간다")
	void everyoneFinishingAtOnceMovesOnExactlyOnce() throws Exception {
		MergingRoom room = mergingRoom(Room.MAX_PARTICIPANTS, i -> "메뉴" + i);

		List<Callable<Void>> finishes = new ArrayList<>();
		for (String token : room.tokens()) {
			finishes.add(() -> {
				mergeService.finish(room.code(), token);
				return null;
			});
		}
		runAllAtOnce(finishes);

		RoomState state = state(room);
		assertThat(state.status()).isEqualTo(RoomStatus.VOTING);
		assertThat(state.menus()).hasSize(Room.MAX_PARTICIPANTS);
	}

	@Test
	@DisplayName("10명이 같은 메뉴에 동시에 직접 합치기를 요청해도 하나만 성공하고, 나머지는 '진행 중인 합치기'로 거절된다")
	void concurrentRequestsToSameMenuCreateOnlyOne() throws Exception {
		MergingRoom room = mergingRoom(11, i -> "메뉴" + i);
		String target = roomService.getRoom(room.code(), room.tokens().getFirst()).me().menuId();
		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();

		List<Callable<Void>> requests = new ArrayList<>();
		for (String token : room.tokens().subList(1, 11)) {
			requests.add(requestCounting(room, token, target, succeeded, rejected));
		}
		runAllAtOnce(requests);

		assertThat(succeeded).hasValue(1);
		assertThat(rejected).hasValue(9);
		assertThat(state(room).mergeGroups()).hasSize(1);
	}

	@Test
	@DisplayName("두 메뉴가 서로에게 동시에 직접 합치기를 요청해도 요청은 하나만 생긴다")
	void crossRequestsCreateOnlyOne() throws Exception {
		MergingRoom room = mergingRoom(2, i -> "메뉴" + i);
		String first = roomService.getRoom(room.code(), room.tokens().get(0)).me().menuId();
		String second = roomService.getRoom(room.code(), room.tokens().get(1)).me().menuId();
		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();

		runAllAtOnce(List.of(requestCounting(room, room.tokens().get(0), second, succeeded, rejected),
				requestCounting(room, room.tokens().get(1), first, succeeded, rejected)));

		assertThat(succeeded).hasValue(1);
		assertThat(rejected).hasValue(1);
		assertThat(state(room).mergeGroups()).hasSize(1);
	}

	/** 요청을 보내고, 성공하면 succeeded를, "진행 중인 합치기"로 거절되면 rejected를 센다. 다른 예외는 테스트를 실패시킨다. */
	private Callable<Void> requestCounting(MergingRoom room, String token, String target, AtomicInteger succeeded,
			AtomicInteger rejected) {
		return () -> {
			try {
				mergeService.request(room.code(), token, target);
				succeeded.incrementAndGet();
			}
			catch (BusinessException e) {
				assertThat(e.errorCode()).isEqualTo(ErrorCode.MENU_BUSY);
				rejected.incrementAndGet();
			}
			return null;
		};
	}

	/** people명이 각자 메뉴를 내서 합치기 단계가 된 방. 첫 번째 토큰이 방장이다. */
	private MergingRoom mergingRoom(int people, IntFunction<String> menuOf) {
		CreatedRoom created = roomService.create(PhaseDurations.defaults());
		List<String> tokens = new ArrayList<>(List.of(created.token()));
		for (int i = 1; i < people; i++) {
			tokens.add(roomService.join(created.code(), null).newToken());
		}
		phaseService.advanceByHost(created.code(), created.token(), RoomStatus.WAITING, null);
		for (int i = 0; i < people; i++) {
			proposalService.propose(created.code(), tokens.get(i), menuOf.apply(i));
		}
		MergingRoom room = new MergingRoom(created.code(), tokens);
		assertThat(state(room).status()).isEqualTo(RoomStatus.MERGING);
		return room;
	}

	private RoomState state(MergingRoom room) {
		return roomService.getRoom(room.code(), room.tokens().getFirst()).room();
	}

	private record MergingRoom(String code, List<String> tokens) {
	}

}
