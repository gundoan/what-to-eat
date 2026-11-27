-- 단계 시간을 60분(3600초)까지 정할 수 있게 한다. 방 만들기 화면에 30분과 "직접 설정"(1~60분)이 생겨서다.
-- 0(제한 없음)과 30초 단위 규칙은 그대로다.
-- 기존 방의 값(0, 또는 30초~10분)은 새 규칙에도 맞으므로 바꿀 데이터는 없다.
ALTER TABLE room
    DROP CHECK chk_room_proposal_seconds,
    DROP CHECK chk_room_merge_seconds,
    DROP CHECK chk_room_vote_seconds,
    DROP CHECK chk_room_random_pick_seconds;

ALTER TABLE room
    ADD CONSTRAINT chk_room_proposal_seconds
        CHECK (proposal_seconds = 0 OR (proposal_seconds BETWEEN 30 AND 3600 AND proposal_seconds % 30 = 0)),
    ADD CONSTRAINT chk_room_merge_seconds
        CHECK (merge_seconds = 0 OR (merge_seconds BETWEEN 30 AND 3600 AND merge_seconds % 30 = 0)),
    ADD CONSTRAINT chk_room_vote_seconds
        CHECK (vote_seconds = 0 OR (vote_seconds BETWEEN 30 AND 3600 AND vote_seconds % 30 = 0)),
    ADD CONSTRAINT chk_room_random_pick_seconds
        CHECK (random_pick_seconds = 0 OR (random_pick_seconds BETWEEN 30 AND 3600 AND random_pick_seconds % 30 = 0));
