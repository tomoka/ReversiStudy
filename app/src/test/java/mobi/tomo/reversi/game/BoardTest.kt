package mobi.tomo.reversi.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {

    @Test
    fun `添字と行列の対応`() {
        assertEquals(11, Board.index(1, 1))
        assertEquals(88, Board.index(8, 8))
        assertEquals(3, Board.colOf(Board.index(3, 6)))
        assertEquals(6, Board.rowOf(Board.index(3, 6)))
        assertEquals(64, Board.INDICES.size)
        assertEquals(Board.INDICES.distinct().size, Board.INDICES.size)
    }

    @Test
    fun `開始局面は中央に4石`() {
        val board = Board.initial()
        assertEquals(
            """
            ........
            ........
            ........
            ...WB...
            ...BW...
            ........
            ........
            ........
            """.trimIndent(),
            board.toDiagram(),
        )
        assertEquals(2, board.count(Disc.BLACK))
        assertEquals(2, board.count(Disc.WHITE))
        assertFalse(board.isFull)
    }

    @Test
    fun `開始局面で黒が打てるのは4箇所`() {
        val moves = Board.initial().legalMoves(Disc.BLACK)
        assertEquals(
            listOf(
                Board.index(4, 3),
                Board.index(3, 4),
                Board.index(6, 5),
                Board.index(5, 6),
            ).sorted(),
            moves,
        )
    }

    @Test
    fun `着手すると挟んだ石が裏返る`() {
        val board = Board.initial().play(Disc.BLACK, Board.index(4, 3))
        assertEquals(
            """
            ........
            ........
            ...B....
            ...BB...
            ...BW...
            ........
            ........
            ........
            """.trimIndent(),
            board.toDiagram(),
        )
        assertEquals(4, board.count(Disc.BLACK))
        assertEquals(1, board.count(Disc.WHITE))
    }

    @Test
    fun `複数方向を同時に裏返す`() {
        // 中央の空きマスを白で囲み、その外側に黒を置いた局面
        val board = Board.fromDiagram(
            "........",
            ".B.B.B..",
            "..WWW...",
            ".BW.WB..",
            "..WWW...",
            ".B.B.B..",
            "........",
            "........",
        )
        assertEquals(8, board.count(Disc.BLACK))
        assertEquals(8, board.count(Disc.WHITE))
        assertEquals(8, board.flipCount(Disc.BLACK, Board.index(4, 4)))

        val played = board.play(Disc.BLACK, Board.index(4, 4))
        assertEquals(0, played.count(Disc.WHITE))
        assertEquals(17, played.count(Disc.BLACK))
    }

    @Test
    fun `一行に並んだ石はまとめて裏返る`() {
        val board = Board.fromDiagram(
            ".WWWWWWB",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
        )
        assertEquals(6, board.flipCount(Disc.BLACK, Board.index(1, 1)))
    }

    @Test
    fun `盤の端で反対側の行へ回り込まない`() {
        // 右端 (8,1) の右隣は「次の行の左端」ではない
        val rightEdge = Board.fromDiagram(
            "........",
            "WB......",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
        )
        assertEquals(0, rightEdge.flipCount(Disc.BLACK, Board.index(8, 1)))

        // 左端 (1,2) の左隣は「前の行の右端」ではない
        val leftEdge = Board.fromDiagram(
            "......BW",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
        )
        assertEquals(0, leftEdge.flipCount(Disc.BLACK, Board.index(1, 2)))
    }

    @Test
    fun `石の無い方向や自分の石だけでは着手できない`() {
        val board = Board.initial()
        assertEquals(0, board.flipCount(Disc.BLACK, Board.index(1, 1)))
        assertFalse(board.isLegal(Disc.BLACK, Board.index(1, 1)))
        // すでに石があるマス
        assertEquals(0, board.flipCount(Disc.BLACK, Board.index(4, 4)))
        // 盤の外
        assertEquals(0, board.flipCount(Disc.BLACK, 0))
        assertEquals(0, board.flipCount(Disc.BLACK, 99))
        assertEquals(0, board.flipCount(Disc.BLACK, 1000))
        assertEquals(0, board.flipCount(Disc.BLACK, -5))
    }

    @Test
    fun `着手しても元の盤面は変わらない`() {
        val before = Board.initial()
        val snapshot = before.toDiagram()
        before.play(Disc.BLACK, Board.index(4, 3))
        assertEquals(snapshot, before.toDiagram())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `着手できない位置に打つと例外`() {
        Board.initial().play(Disc.BLACK, Board.index(1, 1))
    }

    @Test
    fun `盤を全部埋めると満杯になる`() {
        val full = Board.fromDiagram(
            "BBBBBBBB",
            "BBBBBBBB",
            "BBBBBBBB",
            "BBBBWWWW",
            "WWWWWWWW",
            "WWWWWWWW",
            "WWWWWWWW",
            "WWWWWWWW",
        )
        assertTrue(full.isFull)
        assertEquals(28, full.count(Disc.BLACK))
        assertEquals(36, full.count(Disc.WHITE))
        assertFalse(full.hasLegalMove(Disc.BLACK))
        assertFalse(full.hasLegalMove(Disc.WHITE))
    }

    @Test
    fun `図から作った盤を図に戻すと一致する`() {
        val rows = arrayOf(
            "B.......",
            ".W......",
            "..B.....",
            "...W....",
            "....B...",
            ".....W..",
            "......B.",
            ".......W",
        )
        assertEquals(rows.joinToString("\n"), Board.fromDiagram(*rows).toDiagram())
    }

    @Test
    fun `空マスの判定`() {
        val board = Board.initial()
        assertTrue(board.isEmpty(Board.index(1, 1)))
        assertFalse(board.isEmpty(Board.index(4, 4)))
        assertNull(board.discAt(Board.index(1, 1)))
        assertEquals(Disc.WHITE, board.discAt(Board.index(4, 4)))
        assertEquals(Disc.BLACK, board.discAt(Board.index(5, 4)))
    }

    @Test
    fun `色の反転`() {
        assertEquals(Disc.WHITE, Disc.BLACK.opposite)
        assertEquals(Disc.BLACK, Disc.WHITE.opposite)
    }
}
