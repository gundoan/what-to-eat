-- 지금 단계가 언제 시작해서 언제 끝나는지. 대기 중처럼 시간 제한이 없는 단계에서는 비어 있다.
ALTER TABLE room
    ADD COLUMN phase_started_at DATETIME(6) NULL,
    ADD COLUMN phase_ends_at    DATETIME(6) NULL;

-- 1초마다 "시간이 다 된 방"을 찾는 조회를 빠르게 한다.
CREATE INDEX idx_room_phase_ends_at ON room (phase_ends_at);

-- 제안 단계에서 "다 좋음"을 눌렀는지. 메뉴를 냈거나 이 값이 참이면 제안 단계를 끝낸 것으로 본다.
ALTER TABLE participant
    ADD COLUMN proposal_all_good BOOLEAN NOT NULL DEFAULT FALSE;

-- 제안된 메뉴. 한 사람은 한 방에서 메뉴를 하나만 낼 수 있다(participant_id UNIQUE).
CREATE TABLE menu
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    room_id        BIGINT      NOT NULL,
    participant_id BIGINT      NOT NULL,
    name           VARCHAR(30) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_menu_participant UNIQUE (participant_id),
    CONSTRAINT fk_menu_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_menu_participant FOREIGN KEY (participant_id) REFERENCES participant (id),
    CONSTRAINT chk_menu_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0)
);
