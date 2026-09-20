package mobi.tomo.reversi.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameTest {

    @Test
    fun `開始時は黒の手番で4手指せる`() {
        val game = Game()
        assertEquals(Disc.BLACK, game.turn)
        assertEquals(Game.State.IN_PROGRESS, game.state)
        assertEquals(4, game.legalMoves.size)
        assertFalse(game.isFinished)
        assertNull(game.winner)
    }

    @Test
    fun `着手すると手番が交代する`() {
        val game = Game()
        game.play(Board.index(4, 3))
        assertEquals(Disc.WHITE, game.turn)
        assertEquals(Game.State.IN_PROGRESS, game.state)
        assertEquals(4, game.count(Disc.BLACK))
        assertEquals(1, game.count(Disc.WHITE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `着手できない位置は拒否される`() {
        Game().play(Board.index(1, 1))
    }

    @Test
    fun `打てない側はパスになり、確認すると手番が戻る`() {
        // 白は 1 石だけで、黒に挟まれて打つ場所が無い局面
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
        val game = Game(first = Disc.WHITE, board = board)
        assertEquals(Game.State.PASS, game.state)
        assertEquals(Disc.WHITE, game.turn)
        assertTrue(game.legalMoves.isEmpty())

        game.acknowledgePass()
        assertEquals(Disc.BLACK, game.turn)
        assertEquals(Game.State.IN_PROGRESS, game.state)
        assertFalse(game.legalMoves.isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun `パス中でないのに確認すると例外`() {
        Game().acknowledgePass()
    }

    @Test(expected = IllegalStateException::class)
    fun `パス中に着手しようとすると例外`() {
        val game = Game(
            first = Disc.WHITE,
            board = Board.fromDiagram(
                "BW......",
                "........",
                "........",
                "........",
                "........",
                "........",
                "........",
                "......BB",
            ),
        )
        assertEquals(Game.State.PASS, game.state)
        game.play(Board.index(1, 1))
    }

    @Test
    fun `双方が打てなければ終局し、多い方が勝ち`() {
        val board = Board.fromDiagram(
            "BBBBBBBB",
            "BBBBBBBB",
            "BBBBBBBB",
            "BBBBBBBB",
            "BBBBWWWW",
            "WWWWWWWW",
            "WWWWWWWW",
            "WWWWWWWW",
        )
        val game = Game(first = Disc.BLACK, board = board)
        assertEquals(Game.State.FINISHED, game.state)
        assertTrue(game.isFinished)
        assertEquals(36, game.count(Disc.BLACK))
        assertEquals(28, game.count(Disc.WHITE))
        assertEquals(Disc.BLACK, game.winner)
    }

    @Test
    fun `同数なら引き分け`() {
        val board = Board.fromDiagram(
            "BBBBBBBB",
            "BBBBBBBB",
            "BBBBBBBB",
            "BBBBBBBB",
            "WWWWWWWW",
            "WWWWWWWW",
            "WWWWWWWW",
            "WWWWWWWW",
        )
        val game = Game(board = board)
        assertEquals(Game.State.FINISHED, game.state)
        assertEquals(32, game.count(Disc.BLACK))
        assertEquals(32, game.count(Disc.WHITE))
        assertNull(game.winner)
    }

    @Test
    fun `ランダムな対局が必ず終局し、石数の合計が盤上と一致する`() {
        val random = Random(20260920)
        repeat(300) {
            val game = Game()
            var moves = 0
            while (!game.isFinished) {
                when (game.state) {
                    Game.State.IN_PROGRESS -> {
                        game.play(game.legalMoves[random.nextInt(game.legalMoves.size)])
                        moves++
                    }
                    Game.State.PASS -> game.acknowledgePass()
                    Game.State.FINISHED -> Unit
                }
                assertTrue("手数が多すぎる", moves <= 60)
            }
            val stones = Board.INDICES.count { game.board.discAt(it) != null }
            assertEquals(stones, game.count(Disc.BLACK) + game.count(Disc.WHITE))
            // 開始時の 4 石に着手数を足したものが盤上の石数
            assertEquals(4 + moves, stones)
        }
    }
}
