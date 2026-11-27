package com.whattoeat.room.application;

import static com.whattoeat.room.application.Concurrently.runAllAtOnce;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import com.whattoeat.IntegrationTest;
import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.application.RoomResults.CreatedRoom;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.PickTurn;
import com.whattoeat.room.domain.PickTurnRepository;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomRepository;
import com.whattoeat.room.domain.RoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 랜덤 선택에서 시간 초과로 다시 뽑기(한 바퀴까지만), 그리고 여러 일이 동시에 일어날 때 결과가 하나만 나오는지.
 */
@IntegrationTest
class RandomPickTest {

	@Autowired
	RoomService roomService;

	@Autowired
	RoomPhaseService phaseService;

	@Autowired
	ProposalService proposalService;

	@Autowired
	PickService pickService;

	@Autowired
	RoomRepository roomRepository;

	@Autowired
	PickTurnRepository pickTurnRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("뽑힌 사람이 시간 안에 정하지 않으면 다른 사람을 다시 뽑고, 새로 뽑힌 사람에게 시간을 처음부터 준다")
	void redrawsWhenTimeIsUp() {
		PickRoom room = randomPickRoom(2);
		String first = picker(room);
		Instant deadline = room(room).getPhaseEndsAt();

		phaseService.advanceExpiredPhases(deadline.minusMillis(1));
		assertThat(picker(room)).isEqualTo(first);

		phaseService.advanceExpiredPhases(deadline);

		assertThat(picker(room)).as("한 바퀴 안에서는 이미 뽑힌 사람을 다시 뽑지 않는다").isNotEqualTo(first);
		assertThat(state(room).pickTurn()).isEqualTo(2);
		assertThat(room(room).getPhaseEndsAt()).isEqualTo(deadline.plusSeconds(PhaseDurations.DEFAULT_SECONDS));
	}

	@Test
	@DisplayName("모두 한 번씩 뽑혔는데 아무도 안 정하면 더는 자동으로 다시 뽑지 않고, 마지막에 뽑힌 사람은 시간 제한 없이 정할 수 있다")
	void stopsRedrawingAfterOneCycle() {
		PickRoom room = randomPickRoom(2);
		phaseService.advanceExpiredPhases(room(room).getPhaseEndsAt()); // 첫 사람 시간 초과 → 두 번째 사람
		String last = picker(room);
		Instant lastDeadline = room(room).getPhaseEndsAt();

		phaseService.advanceExpiredPhases(lastDeadline); // 두 번째 사람도 시간 초과 → 모두 한 번씩 뽑혔다

		assertThat(state(room).pickTurn()).as("다시 뽑지 않았다").isEqualTo(2);
		assertThat(room(room).getPhaseEndsAt()).as("타이머가 멈췄다").isNull();
		assertThat(room(room).isPhaseExpired(lastDeadline.plus(Duration.ofDays(1)))).as("하루가 지나도 시간 초과가 없다")
			.isFalse();
		assertThat(picker(room)).as("마지막에 뽑힌 사람이 그대로 정할 차례다").isEqualTo(last);

		pickService.decide(room.code(), last, "김밥");

		assertThat(state(room).status()).isEqualTo(RoomStatus.FINISHED);
		assertThat(state(room).result().name()).isEqualTo("김밥");
	}

	@Test
	@DisplayName("자동 다시 뽑기가 멈춘 뒤 방장이 다른 사람 뽑기를 누르면 다음 바퀴가 시작되고, 시간 제한도 다시 생긴다")
	void hostStartsNextCycleAfterStop() {
		PickRoom room = randomPickRoom(2);
		phaseService.advanceExpiredPhases(room(room).getPhaseEndsAt());
		phaseService.advanceExpiredPhases(room(room).getPhaseEndsAt());
		assertThat(room(room).getPhaseEndsAt()).isNull();

		phaseService.advanceByHost(room.code(), room.host(), RoomStatus.RANDOM_PICK, 2);

		assertThat(state(room).pickTurn()).isEqualTo(3);
		assertThat(room(room).getPhaseEndsAt()).isNotNull();
		assertThat(pickTurnRepository.findByRoomOrderByTurnNumber(room(room)).getLast().getCycle()).isEqualTo(2);
		picker(room); // 새 바퀴에서 누군가 뽑혀 있다
	}

