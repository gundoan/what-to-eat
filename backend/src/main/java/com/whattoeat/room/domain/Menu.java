package com.whattoeat.room.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 참여자가 제안한 메뉴. 한 사람은 한 방에서 메뉴를 하나만 낼 수 있다(DB의 UNIQUE 제약으로도 막는다).
 * 합치기에서 다른 메뉴에 합쳐지면 지우지 않고 대표 메뉴를 가리킨다(mergedInto). 합쳐진 메뉴는 목록에서 사라지고, 낸 사람은 투표권만 가진다.
 */
@Entity
@Table(name = "menu")
public class Menu {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false, updatable = false)
	private Room room;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "participant_id", nullable = false, updatable = false)
	private Participant participant;

	/** 화면에 내보내는 번호. DB 번호와 달리 올라온 순서가 드러나지 않는다. {@link PublicId} */
	@Column(nullable = false, updatable = false, length = PublicId.LENGTH)
	private String publicId;

	@Column(nullable = false, length = MenuName.MAX_LENGTH)
	private String name;

	/** 이 메뉴가 합쳐진 대표 메뉴. 합쳐지지 않았으면 비어 있다. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "merged_into_id")
	private Menu mergedInto;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Menu() {
	}

	private Menu(Room room, Participant participant, MenuName name, Instant now) {
		this.room = room;
		this.participant = participant;
		this.publicId = PublicId.generate();
		this.name = name.value();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static Menu propose(Room room, Participant participant, MenuName name, Instant now) {
		return new Menu(room, participant, name, now);
	}

	/** 다른 메뉴에 합쳐지지 않고 남은 메뉴들. 순서는 그대로 둔다. 투표는 이 메뉴들로 한다. */
	public static List<Menu> remaining(List<Menu> menus) {
		return menus.stream().filter(menu -> !menu.isMerged()).toList();
	}

	/** 메뉴들을 낸 사람(참여자 번호)으로 찾을 수 있게 모은다. 한 사람은 한 방에 메뉴가 많아야 하나다. */
	public static Map<Long, Menu> byParticipantId(List<Menu> menus) {
		return menus.stream().collect(Collectors.toMap(menu -> menu.getParticipant().getId(), Function.identity()));
	}

	public void rename(MenuName newName, Instant now) {
		this.name = newName.value();
		this.updatedAt = now;
	}

	/** 합치기 그룹에서 대표 메뉴로 뽑히지 않은 메뉴를 대표 메뉴에 합친다. */
	public void mergeInto(Menu representative) {
		if (mergedInto != null) {
			throw new IllegalStateException("메뉴 " + publicId + "은 이미 다른 메뉴에 합쳐졌다");
		}
		if (representative.getId().equals(id)) {
			throw new IllegalArgumentException("메뉴를 자기 자신에게 합칠 수 없다");
		}
		this.mergedInto = representative;
	}

	/** 다른 메뉴에 합쳐져서 목록에서 사라졌는지. */
	public boolean isMerged() {
		return mergedInto != null;
	}

	/**
	 * 이 메뉴가 최종적으로 들어간 메뉴. 합쳐지지 않았으면 자기 자신이다.
	 * 대표 메뉴도 나중에 직접 합치기로 다른 메뉴에 합쳐질 수 있어서(A → B, 다시 B → C), 끝까지 따라간다.
	 * 남아 있는 메뉴끼리만 합칠 수 있으므로 고리가 생기지 않는다.
	 */
	public Menu finalMenu() {
		Menu current = this;
		while (current.getMergedInto() != null) {
			current = current.getMergedInto();
		}
		return current;
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
	}

	public String getName() {
		return name;
	}

	/** 낸 사람. 서버 안에서만 쓰고 화면에는 절대 내보내지 않는다. */
	public Participant getParticipant() {
		return participant;
	}

	/** 이 메뉴가 합쳐진 대표 메뉴. 합쳐지지 않았으면 null */
	public Menu getMergedInto() {
		return mergedInto;
	}

}
