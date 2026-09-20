package mobi.tomo.reversi.game

/**
 * リバーシの盤面。8x8 の盤を、番兵で囲った 10x10 の配列として持つ。
 * マスは `row * 10 + col`（col, row ともに 1..8）で指す。
 *
 * 不変オブジェクトで、[play] は新しい盤面を返す。Android には依存しないため
 * 単体テストからそのまま使える。
 */
class Board private constructor(private val cells: IntArray) {

    /** そのマスの石。空なら null。 */
    fun discAt(index: Int): Disc? = when (cells[index]) {
        Disc.BLACK.code -> Disc.BLACK
        Disc.WHITE.code -> Disc.WHITE
        else -> null
    }

    fun isEmpty(index: Int): Boolean = index in 0..99 && cells[index] == EMPTY

    /**
     * そこへ [disc] を置いたときに裏返る石の数。0 なら着手できない。
     * 盤の外や石のあるマスも 0 を返す。
     */
    fun flipCount(disc: Disc, index: Int): Int {
        if (index !in 0..99 || cells[index] != EMPTY) return 0

        val mine = disc.code
        val yours = disc.opposite.code
        var flipped = 0
        for (direction in DIRECTIONS) {
            if (cells[index + direction] != yours) continue
            var step = 2
            while (step <= MAX_STEP) {
                val cell = cells[index + direction * step]
                if (cell == mine) {
                    flipped += step - 1
                    break
                }
                if (cell != yours) break
                step++
            }
        }
        return flipped
    }

    fun isLegal(disc: Disc, index: Int): Boolean = flipCount(disc, index) > 0

    /** 着手できるマスを昇順で。 */
    fun legalMoves(disc: Disc): List<Int> = INDICES.filter { flipCount(disc, it) > 0 }

    fun hasLegalMove(disc: Disc): Boolean = INDICES.any { flipCount(disc, it) > 0 }

    /** 石を置いて裏返した新しい盤面を返す。着手できない位置なら例外。 */
    fun play(disc: Disc, index: Int): Board {
        require(flipCount(disc, index) > 0) { "着手できない位置です: index=$index, disc=$disc" }

        val mine = disc.code
        val yours = disc.opposite.code
        val next = cells.copyOf()
        next[index] = mine
        for (direction in DIRECTIONS) {
            if (cells[index + direction] != yours) continue
            var step = 2
            while (step <= MAX_STEP) {
                val cell = cells[index + direction * step]
                if (cell == mine) {
                    for (back in 1 until step) {
                        next[index + direction * back] = mine
                    }
                    break
                }
                if (cell != yours) break
                step++
            }
        }
        return Board(next)
    }

    fun count(disc: Disc): Int = INDICES.count { cells[it] == disc.code }

    val isFull: Boolean
        get() = INDICES.none { cells[it] == EMPTY }

    /** `B` `W` `.` の 8 行。テストやログで盤面をそのまま読めるようにするため。 */
    fun toDiagram(): String = (1..SIZE).joinToString("\n") { row ->
        (1..SIZE).map { col ->
            when (discAt(index(col, row))) {
                Disc.BLACK -> 'B'
                Disc.WHITE -> 'W'
                null -> '.'
            }
        }.joinToString("")
    }

    override fun toString(): String = toDiagram()

    companion object {
        const val SIZE = 8

        private const val EMPTY = 0
        private const val WALL = -1

        /** 8 方向。番兵があるので盤の外へ出ても添字は 0..99 に収まる。 */
        private val DIRECTIONS = intArrayOf(-11, -10, -9, -1, 1, 9, 10, 11)

        /** 一方向に進める最大歩数。盤の端から端まででも 7 歩。 */
        private const val MAX_STEP = 7

        /** 盤上 64 マスの添字。 */
        val INDICES: List<Int> = (1..SIZE).flatMap { row -> (1..SIZE).map { col -> index(col, row) } }

        fun index(col: Int, row: Int): Int = row * 10 + col

        fun colOf(index: Int): Int = index % 10

        fun rowOf(index: Int): Int = index / 10

        fun empty(): Board = Board(walled())

        /** 中央 4 石を置いた開始局面。黒が先手。 */
        fun initial(): Board {
            val cells = walled()
            cells[index(4, 4)] = Disc.WHITE.code
            cells[index(5, 4)] = Disc.BLACK.code
            cells[index(4, 5)] = Disc.BLACK.code
            cells[index(5, 5)] = Disc.WHITE.code
            return Board(cells)
        }

        /**
         * 8 行の文字列から盤面を作る。`B` が黒、`W` が白、それ以外は空。
         * テストで局面を読みやすく書くためのもの。
         */
        fun fromDiagram(vararg rows: String): Board {
            require(rows.size == SIZE) { "行数は $SIZE 必要です: ${rows.size}" }
            val cells = walled()
            rows.forEachIndexed { rowIndex, line ->
                val trimmed = line.trim()
                require(trimmed.length == SIZE) { "各行は $SIZE 文字必要です: '$line'" }
                trimmed.forEachIndexed { colIndex, symbol ->
                    val cell = index(colIndex + 1, rowIndex + 1)
                    cells[cell] = when (symbol.uppercaseChar()) {
                        'B' -> Disc.BLACK.code
                        'W' -> Disc.WHITE.code
                        else -> EMPTY
                    }
                }
            }
            return Board(cells)
        }

        private fun walled(): IntArray {
            val cells = IntArray(100)
            for (i in 0..9) {
                cells[i] = WALL
                cells[i + 90] = WALL
            }
            for (row in 1..SIZE) {
                cells[row * 10] = WALL
                cells[row * 10 + 9] = WALL
            }
            return cells
        }
    }
}
