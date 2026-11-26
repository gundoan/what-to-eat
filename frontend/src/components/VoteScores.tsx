import type { MenuScore } from '../types/room'

type Props = {
  scores: MenuScore[]
}

/** 메뉴별 점수(총점이 높은 순). 투표가 끝난 뒤에만 보인다. 누가 찍었는지는 없다. */
export function VoteScores({ scores }: Props) {
  return (
    <table className="scores">
      <thead>
        <tr>
          <th>메뉴</th>
          <th>+1</th>
          <th>-1</th>
          <th>총점</th>
        </tr>
      </thead>
      <tbody>
        {scores.map((score) => (
          <tr key={score.menu.id}>
            <td>{score.menu.name}</td>
            <td>{score.plus}</td>
            <td>{score.minus}</td>
            <td>{score.total}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
