package com.whattoeat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * V6 마이그레이션이 이미 있던 방(기능 4까지의 코드가 만든 데이터)을 새 규칙에 맞게 옮기는지 확인한다.
 * 다른 통합 테스트는 빈 DB에 V6까지 한 번에 올리므로, 옮기는 SQL이 바꿀 데이터가 하나도 없다.
 * 그래서 이 테스트만 쓰는 MySQL을 따로 띄워 V5까지 올리고, 기능 4 시절의 방을 넣은 뒤 V6을 올려서 본다.
 */
@Testcontainers
class V6MigrationTest {

	@Container
	static final MySQLContainer mysql = new MySQLContainer(TestcontainersConfiguration.MYSQL_IMAGE);

	/** 테스트 데이터의 시각. 기능 4 시절에 만들어진 방이다. */
	static final LocalDateTime BEFORE = LocalDateTime.of(2026, 11, 20, 12, 0);

	/** 투표 중인 방에 걸려 있던 타이머. V6이 건드리면 안 된다. */
	static final LocalDateTime VOTING_ENDS_AT = BEFORE.plusMinutes(1);

	static JdbcTemplate db;

	@BeforeAll
	static void migrateFeature4Rooms() {
		db = new JdbcTemplate(
				new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
		migrateTo("5");

		// 1번 방: 투표로 끝남(김밥 +1)
		room(1, "FINVOTE001", "FINISHED", null);
		participant(11, 1, true);
		participant(12, 1, false);
		menu(101, 1, 11, "김밥");
		menu(102, 1, 12, "라면");
		ballot(1001, 1, 11, 101, 1);
		db.update("update room set result_menu_id = 101 where id = 1");

		// 2번 방: 메뉴가 하나라 투표 없이 끝남
		room(2, "FINONLY002", "FINISHED", null);
		participant(21, 2, true);
		menu(201, 2, 21, "떡볶이");
		db.update("update room set result_menu_id = 201 where id = 2");

		// 3번 방: 아무도 제안하지 않아 랜덤 선택으로 감. 기능 4는 타이머도 뽑힌 사람도 만들지 않았다
		room(3, "RANDPICK03", "RANDOM_PICK", null);
		participant(31, 3, true);
		participant(32, 3, false);
		participant(33, 3, false);

		// 4번 방: 동점이라 재투표로 감. 기능 4는 타이머를 걸지 않았다
		room(4, "REVOTING04", "REVOTING", null);
		participant(41, 4, true);
		participant(42, 4, false);
		menu(401, 4, 41, "김밥");
		menu(402, 4, 42, "라면");
		ballot(4001, 4, 41, 401, 1);
		ballot(4002, 4, 42, 402, 1);

		// 5번 방: 투표 중, 6번 방: 대기 중
		room(5, "VOTINGNOW5", "VOTING", VOTING_ENDS_AT);
		participant(51, 5, true);
		room(6, "WAITING006", "WAITING", null);
		participant(61, 6, true);

		migrateTo("6");
	}

	@Test
	@DisplayName("끝난 방은 결정된 메뉴 이름을 채우고, 투표지가 있으면 투표로, 없으면 메뉴가 하나라서 정해진 것으로 본다")
	void fillsResultOfFinishedRooms() {
		assertThat(result(1)).containsEntry("result_name", "김밥").containsEntry("result_reason", "VOTE");
		assertThat(result(2)).containsEntry("result_name", "떡볶이").containsEntry("result_reason", "ONLY_MENU");
	}

	@Test
	@DisplayName("랜덤 선택 중이던 방은 그 방 참여자 한 명을 뽑아 첫 차례를 만들고, 랜덤 선택 시간(90초)만큼 타이머를 건다")
	void startsRandomPickOfStuckRooms() {
		List<Map<String, Object>> turns = db
			.queryForList("select participant_id, turn_number, cycle_number, status from pick_turn where room_id = 3");

		assertThat(turns).hasSize(1);
		assertThat(turns.getFirst()).containsEntry("turn_number", 1)
			.containsEntry("cycle_number", 1)
			.containsEntry("status", "ACTIVE");
		assertThat(turns.getFirst().get("participant_id")).isIn(31L, 32L, 33L);
		assertThat(timerSeconds(3)).isEqualTo(90);
	}

	@Test
	@DisplayName("재투표 중이던 방은 투표 시간(120초)만큼 타이머를 건다")
	void startsRevoteTimer() {
		assertThat(timerSeconds(4)).isEqualTo(120);
		assertThat(db.queryForObject("select count(*) from pick_turn where room_id = 4", Long.class)).isZero();
	}

	@Test
	@DisplayName("다른 단계의 방은 건드리지 않는다")
	void leavesOtherRoomsAlone() {
		assertThat(phaseEndsAt(5)).isEqualTo(VOTING_ENDS_AT);
		assertThat(result(5)).containsEntry("result_name", null).containsEntry("result_reason", null);
		assertThat(phaseEndsAt(6)).isNull();
		assertThat(db.queryForObject("select count(*) from pick_turn where room_id <> 3", Long.class))
			.as("랜덤 선택 중이던 3번 방 말고는 뽑기 차례가 생기지 않는다")
			.isZero();
	}

	private static void migrateTo(String version) {
		Flyway.configure()
			.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
			.target(version)
			.load()
			.migrate();
	}

	/** 단계별 시간: 제안·합치기 60초, 투표 120초, 랜덤 선택 90초 */
	private static void room(long id, String code, String status, LocalDateTime phaseEndsAt) {
		db.update("""
				insert into room (id, code, status, proposal_seconds, merge_seconds, vote_seconds, random_pick_seconds,
				                  created_at, phase_started_at, phase_ends_at)
				values (?, ?, ?, 60, 60, 120, 90, ?, ?, ?)
				""", id, code, status, BEFORE, BEFORE, phaseEndsAt);
	}

	private static void participant(long id, long roomId, boolean host) {
		db.update("insert into participant (id, room_id, token_hash, host, joined_at) values (?, ?, ?, ?, ?)", id,
				roomId, "hash-" + id, host, BEFORE);
	}

	private static void menu(long id, long roomId, long participantId, String name) {
		db.update("""
				insert into menu (id, room_id, participant_id, name, public_id, created_at, updated_at)
				values (?, ?, ?, ?, uuid(), ?, ?)
				""", id, roomId, participantId, name, BEFORE, BEFORE);
	}

	/** 첫 투표(vote_round 1)에 메뉴 하나에 표 하나를 준 투표지 */
	private static void ballot(long id, long roomId, long participantId, long menuId, int point) {
		db.update("""
				insert into ballot (id, room_id, participant_id, vote_round, all_good, submitted_at)
				values (?, ?, ?, 1, false, ?)
				""", id, roomId, participantId, BEFORE);
		db.update("insert into vote (ballot_id, menu_id, point) values (?, ?, ?)", id, menuId, point);
	}

	private static Map<String, Object> result(long roomId) {
		return db.queryForMap("select result_name, result_reason from room where id = ?", roomId);
	}

	private static LocalDateTime phaseEndsAt(long roomId) {
		return db.queryForObject("select phase_ends_at from room where id = ?", LocalDateTime.class, roomId);
	}

	/** 단계가 시작된 시각부터 끝나는 시각까지 몇 초인지 */
	private static Long timerSeconds(long roomId) {
		return db.queryForObject(
				"select timestampdiff(second, phase_started_at, phase_ends_at) from room where id = ?", Long.class,
				roomId);
	}

}
