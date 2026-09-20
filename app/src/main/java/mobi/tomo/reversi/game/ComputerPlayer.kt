package mobi.tomo.reversi.game

import kotlin.random.Random

/**
 * CPU の思考ルーチン。αβ 探索で着手を選ぶ。
 *
 * 評価はマスの重み付けと着手可能数（打てる手の多さ）の組み合わせ。
 * 残りマスが少なくなったら最後まで読み切って石差を最大化する。
 *
 * 強さは [depth] と [endgameEmpties] で決まる。既定は「中くらい」で、
 * 素人には勝ち越すが、定石を知っている相手には負ける程度を狙っている。
 */
class ComputerPlayer(
    private val depth: Int = MEDIUM_DEPTH,
    private val endgameEmpties: Int = MEDIUM_ENDGAME_EMPTIES,
    private val random: Random = Random.Default,
) {

    /** [disc] の手番として指す場所を選ぶ。打てる場所が無ければ null。 */
    fun chooseMove(board: Board, disc: Disc): Int? {
        val moves = board.legalMoves(disc)
        if (moves.isEmpty()) return null
        if (moves.size == 1) return moves.first()

        val empties = Board.INDICES.count { board.isEmpty(it) }
        val readToEnd = empties <= endgameEmpties
        val searchDepth = if (readToEnd) empties else depth

        var best = moves.first()
        var alpha = -INFINITY
        for (move in ordered(moves)) {
            val score = -search(
                board = board.play(disc, move),
                disc = disc.opposite,
                depth = searchDepth - 1,
                alpha = -INFINITY,
                beta = -alpha,
            )
            if (score > alpha) {
                alpha = score
                best = move
            }
        }
        return best
    }

    /**
     * ネガマックス法のαβ探索。[disc] から見た評価値を返す。
     *
     * 打てる手が無いときはパスとして相手に手番を渡す。深さは消費しない。
     * 双方が打てなければ終局として石差を返す。
     */
    private fun search(board: Board, disc: Disc, depth: Int, alpha: Int, beta: Int): Int {
        val moves = board.legalMoves(disc)
        if (moves.isEmpty()) {
            if (!board.hasLegalMove(disc.opposite)) return finalScore(board, disc)
            return -search(board, disc.opposite, depth, -beta, -alpha)
        }
        if (depth <= 0) return evaluate(board, disc, moves.size)

        var bestScore = -INFINITY
        var lowerBound = alpha
        for (move in ordered(moves)) {
            val score = -search(board.play(disc, move), disc.opposite, depth - 1, -beta, -lowerBound)
            if (score > bestScore) bestScore = score
            if (bestScore > lowerBound) lowerBound = bestScore
            if (lowerBound >= beta) break
        }
        return bestScore
    }

    /**
     * 枝刈りが効くよう、良さそうな手から並べる。
     * 重みが同じ手同士の順序はランダムにして、毎回同じ対局にならないようにする。
     */
    private fun ordered(moves: List<Int>): List<Int> =
        moves.shuffled(random).sortedByDescending { WEIGHTS[weightIndex(it)] }

    private fun evaluate(board: Board, disc: Disc, myMoveCount: Int): Int {
        var score = 0
        for (index in Board.INDICES) {
            val cell = board.discAt(index) ?: continue
            if (cell == disc) {
                score += WEIGHTS[weightIndex(index)]
            } else {
                score -= WEIGHTS[weightIndex(index)]
            }
        }
        val opponentMoveCount = board.legalMoves(disc.opposite).size
        return score + MOBILITY_WEIGHT * (myMoveCount - opponentMoveCount)
    }

    /** 終局時の評価。石差がそのまま勝敗なので、位置評価より十分大きく見積もる。 */
    private fun finalScore(board: Board, disc: Disc): Int =
        (board.count(disc) - board.count(disc.opposite)) * TERMINAL_WEIGHT

    private fun weightIndex(index: Int): Int =
        (Board.rowOf(index) - 1) * Board.SIZE + (Board.colOf(index) - 1)

    companion object {
        /** 中くらいの強さ。読みの深さ（手数）。 */
        const val MEDIUM_DEPTH = 4

        /** 空きマスがこの数以下になったら最後まで読み切る。 */
        const val MEDIUM_ENDGAME_EMPTIES = 8

        private const val INFINITY = 10_000_000
        private const val TERMINAL_WEIGHT = 10_000
        private const val MOBILITY_WEIGHT = 10

        /**
         * マスの重み。隅は強く、隅の隣は弱い、という定番の配点。
         * 添字は盤の左上から右下へ順に 0..63。
         */
        private val WEIGHTS = intArrayOf(
            120, -20, 20, 5, 5, 20, -20, 120,
            -20, -40, -5, -5, -5, -5, -40, -20,
            20, -5, 15, 3, 3, 15, -5, 20,
            5, -5, 3, 3, 3, 3, -5, 5,
            5, -5, 3, 3, 3, 3, -5, 5,
            20, -5, 15, 3, 3, 15, -5, 20,
            -20, -40, -5, -5, -5, -5, -40, -20,
            120, -20, 20, 5, 5, 20, -20, 120,
        )
    }
}
