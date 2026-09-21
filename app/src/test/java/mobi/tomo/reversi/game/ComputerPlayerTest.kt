package mobi.tomo.reversi.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ComputerPlayerTest {

    private fun engine(seed: Int = 1) = ComputerPlayer(random = Random(seed))

    @Test
    fun `打てないときは null を返す`() {
        val board = Board.fromDiagram(
            "BW......",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "......BB",
        )
        assertTrue(board.legalMoves(Disc.WHITE).isEmpty())
        assertNull(engine().chooseMove(board, Disc.WHITE))
    }

    @Test
    fun `どの局面でも合法手を返す`() {
        val random = Random(4242)
        val cpu = engine()
        repeat(40) {
            val game = Game()
            while (!game.isFinished) {
                when (game.state) {
                    Game.State.PASS -> game.acknowledgePass()
                    Game.State.FINISHED -> Unit
                    Game.State.IN_PROGRESS -> {
                        val chosen = cpu.chooseMove(game.board, game.turn)
                        assertNotNull(chosen)
                        assertTrue(
                            "合法手でない手を返した: $chosen",
                            game.legalMoves.contains(chosen),
                        )
                        game.play(game.legalMoves[random.nextInt(game.legalMoves.size)])
                    }
                }
            }
        }
    }

    @Test
    fun `評価は隅を隅の隣より高く見る`() {
        // 打てるのは隅とその斜め隣の 2 箇所だけ、という局面を 4 隅ぶん作って確かめる。
        // 深く読むと別の判断になりうるので、ここでは評価値そのものを見るため depth=1 で試す。
        val base = arrayOf(
            ".WB.....",
            "........",
            "..W.....",
            "...B....",
            "........",
            "........",
            "........",
            "........",
        )
        val cases = listOf(
            Triple(false, false, Board.index(1, 1)),
            Triple(true, false, Board.index(8, 1)),
            Triple(false, true, Board.index(1, 8)),
            Triple(true, true, Board.index(8, 8)),
        )
        for ((flipX, flipY, corner) in cases) {
            val board = Board.fromDiagram(*mirror(base, flipX, flipY))
            val moves = board.legalMoves(Disc.BLACK)
            assertEquals("打てる手は隅と斜め隣の 2 箇所のはず", 2, moves.size)
            assertTrue("隅が候補に無い", moves.contains(corner))

            val shallow = ComputerPlayer.custom(depth = 1, random = Random(1))
            assertEquals("隅を選ばなかった", corner, shallow.chooseMove(board, Disc.BLACK))
        }
    }

    /** 盤の図を左右・上下に反転する。重み表が 4 隅で対称かを確かめるため。 */
    private fun mirror(rows: Array<String>, horizontal: Boolean, vertical: Boolean): Array<String> {
        var result = rows
        if (horizontal) result = result.map { it.reversed() }.toTypedArray()
        if (vertical) result = result.reversedArray()
        return result
    }

    @Test
    fun `終盤は読み切って最善手を選ぶ`() {
        val random = Random(20260920)
        val cpu = engine(seed = 5)
        var checked = 0

        repeat(30) {
            // 空きが 8 マスになるまでランダムに進める
            val game = Game()
            while (!game.isFinished && emptyCount(game.board) > Difficulty.NORMAL.endgameEmpties) {
                when (game.state) {
                    Game.State.PASS -> game.acknowledgePass()
                    Game.State.FINISHED -> Unit
                    Game.State.IN_PROGRESS ->
                        game.play(game.legalMoves[random.nextInt(game.legalMoves.size)])
                }
            }
            if (game.isFinished || game.state != Game.State.IN_PROGRESS) return@repeat

            val chosen = cpu.chooseMove(game.board, game.turn)!!
            // 総当たりで求めた最善の石差と、CPU の手の石差が一致するはず
            val best = solve(game.board, game.turn)
            val actual = -solve(game.board.play(game.turn, chosen), game.turn.opposite)
            assertEquals("終盤の読みが最善でない", best, actual)
            checked++
        }
        assertTrue("検証できた局面が少なすぎる", checked >= 20)
    }

    @Test
    fun `ランダムに打つ相手には勝ち越す`() {
        val random = Random(777)
        val cpu = engine(seed = 8)
        var wins = 0
        val games = 10

        repeat(games) { i ->
            val cpuDisc = if (i % 2 == 0) Disc.BLACK else Disc.WHITE
            val game = Game()
            while (!game.isFinished) {
                when (game.state) {
                    Game.State.PASS -> game.acknowledgePass()
                    Game.State.FINISHED -> Unit
                    Game.State.IN_PROGRESS ->
                        if (game.turn == cpuDisc) {
                            game.play(cpu.chooseMove(game.board, game.turn)!!)
                        } else {
                            game.play(game.legalMoves[random.nextInt(game.legalMoves.size)])
                        }
                }
            }
            if (game.count(cpuDisc) > game.count(cpuDisc.opposite)) wins++
        }
        assertTrue("ランダム相手に $games 局中 $wins 勝しかしていない", wins >= 8)
    }

    @Test
    fun `乱数を固定すれば同じ手を返す`() {
        val board = Board.initial()
        val first = ComputerPlayer(random = Random(99)).chooseMove(board, Disc.BLACK)
        val second = ComputerPlayer(random = Random(99)).chooseMove(board, Disc.BLACK)
        assertEquals(first, second)
    }

    private fun emptyCount(board: Board): Int = Board.INDICES.count { board.isEmpty(it) }

    /**
     * 総当たりで求めた石差（[disc] から見た最善値）。
     * 枝刈りをしない素朴な実装で、[ComputerPlayer] の読みを外から検証するために使う。
     */
    private fun solve(board: Board, disc: Disc): Int {
        val moves = board.legalMoves(disc)
        if (moves.isEmpty()) {
            if (!board.hasLegalMove(disc.opposite)) {
                return board.count(disc) - board.count(disc.opposite)
            }
            return -solve(board, disc.opposite)
        }
        return moves.maxOf { -solve(board.play(disc, it), disc.opposite) }
    }
}
