package com.whattoeat.room.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 합치기 그룹: "이 메뉴들을 합칠까요?"라는 질문 하나. 그룹에 든 메뉴를 낸 사람들이 각자 한 번씩 답한다.
 *
 * <ul>
 * <li>모두 답하면 바로 결과를 낸다. 시간이 다 되거나 방장이 넘기면 답하지 않은 사람은 "합치지 않기"로 본다.</li>
 * <li>동의한 메뉴가 2개 이상이면 그중 하나를 서버가 무작위로 대표 메뉴로 뽑고, 나머지 동의한 메뉴를 대표에 합친다.
 * 이름은 다시 정하지 않고 뽑힌 메뉴의 원래 이름을 쓴다(같은 제육이라도 가게마다 맛이 다르니까).</li>
 * <li>거절했거나 답하지 않은 사람의 메뉴는 따로 남는다.</li>
 * </ul>
 */
@Entity
@Table(name = "merge_group")
public class MergeGroup {

	/** 합칠 상대가 있으려면 메뉴가 2개 이상이어야 한다. 동의한 메뉴도 2개 이상이어야 합친다. */
	private static final int MIN_MENUS = 2;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false, updatable = false)
	private Room room;

	@Column(nullable = false, updatable = false, length = PublicId.LENGTH)
	private String publicId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, updatable = false, length = 20)
	private MergeGroupKind kind;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private MergeGroupStatus status;

	/** 합쳐졌을 때 남은 대표 메뉴. 결과가 안 나왔거나 합쳐지지 않았으면 비어 있다. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "representative_menu_id")
	private Menu representative;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant resolvedAt;

	@OneToMany(mappedBy = "mergeGroup", cascade = CascadeType.PERSIST)
	@OrderBy("id")
	private List<MergeGroupMember> members = new ArrayList<>();

	protected MergeGroup() {
	}

	private MergeGroup(Room room, MergeGroupKind kind, List<Menu> menus, Instant now) {
		if (menus.size() < MIN_MENUS || menus.stream().map(Menu::getId).distinct().count() != menus.size()) {
			throw new IllegalArgumentException("합치기 그룹에는 서로 다른 메뉴가 2개 이상 있어야 한다");
		}
		this.room = room;
		this.publicId = PublicId.generate();
		this.kind = kind;
		this.status = MergeGroupStatus.PENDING;
		this.createdAt = now;
		for (Menu menu : menus) {
			members.add(new MergeGroupMember(this, menu));
		}
	}

	/** 자동으로 묶은 그룹. 지금은 이름이 같은 메뉴끼리, 기능 6부터는 AI가 묶는다. */
	public static MergeGroup auto(Room room, List<Menu> menus, Instant now) {
		return new MergeGroup(room, MergeGroupKind.AUTO, menus, now);
	}

	/**
	 * 이 메뉴를 낸 사람의 답을 기록한다. 한 번 한 답은 바꿀 수 없다.
	 *
	 * @return 답이 새로 기록됐는지. 같은 답을 다시 보내면(버튼 두 번 누르기 등) 아무것도 바꾸지 않고 false
	 */
	public boolean answer(Menu menu, boolean agree, Instant now) {
		MergeGroupMember member = findMember(menu)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_MERGE_MEMBER));
		return member.answer(agree ? MergeAnswer.AGREED : MergeAnswer.DECLINED, now);
	}

	/** 이 메뉴를 낸 사람의 답을 아직 기다리는지. */
	public boolean isWaitingFor(Menu menu) {
		return findMember(menu).map(MergeGroupMember::isWaiting).orElse(false);
	}

	public boolean isEveryoneAnswered() {
		return members.stream().noneMatch(MergeGroupMember::isWaiting);
	}

	/**
	 * 결과를 낸다. 답하지 않은 사람은 "합치지 않기"로 본다.
	 * 동의한 메뉴가 2개 이상이면 그중 하나를 무작위로 대표로 뽑고, 나머지 동의한 메뉴를 대표에 합친다.
	 *
	 * @param random 대표를 뽑을 난수. 서버가 뽑아야 화면(클라이언트)에서 결과를 조작할 수 없다
	 */
	public void resolve(RandomGenerator random, Instant now) {
		if (status != MergeGroupStatus.PENDING) {
			// 서비스가 결과가 안 나온 그룹만 골라서 부르므로, 여기에 걸리면 코드의 실수다.
			throw new IllegalStateException("합치기 그룹 " + publicId + "은 이미 결과가 나왔다");
		}
		members.forEach(MergeGroupMember::declineIfWaiting);
		List<Menu> agreed = members.stream().filter(MergeGroupMember::isAgreed).map(MergeGroupMember::getMenu).toList();
		if (agreed.size() >= MIN_MENUS) {
			Menu chosen = agreed.get(random.nextInt(agreed.size()));
			agreed.stream().filter(menu -> !menu.getId().equals(chosen.getId())).forEach(menu -> menu.mergeInto(chosen));
			this.representative = chosen;
			this.status = MergeGroupStatus.MERGED;
		}
		else {
			this.status = MergeGroupStatus.NOT_MERGED;
		}
		this.resolvedAt = now;
	}

	public boolean isPending() {
		return status == MergeGroupStatus.PENDING;
	}

	/** 그룹에 든 메뉴들(합쳐져 사라진 메뉴 포함). */
	public List<Menu> getMenus() {
		return members.stream().map(MergeGroupMember::getMenu).toList();
	}

	/** 아직 답하지 않은 사람들의 메뉴. */
	public List<Menu> getWaitingMenus() {
		return members.stream().filter(MergeGroupMember::isWaiting).map(MergeGroupMember::getMenu).toList();
	}

	/** 답한 사람 수. 누가 어떻게 답했는지는 밖으로 내보내지 않는다. */
	public long getAnsweredCount() {
		return members.stream().filter(member -> !member.isWaiting()).count();
	}

	private Optional<MergeGroupMember> findMember(Menu menu) {
		return members.stream().filter(member -> member.isFor(menu)).findFirst();
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
	}

	public MergeGroupKind getKind() {
		return kind;
	}

	public MergeGroupStatus getStatus() {
		return status;
	}

	/** 합쳐졌을 때 남은 대표 메뉴. 아니면 null */
	public Menu getRepresentative() {
		return representative;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

}
