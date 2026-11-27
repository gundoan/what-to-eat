-- 결정된 이름과 결정 이유.
-- 랜덤 선택에서 뽑힌 사람이 정한 이름은 메뉴 목록에 없을 수 있어서, 메뉴(result_menu_id)와 따로 이름을 저장한다.
-- result_reason: ONLY_MENU(메뉴가 하나) / VOTE(투표) / REVOTE(재투표) / REVOTE_RANDOM(재투표도 동점이라 무작위) / PICKED(뽑힌 사람이 정함)
ALTER TABLE room
    ADD COLUMN result_name   VARCHAR(30) NULL,
    ADD COLUMN result_reason VARCHAR(20) NULL;

-- 이미 끝난 방(로컬에서 써 본 데이터)에도 채운다.
-- 기능 4까지는 재투표와 랜덤 선택이 없었으므로, 투표지가 있으면 투표로, 없으면 메뉴가 하나라 정해진 것이다.
UPDATE room r
SET r.result_name   = (SELECT m.name FROM menu m WHERE m.id = r.result_menu_id),
    r.result_reason = CASE WHEN EXISTS (SELECT 1 FROM ballot b WHERE b.room_id = r.id) THEN 'VOTE' ELSE 'ONLY_MENU' END
WHERE r.status = 'FINISHED';

-- 뽑기 차례: 랜덤 선택에서 서버가 한 명을 뽑을 때마다 한 줄.
-- cycle_number: 몇 번째 바퀴인지. 한 바퀴 안에서는 이미 뽑힌 사람을 다시 뽑지 않고, 모두 뽑히면 다음 바퀴로 처음부터 뽑는다.
-- status: ACTIVE(정하는 중) / DECIDED(정함) / EXPIRED(시간 안에 안 정함) / SKIPPED(방장이 다른 사람을 뽑음)
CREATE TABLE pick_turn
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    room_id        BIGINT      NOT NULL,
    participant_id BIGINT      NOT NULL,
    turn_number    INT         NOT NULL,
    cycle_number   INT         NOT NULL,
    status         VARCHAR(20) NOT NULL,
    menu_name      VARCHAR(30) NULL,
    started_at     DATETIME(6) NOT NULL,
    finished_at    DATETIME(6) NULL,
    -- 정하는 중인 차례에만 방 번호가 들어가는 칸(DB가 계산한다). NULL은 UNIQUE에 걸리지 않으므로,
    -- "방마다 정하는 중인 차례는 하나뿐"이라는 규칙을 DB도 막는다.
    active_room_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN room_id END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uk_pick_turn_room_number UNIQUE (room_id, turn_number),
    CONSTRAINT uk_pick_turn_active UNIQUE (active_room_id),
    CONSTRAINT fk_pick_turn_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_pick_turn_participant FOREIGN KEY (participant_id) REFERENCES participant (id)
);

-- 기능 4에서는 재투표와 랜덤 선택이 자리만 있어서, 그 단계로 간 방(로컬에서 써 본 데이터)에 타이머도 뽑힌 사람도 없다.
-- 그대로 두면 랜덤 선택 중인 방은 아무도 정할 수 없고 다시 뽑을 수도 없어서 영원히 멈춘다.
-- 그래서 두 단계가 지금 막 시작된 것처럼 새 규칙에 맞춘다. 시각은 앱과 같이 UTC로 넣는다(NOW()는 DB 시간대를 따른다).

-- 재투표 중인 방: 투표 시간만큼 타이머를 건다.
UPDATE room
SET phase_started_at = UTC_TIMESTAMP(6),
    phase_ends_at    = UTC_TIMESTAMP(6) + INTERVAL vote_seconds SECOND
WHERE status = 'REVOTING';

-- 랜덤 선택 중인 방: 그 방 참여자 중 한 명을 뽑아 첫 차례를 만들고, 랜덤 선택 시간만큼 타이머를 건다.
-- 앱은 SecureRandom으로 뽑지만, 여기서는 옛날 방을 한 번 옮기는 것뿐이라 DB의 RAND()로 충분하다.
-- 방장은 방을 만들 때 함께 들어가므로, 방마다 참여자가 적어도 한 명 있다.
INSERT INTO pick_turn (room_id, participant_id, turn_number, cycle_number, status, started_at)
SELECT r.id,
       (SELECT p.id FROM participant p WHERE p.room_id = r.id ORDER BY RAND() LIMIT 1),
       1,
       1,
       'ACTIVE',
       UTC_TIMESTAMP(6)
FROM room r
WHERE r.status = 'RANDOM_PICK';

UPDATE room
SET phase_started_at = UTC_TIMESTAMP(6),
    phase_ends_at    = UTC_TIMESTAMP(6) + INTERVAL random_pick_seconds SECOND
WHERE status = 'RANDOM_PICK';