	@Test
	@DisplayName("뽑힌 사람이 정하는 순간 시간이 다 돼도, 정해지거나 다시 뽑히거나 둘 중 하나만 일어난다 (10번 반복)")
	void decideAndTimeoutNeverBothHappen() throws Exception {
		for (int trial = 0; trial < 10; trial++) {
			PickRoom room = randomPickRoom(2);
			String token = picker(room);
			Instant deadline = room(room).getPhaseEndsAt();
			AtomicInteger decided = new AtomicInteger();

			runAllAtOnce(List.of(() -> {
				try {
					pickService.decide(room.code(), token, "김밥");
					decided.incrementAndGet();
				}
				catch (BusinessException e) {
					// 시간 초과로 먼저 다시 뽑혔다. 더는 뽑힌 사람이 아니다.
					assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_PICKER);
				}
				return null;
			}, () -> {
				phaseService.advanceExpiredPhases(deadline);
				return null;
			}));

			RoomState state = state(room);
			List<PickTurn> turns = pickTurnRepository.findByRoomOrderByTurnNumber(room(room));
			if (decided.get() == 1) {
				assertThat(state.status()).isEqualTo(RoomStatus.FINISHED);
				assertThat(turns).as("정해졌으면 다시 뽑지 않았다").hasSize(1);
			}
			else {
				assertThat(state.status()).isEqualTo(RoomStatus.RANDOM_PICK);
				assertThat(turns).as("다시 뽑혔으면 정해지지 않았다").hasSize(2);
			}
		}
	}

	@Test
	@DisplayName("방장이 다른 사람 뽑기를 동시에 두 번 눌러도 한 번만 다시 뽑는다")
	void concurrentSkipsRedrawOnce() throws Exception {
		PickRoom room = randomPickRoom(3);
		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();

		Callable<Void> skip = () -> {
			try {
				phaseService.advanceByHost(room.code(), room.host(), RoomStatus.RANDOM_PICK, 1);
				succeeded.incrementAndGet();
			}
			catch (BusinessException e) {
				assertThat(e.errorCode()).isEqualTo(ErrorCode.PHASE_ALREADY_CHANGED);
				rejected.incrementAndGet();
			}
			return null;
		};
		runAllAtOnce(List.of(skip, skip));

		assertThat(succeeded).hasValue(1);
		assertThat(rejected).hasValue(1);
		assertThat(state(room).pickTurn()).isEqualTo(2);
	}

	@Test
	@DisplayName("방마다 정하는 중인 뽑기 차례는 하나뿐이라는 규칙을 DB도 막는다")
	void databaseAllowsOneActiveTurnPerRoom() {
		PickRoom room = randomPickRoom(2);
		Room entity = room(room);
		Long someoneElse = jdbcTemplate.queryForObject(
				"select id from participant where room_id = ? order by id desc limit 1", Long.class, entity.getId());

		assertThatThrownBy(() -> jdbcTemplate.update("""
				insert into pick_turn (room_id, participant_id, turn_number, cycle_number, status, started_at)
				values (?, ?, 99, 1, 'ACTIVE', now(6))
				""", entity.getId(), someoneElse)).isInstanceOf(DataIntegrityViolationException.class);
	}

	/** people명이 들어와 모두 "다 좋음"을 눌러 랜덤 선택이 된 방. 첫 번째 토큰이 방장이다. */
	private PickRoom randomPickRoom(int people) {
		CreatedRoom created = roomService.create(PhaseDurations.defaults());
		List<String> tokens = new ArrayList<>(List.of(created.token()));
		for (int i = 1; i < people; i++) {
			tokens.add(roomService.join(created.code(), null).newToken());
		}
		phaseService.advanceByHost(created.code(), created.token(), RoomStatus.WAITING, null);
		for (String token : tokens) {
			proposalService.allGood(created.code(), token);
		}
		PickRoom room = new PickRoom(created.code(), tokens);
		assertThat(state(room).status()).isEqualTo(RoomStatus.RANDOM_PICK);
		return room;
	}

	/** 지금 뽑혀서 메뉴를 정해야 하는 사람의 토큰 */
	private String picker(PickRoom room) {
		List<String> pickers = room.tokens()
			.stream()
			.filter(token -> roomService.getRoom(room.code(), token).me().picking())
			.toList();
		assertThat(pickers).hasSize(1);
		return pickers.getFirst();
	}

	private RoomState state(PickRoom room) {
		return roomService.getRoom(room.code(), room.host()).room();
	}

	private Room room(PickRoom room) {
		return roomRepository.findByCode(room.code()).orElseThrow();
	}

	private record PickRoom(String code, List<String> tokens) {

		String host() {
			return tokens.getFirst();
		}

	}

}
