-- 직접 합치기 요청에서 요청받은 메뉴. 요청이 받아들여지면 이 메뉴가 대표로 남고, 요청한 메뉴가 여기에 합쳐진다.
-- 자동 그룹(kind = AUTO)은 요청한 쪽과 요청받은 쪽이 없으므로 비어 있다.
-- 직접 요청은 kind = DIRECT로 저장한다(kind는 VARCHAR라 테이블을 바꿀 필요가 없다).
ALTER TABLE merge_group
    ADD COLUMN target_menu_id BIGINT NULL,
    ADD CONSTRAINT fk_merge_group_target_menu FOREIGN KEY (target_menu_id) REFERENCES menu (id);
