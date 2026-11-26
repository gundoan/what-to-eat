-- 결정된 메뉴. 투표에서 1등이 하나로 정해지거나, 메뉴가 하나뿐이라 투표 없이 정해지면 채운다.
ALTER TABLE room
    ADD COLUMN result_menu_id BIGINT NULL,
    ADD CONSTRAINT fk_room_result_menu FOREIGN KEY (result_menu_id) REFERENCES menu (id);

-- 투표지: 한 사람이 한 판(vote_round)에 한 번 내는 투표. 1 = 투표, 2 = 재투표(기능 5).
-- all_good: "다 좋음"(투표하지 않음)을 눌렀는지. 참이면 이 투표지에는 표(vote)가 없다.
CREATE TABLE ballot
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    room_id        BIGINT      NOT NULL,
    participant_id BIGINT      NOT NULL,
    vote_round     INT         NOT NULL,
    all_good       BOOLEAN     NOT NULL,
    submitted_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 한 사람은 한 판에 한 번만 낸다. 코드에 실수가 있어도 DB가 두 번째 투표지를 막는다.
    CONSTRAINT uk_ballot_participant_round UNIQUE (participant_id, vote_round),
    CONSTRAINT fk_ballot_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_ballot_participant FOREIGN KEY (participant_id) REFERENCES participant (id),
    CONSTRAINT chk_ballot_round CHECK (vote_round IN (1, 2))
);

-- 표: 투표지에 적은 메뉴와 점수(+1 먹고 싶다 / -1 먹기 싫다).
-- "한 사람 최대 2표"는 행 개수에 대한 규칙이라 CHECK로 표현할 수 없어서, 앱이 방을 잠근 상태에서 지킨다.
CREATE TABLE vote
(
    id        BIGINT NOT NULL AUTO_INCREMENT,
    ballot_id BIGINT NOT NULL,
    menu_id   BIGINT NOT NULL,
    point     INT    NOT NULL,
    PRIMARY KEY (id),
    -- 한 투표지에서 한 메뉴에는 1표만 줄 수 있다.
    CONSTRAINT uk_vote_ballot_menu UNIQUE (ballot_id, menu_id),
    CONSTRAINT fk_vote_ballot FOREIGN KEY (ballot_id) REFERENCES ballot (id),
    CONSTRAINT fk_vote_menu FOREIGN KEY (menu_id) REFERENCES menu (id),
    CONSTRAINT chk_vote_point CHECK (point IN (-1, 1))
);
