-- 메뉴의 공개 번호와 "어느 메뉴로 합쳐졌는지".
-- 공개 번호: 화면에 DB 번호(1, 2, 3...)를 보여주면 올라온 순서가 드러나서, 순서가 없는 무작위 UUID를 따로 둔다.
-- merged_into_id: 합쳐진 메뉴는 지우지 않고 대표 메뉴를 가리키게 한다. 비어 있으면 아직 남아 있는 메뉴다.
ALTER TABLE menu
    ADD COLUMN public_id      VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN merged_into_id BIGINT NULL;

-- 이미 있는 메뉴(로컬에서 써 본 데이터)에도 번호를 채운다. 새 메뉴는 앱이 무작위 UUID를 만든다.
UPDATE menu SET public_id = UUID() WHERE public_id IS NULL;

ALTER TABLE menu
    MODIFY COLUMN public_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT uk_menu_public_id UNIQUE (public_id),
    ADD CONSTRAINT fk_menu_merged_into FOREIGN KEY (merged_into_id) REFERENCES menu (id);

-- 합치기 단계에서 "합치기 끝냄"을 눌렀는지.
ALTER TABLE participant
    ADD COLUMN merge_done BOOLEAN NOT NULL DEFAULT FALSE;

-- 합치기 그룹: "이 메뉴들을 합칠까요?"라는 질문 하나.
-- kind: AUTO(자동으로 묶음. 지금은 이름이 같은 메뉴, 나중에는 AI). 직접 합치기 요청이 생기면 종류가 늘어난다.
-- status: PENDING(답을 기다림) → MERGED(합쳐짐) 또는 NOT_MERGED(안 합쳐짐)
CREATE TABLE merge_group
(
    id                     BIGINT      NOT NULL AUTO_INCREMENT,
    room_id                BIGINT      NOT NULL,
    public_id              VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kind                   VARCHAR(20) NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    representative_menu_id BIGINT      NULL,
    created_at             DATETIME(6) NOT NULL,
    resolved_at            DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_merge_group_public_id UNIQUE (public_id),
    CONSTRAINT fk_merge_group_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_merge_group_representative FOREIGN KEY (representative_menu_id) REFERENCES menu (id)
);

-- 그룹에 든 메뉴와, 그 메뉴를 낸 사람의 답.
-- answer: WAITING(아직 답 안 함) / AGREED(합치기) / DECLINED(합치지 않기)
-- answered_at: 답한 시각. 시간이 다 되거나 방장이 넘겨서 "합치지 않기"로 처리된 경우에는 비어 있다.
CREATE TABLE merge_group_member
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    merge_group_id BIGINT      NOT NULL,
    menu_id        BIGINT      NOT NULL,
    answer         VARCHAR(20) NOT NULL,
    answered_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_merge_group_member UNIQUE (merge_group_id, menu_id),
    CONSTRAINT fk_merge_group_member_group FOREIGN KEY (merge_group_id) REFERENCES merge_group (id),
    CONSTRAINT fk_merge_group_member_menu FOREIGN KEY (menu_id) REFERENCES menu (id)
);
