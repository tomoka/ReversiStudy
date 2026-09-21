package mobi.tomo.reversi.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DifficultyTest {

    @Test
    fun `5 段階あり、番号は1から5`() {
        assertEquals(5, Difficulty.entries.size)
        assertEquals(listOf(1, 2, 3, 4, 5), Difficulty.entries.map { it.level })
        assertEquals(Difficulty.NORMAL, Difficulty.DEFAULT)
    }

    @Test
    fun `強い段階ほど深く読み、終盤も長く読み切る`() {
        val depths = Difficulty.entries.map { it.depth }
        val endgames = Difficulty.entries.map { it.endgameEmpties }
        assertEquals(depths.sorted(), depths)
        assertEquals(endgames.sorted(), endgames)
    }

    @Test
    fun `わざと外すのは弱い段階だけ`() {
        assertTrue("入門は半分以上わざと外す", Difficulty.BEGINNER.randomness > 0.5f)
        assertTrue("初級は少し外す", Difficulty.EASY.randomness in 0.1f..0.5f)
        for (value in listOf(Difficulty.NORMAL, Difficulty.HARD, Difficulty.EXPERT)) {
            assertEquals("${value.name} は最善手だけを選ぶはず", 0f, value.randomness, 0f)
        }
    }

    @Test
    fun `番号や名前から引ける`() {
        assertEquals(Difficulty.BEGINNER, Difficulty.ofLevel(1))
        assertEquals(Difficulty.EXPERT, Difficulty.ofLevel(5))
        assertEquals(Difficulty.DEFAULT, Difficulty.ofLevel(0))
        assertEquals(Difficulty.DEFAULT, Difficulty.ofLevel(6))
        assertEquals(Difficulty.HARD, Difficulty.ofName("HARD"))
        assertEquals(Difficulty.DEFAULT, Difficulty.ofName("なにこれ"))
        assertEquals(Difficulty.DEFAULT, Difficulty.ofName(null))
    }

    @Test
    fun `入門はときどき最善から外れる`() {
        // 同じ局面で何度も選ばせると、最善手以外も混じる
        val board = Board.initial()
        val best = ComputerPlayer(Difficulty.EXPERT, Random(1)).chooseMove(board, Disc.BLACK)
        val beginner = ComputerPlayer(Difficulty.BEGINNER, Random(7))
        val chosen = (1..60).map { beginner.chooseMove(board, Disc.BLACK) }
        assertTrue("いつも同じ手を選んでいる", chosen.toSet().size > 1)
        assertTrue("最善手以外を選ぶことがあるはず", chosen.any { it != best })
        assertTrue("すべて合法手であるはず", chosen.all { it in board.legalMoves(Disc.BLACK) })
    }

    @Test
    fun `どの段階でも合法手を返す`() {
        val random = Random(20260920)
        for (value in Difficulty.entries) {
            val cpu = ComputerPlayer(value, Random(value.ordinal))
            val game = Game()
            while (!game.isFinished) {
                when (game.state) {
                    Game.State.PASS -> game.acknowledgePass()
                    Game.State.FINISHED -> Unit
                    Game.State.IN_PROGRESS -> {
                        val move = cpu.chooseMove(game.board, game.turn)
                        assertTrue("${value.name} が合法手でない手を返した", move in game.legalMoves)
                        game.play(game.legalMoves[random.nextInt(game.legalMoves.size)])
                    }
                }
            }
        }
    }
}
