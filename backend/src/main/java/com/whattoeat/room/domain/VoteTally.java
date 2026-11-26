package com.whattoeat.room.domain;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 판의 투표 결과. 후보 메뉴마다 +1과 -1을 센다.
 *
 * <ul>
 * <li>총점(+1 개수 - -1 개수)이 가장 높은 메뉴가 1등이다. 전부 마이너스여도 가장 높은(덜 싫은) 메뉴가 1등이다.</li>
 * <li>표를 하나도 못 받은 메뉴는 0점이다. 그래서 다른 메뉴가 모두 마이너스면 표를 못 받은 메뉴가 1등이 될 수 있다.</li>
 * <li>"다 좋음" 투표지는 표가 없으니 점수에 영향이 없다. 일부만 "다 좋음"을 누르면 나머지 사람들의 표로 정해진다.</li>
 * </ul>
 */
public final class VoteTally {

	/** 후보 메뉴의 점수. 후보 순서(가나다순) 그대로다. */
	private final List<MenuScore> scores;

	/** 후보 메뉴에 들어온 표의 수 */
	private final int voteCount;

	private VoteTally(List<MenuScore> scores, int voteCount) {
		this.scores = scores;
		this.voteCount = voteCount;
	}

	/**
	 * @param candidates 투표할 수 있었던 메뉴들(가나다순)
	 * @param ballots 이 판의 투표지들
	 */
	public static VoteTally count(List<Menu> candidates, List<Ballot> ballots) {
		Map<Long, int[]> plusAndMinusByMenuId = new HashMap<>();
		for (Menu candidate : candidates) {
			plusAndMinusByMenuId.put(candidate.getId(), new int[2]);
		}
		int voteCount = 0;
		for (Ballot ballot : ballots) {
			for (Vote vote : ballot.getVotes()) {
				int[] counts = plusAndMinusByMenuId.get(vote.getMenu().getId());
				if (counts == null) {
					continue; // 후보가 아닌 메뉴의 표는 세지 않는다. 표를 받을 때 이미 막으므로 생기지 않는다.
				}
				counts[vote.getPoint() == VoteChoice.LIKE ? 0 : 1]++;
				voteCount++;
			}
		}
		List<MenuScore> scores = candidates.stream().map(menu -> {
			int[] counts = plusAndMinusByMenuId.get(menu.getId());
			return new MenuScore(menu, counts[0], counts[1]);
		}).toList();
		return new VoteTally(scores, voteCount);
	}

	/** 아무도 표를 내지 않았는지(모두 "다 좋음"을 눌렀거나 아무도 투표하지 않음). 이때는 랜덤 선택으로 간다. */
	public boolean hasNoVotes() {
		return voteCount == 0;
	}

	/** 총점이 가장 높은 메뉴들. 하나면 그 메뉴로 결정하고, 둘 이상이면 동점이다. 표가 없으면 비어 있다. */
	public List<Menu> leaders() {
		if (hasNoVotes()) {
			return List.of();
		}
		int best = scores.stream().mapToInt(MenuScore::total).max().orElseThrow();
		return scores.stream().filter(score -> score.total() == best).map(MenuScore::menu).toList();
	}

	/** 화면에 보여줄 순서: 총점이 높은 순, 같으면 원래 순서(가나다순). */
	public List<MenuScore> ranking() {
		return scores.stream().sorted(Comparator.comparingInt(MenuScore::total).reversed()).toList();
	}

	/**
	 * @param plus +1을 받은 수
	 * @param minus -1을 받은 수
	 */
	public record MenuScore(Menu menu, int plus, int minus) {

		public int total() {
			return plus - minus;
		}

	}

}
