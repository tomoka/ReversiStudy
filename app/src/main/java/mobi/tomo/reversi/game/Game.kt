package mobi.tomo.reversi.game

/**
 * 対局の進行。手番の交代、パス、終局の判定を持つ。
 *
 * [state] が [State.PASS] のときは [turn] の側が打てない状態で、
 * 画面がパスを見せ終えたら [acknowledgePass] を呼んで手番を戻す。
 */
class Game(
    first: Disc = Disc.BLACK,
    board: Board = Board.initial(),
) {

    enum class State {
        /** [turn] の側が打てる。 */
        IN_PROGRESS,

        /** [turn] の側が打てず、相手は打てる。パスして手番を戻す。 */
        PASS,

        /** 双方が打てない。終局。 */
        FINISHED,
    }

    var board: Board = board
        private set

    var turn: Disc = first
        private set

    var state: State = State.IN_PROGRESS
        private set

    /** 今の手番が打てるマス。 */
    var legalMoves: List<Int> = emptyList()
        private set

    init {
        refresh()
    }

    val isFinished: Boolean
        get() = state == State.FINISHED

    fun count(disc: Disc): Int = board.count(disc)

    /** 終局時の勝者。引き分けなら null。終局していなければ null。 */
    val winner: Disc?
        get() {
            if (state != State.FINISHED) return null
            val black = board.count(Disc.BLACK)
            val white = board.count(Disc.WHITE)
            return when {
                black > white -> Disc.BLACK
                white > black -> Disc.WHITE
                else -> null
            }
        }

    /** 今の手番で着手する。着手できない位置なら例外。 */
    fun play(index: Int) {
        check(state == State.IN_PROGRESS) { "着手できる状態ではありません: $state" }
        board = board.play(turn, index)
        turn = turn.opposite
        refresh()
    }

    /** パスを見せ終えたら呼ぶ。手番を相手に戻す。 */
    fun acknowledgePass() {
        check(state == State.PASS) { "パス中ではありません: $state" }
        turn = turn.opposite
        refresh()
    }

    private fun refresh() {
        legalMoves = board.legalMoves(turn)
        state = when {
            legalMoves.isNotEmpty() -> State.IN_PROGRESS
            board.hasLegalMove(turn.opposite) -> State.PASS
            else -> State.FINISHED
        }
    }
}
