-- 방. code는 링크에 들어가는 무작위 문자열이라 대소문자를 구분해야 한다(기본 정렬은 대소문자를 구분하지 않음).
CREATE TABLE room
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    code                VARCHAR(10)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    proposal_seconds    INT          NOT NULL,
    merge_seconds       INT          NOT NULL,
    vote_seconds        INT          NOT NULL,
    random_pick_seconds INT          NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_room_code UNIQUE (code),
    -- 단계 시간 규칙(30초~10분, 30초 단위)을 애플리케이션과 DB 양쪽에서 막는다.
    CONSTRAINT chk_room_proposal_seconds CHECK (proposal_seconds BETWEEN 30 AND 600 AND proposal_seconds % 30 = 0),
    CONSTRAINT chk_room_merge_seconds CHECK (merge_seconds BETWEEN 30 AND 600 AND merge_seconds % 30 = 0),
    CONSTRAINT chk_room_vote_seconds CHECK (vote_seconds BETWEEN 30 AND 600 AND vote_seconds % 30 = 0),
    CONSTRAINT chk_room_random_pick_seconds CHECK (random_pick_seconds BETWEEN 30 AND 600 AND random_pick_seconds % 30 = 0)
);

-- 참여자. 브라우저에 준 토큰의 원본은 저장하지 않고 SHA-256 해시만 저장한다(DB가 유출돼도 참여자 행세를 못 하게).
CREATE TABLE participant
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    room_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    host       BOOLEAN     NOT NULL,
    joined_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_participant_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_participant_room FOREIGN KEY (room_id) REFERENCES room (id)
);
