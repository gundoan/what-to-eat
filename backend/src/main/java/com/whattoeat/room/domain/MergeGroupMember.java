package com.whattoeat.room.domain;

import java.time.Instant;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 합치기 그룹에 든 메뉴 하나와, 그 메뉴를 낸 사람의 답. {@link MergeGroup}을 통해서만 바꾼다.
 */
@Entity
@Table(name = "merge_group_member")
public class MergeGroupMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "merge_group_id", nullable = false, updatable = false)
	private MergeGroup mergeGroup;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "menu_id", nullable = false, updatable = false)
	private Menu menu;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private MergeAnswer answer;

	/** 답한 시각. 시간이 다 되거나 방장이 넘겨서 "합치지 않기"로 처리된 경우에는 비어 있다. */
	private Instant answeredAt;

	protected MergeGroupMember() {
	}

	MergeGroupMember(MergeGroup mergeGroup, Menu menu) {
		this.mergeGroup = mergeGroup;
		this.menu = menu;
		this.answer = MergeAnswer.WAITING;
	}

	/**
	 * 답을 기록한다. 한 번 한 답은 바꿀 수 없다.
	 *
	 * @return 답이 새로 기록됐는지. 같은 답을 다시 보내면(버튼 두 번 누르기 등) 아무것도 바꾸지 않고 false
	 */
	boolean answer(MergeAnswer newAnswer, Instant now) {
		if (answer == newAnswer) {
			return false;
		}
		if (answer != MergeAnswer.WAITING) {
			throw new BusinessException(ErrorCode.ALREADY_ANSWERED);
		}
		this.answer = newAnswer;
		this.answeredAt = now;
		return true;
	}

	/** 답하지 않았으면 "합치지 않기"로 본다. 본인이 답한 것이 아니므로 답한 시각은 비워 둔다. */
	void declineIfWaiting() {
		if (answer == MergeAnswer.WAITING) {
			this.answer = MergeAnswer.DECLINED;
		}
	}

	boolean isWaiting() {
		return answer == MergeAnswer.WAITING;
	}

	boolean isAgreed() {
		return answer == MergeAnswer.AGREED;
	}

	boolean isFor(Menu other) {
		return menu.getId().equals(other.getId());
	}

	Menu getMenu() {
		return menu;
	}

	MergeAnswer getAnswer() {
		return answer;
	}

}
