-- 단계 시간에 "제한 없음"을 허용한다. 0초가 "제한 없음"이다.
-- 0초짜리 단계는 원래 있을 수 없는 값이라 다른 뜻과 헷갈리지 않고, API와 서버 코드도 같은 값(0)을 쓴다.
-- 제한 없는 단계는 끝나는 시각(phase_ends_at)이 없어서, 모두 끝내거나 방장이 넘길 때만 넘어간다.
-- 기존 방의 값(30초~10분)은 새 규칙에도 맞으므로 바꿀 데이터는 없다.
ALTER TABLE room
    DROP CHECK chk_room_proposal_seconds,
    DROP CHECK chk_room_merge_seconds,
    DROP CHECK chk_room_vote_seconds,
    DROP CHECK chk_room_random_pick_seconds;

ALTER TABLE room
    ADD CONSTRAINT chk_room_proposal_seconds
        CHECK (proposal_seconds = 0 OR (proposal_seconds BETWEEN 30 AND 600 AND proposal_seconds % 30 = 0)),
    ADD CONSTRAINT chk_room_merge_seconds
        CHECK (merge_seconds = 0 OR (merge_seconds BETWEEN 30 AND 600 AND merge_seconds % 30 = 0)),
    ADD CONSTRAINT chk_room_vote_seconds
        CHECK (vote_seconds = 0 OR (vote_seconds BETWEEN 30 AND 600 AND vote_seconds % 30 = 0)),
    ADD CONSTRAINT chk_room_random_pick_seconds
        CHECK (random_pick_seconds = 0 OR (random_pick_seconds BETWEEN 30 AND 600 AND random_pick_seconds % 30 = 0));
